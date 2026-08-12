import matrix.Simulation;
import matrix.core.Config;
import matrix.realworld.Human;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * D-047's capture layer: a day caught before a word of it is written.
 *
 * The reader boots its OWN quiet universe — the probe bench's pattern: a
 * private {@link Simulation}, an explicit seed, every instrument line landing
 * in a buffer and nowhere else — and takes three feeds in strict capture
 * order:
 *
 * <ol>
 *   <li>the pilot's PERCEPTION FRAMES, off the daemon's own --follow tap;</li>
 *   <li>every LOG LINE that carries their name;</li>
 *   <li>the world's own BEATS — history happening elsewhere, whether or not
 *       the pilot ever hears it.</li>
 * </ol>
 *
 * Three feeds is a structural fact, not tidiness: the sleeper's window, the
 * record's mention of them and the war being fought two districts away are
 * different kinds of evidence, and mixing them here would make it impossible
 * to say later which sentence came from which.
 *
 * <h2>The name is a search key, never a binding (#375, fixed on main by #765)</h2>
 *
 * The tap binds to a MIND: the string resolves once and the stream belongs to
 * that person forever after. The reader's third feed cannot inherit that for
 * free, because the log names people by NAME and nothing else — and names are
 * not unique: 196 humans wear 154 of them at seed 42, 155 at seed 1. So the
 * capture layer resolves the same mind the tap resolved, counts how many minds
 * in this record answer to that name, and refuses to guess:
 *
 * <ul>
 *   <li>one mind wears the name — the name IS a binding here, and every line
 *       carrying it is theirs;</li>
 *   <li>more than one — the only binding the record offers is the pod address
 *       some lines carry. A line with the pilot's rack unit is theirs; a line
 *       with somebody else's is dropped as proven to belong to a namesake; a
 *       line with none is kept as UNPROVEN and the page says so rather than
 *       braiding two lives into one day.</li>
 * </ul>
 *
 * <h2>Observer-only</h2>
 *
 * Not one domain byte moves. The one private field this class opens is the
 * root's {@code realWorld}, read-only, exactly the opener the probe bench uses
 * (probes/README.md, contract clause 3) — the reader lives in tools/ only
 * because --out writes a file the user names, which the bench forbids; the
 * rest of the probe contract it keeps. Nothing here queues a WorldEvent, draws
 * from the rng or touches the digest chain.
 */
final class Capture {

    /** One frame off the tap: what a brain was fed that tick. */
    record Frame(int seq, long tick, String pill, int xCm, int yCm, long agentCm, long exitCm) {}

    /** The tap's own verdict on the wire: "ended …" or "lost …". */
    record Signal(int seq, long tick, String text) {}

    /** One line of the record. */
    record Line(int seq, long tick, String sev, String msg) {}

    /** Whether a line that carries the pilot's name can be proven to be theirs. */
    enum Claim { MINE, UNPROVEN }

    /** A line carrying the pilot's name, with the strength of that claim. */
    record Named(Line line, Claim claim) {}

    // ------------------------------------------------------------- subject

    final String pilotArg;
    final long seed;
    final long ticks;

    /** The mind the tap bound to — null when no streamable link answered. */
    private Human mind;
    /** That mind's name, canonical; null means no day to render. */
    String resolvedName;
    /** That mind's rack unit — the one identity handle the record ever prints. */
    String podAddress;
    /** How many minds in this record wear {@link #resolvedName}. */
    int namesakes;
    /** How many minds the record holds whose name carries the argument at all. */
    int censusMatches;
    /** The whole population, for the record. */
    int censusSize;
    /**
     * The name the TAP itself announced at boot. The reader resolves the mind
     * with a second call to the same resolver, which is only sound while the
     * root's constructor does nothing between arming the tap and printing this
     * line. So the reader does not trust that: it reads the tap's own word back
     * and refuses to render a page the tap disagrees with.
     */
    String tapName;

    // --------------------------------------------------------------- feeds

    final List<Frame> frames = new ArrayList<>();
    final List<Signal> signals = new ArrayList<>();
    final List<Named> naming = new ArrayList<>();
    final List<Line> beats = new ArrayList<>();
    final List<Line> boot = new ArrayList<>();
    /** Lines proven to belong to a namesake — counted, never rendered. */
    int namesakeLinesDropped;

    final Tally tally = new Tally();

    private int seq;

    private Capture(String pilotArg, long seed, long ticks) {
        this.pilotArg = pilotArg;
        this.seed = seed;
        this.ticks = ticks;
    }

    // -------------------------------------------------------------- taking

    /**
     * Boot a private universe, run it to {@code ticks}, and fold the buffer
     * into three feeds. Deterministic end to end: no wall clock, no rng of the
     * reader's own, and every list stays in capture order.
     */
    static Capture of(String pilotArg, long seed, long ticks) throws ReflectiveOperationException {
        Capture c = new Capture(pilotArg, seed, ticks);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 22);
        Simulation sim = new Simulation(seed, buffer, pilotArg);
        RealWorld realWorld = realWorldOf(sim);

        // The same resolution the tap made in the constructor, and the same
        // semantics: first STREAMABLE link whose name carries the fragment.
        // Nothing has moved between the two calls, so this is that link.
        NeuralLink tapped = realWorld.findLink(pilotArg);
        if (tapped != null) {
            c.mind = tapped.human;
            c.resolvedName = tapped.human.name;
            c.podAddress = tapped.human.pod == null ? null : tapped.human.pod.rackUnit;
        }

        sim.run(ticks);

        // The census AFTER the run: minds are grown mid-day too (The One is),
        // so a namesake born at tick 1179 must count against the name.
        c.census(realWorld);
        c.read(buffer.toString(StandardCharsets.UTF_8));

        // The reader resolved the mind with its own call to the tap's resolver.
        // That is sound only while nothing moves between the root arming the tap
        // and this call — true today, an assumption tomorrow. So it is checked
        // rather than assumed: if the tap streamed somebody else's dream, no
        // page gets written at all. Loud and early beats a beautiful lie.
        String announced = c.tapName == null ? "" : c.tapName;
        String mine = c.resolvedName == null ? "" : c.resolvedName;
        if (!announced.equals(mine)) {
            throw new IllegalStateException("FATAL the tap streamed '" + announced
                    + "' but the reader resolved '" + mine
                    + "' — the page would not be that mind's day");
        }
        return c;
    }

    private void census(RealWorld realWorld) {
        String needle = pilotArg.toLowerCase(Locale.ROOT);
        for (Human h : realWorld.humans()) {
            censusSize++;
            if (h.name.toLowerCase(Locale.ROOT).contains(needle)) {
                censusMatches++;
            }
            if (resolvedName != null && h.name.equals(resolvedName)) {
                namesakes++;
            }
        }
    }

    /** One pass over the buffer, strictly in capture order. */
    private void read(String buffer) {
        for (String line : buffer.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == '{') {
                takeFrame(line);
            } else if (line.charAt(0) == '[' && line.length() > 15) {
                takeLine(line);
            }
            // everything else is a METRIC/ECO/ZION/DIGEST instrument line:
            // built for pipelines, and no sentence on this page derives from one.
        }
    }

    private void takeFrame(String line) {
        long tick = Json.number(line, "\"tick\":");
        String signal = Json.text(line, "\"signal\":\"");
        if (signal != null) {
            signals.add(new Signal(seq++, tick, signal));
            return;
        }
        frames.add(new Frame(seq++, tick,
                Json.text(line, "\"pill\":\""),
                (int) Json.number(line, "\"pos\":["),
                (int) Json.numberAfter(line, "\"pos\":[", 1),
                line.contains("\"nearestAgentCm\":") ? Json.number(line, "\"nearestAgentCm\":") : -1,
                Json.number(line, "\"nearestExitCm\":")));
    }

    private void takeLine(String raw) {
        long tick;
        try {
            tick = Long.parseLong(raw.substring(1, 7));
        } catch (NumberFormatException e) {
            return; // not an EventLog line; the reader does not guess at it
        }
        String sev = raw.substring(9, 14).trim();
        String msg = raw.substring(15);
        Line line = new Line(seq++, tick, sev, msg);

        // The tap's own two lines are bookkeeping about the reader, not the day
        // — but the streaming line names the mind the tap actually bound to,
        // and that is the one claim the reader checks itself against.
        if (msg.startsWith("follow: ")) {
            String open = "follow: streaming the dream of ";
            String close = " as JSONL";
            if (msg.startsWith(open) && msg.endsWith(close)) {
                tapName = msg.substring(open.length(), msg.length() - close.length());
            }
            return;
        }
        if (tick == 0) {
            boot.add(line);
            return;
        }
        tally.count(msg);

        if (resolvedName != null && msg.contains(resolvedName)) {
            Claim claim = claim(msg);
            if (claim == null) {
                namesakeLinesDropped++;
            } else {
                naming.add(new Named(line, claim));
            }
            return;
        }
        if (Beats.kindOf(msg) != null) {
            beats.add(line);
        }
    }

    /**
     * Can this line be proven to be the pilot's? MINE when the name binds or
     * the line carries their rack unit, UNPROVEN when the name is shared and
     * the line offers no handle, null when the line proves it is somebody
     * else's — the namesake case #375 named, at the reader's own layer.
     */
    private Claim claim(String msg) {
        if (namesakes <= 1) {
            return Claim.MINE;
        }
        String pod = podIn(msg);
        if (pod == null) {
            return Claim.UNPROVEN;
        }
        return pod.equals(podAddress) ? Claim.MINE : null;
    }

    /** The rack unit a line carries — "(pod R06/U22 opens)", "(pod R01/U22 flushed)". */
    static String podIn(String msg) {
        int i = msg.indexOf("(pod ");
        if (i < 0) {
            return null;
        }
        int from = i + "(pod ".length();
        int to = msg.indexOf(' ', from);
        return to < 0 ? null : msg.substring(from, to);
    }

    // ------------------------------------------------------------ counting

    /** Lines that name whoever they name are the day's weather; the page counts them. */
    static final class Tally {
        int redPills, recaptures, copies, hijacks, flatlines, cookies, selfsubs, sigtermDodges;
        int doorTally = -1;
        String cookieLine;
        boolean cookiesIdentical = true;

        void count(String msg) {
            if (msg.startsWith("red pill: ")) {
                redPills++;
            } else if (msg.contains(": rogue client ")) {
                recaptures++;
            } else if (msg.startsWith("The One: a copy deleted")) {
                copies++;
            } else if (msg.startsWith("Smith.copyOnto():")) {
                hijacks++;
            } else if (msg.contains(" flatlined ")) {
                flatlines++;
            } else if (msg.startsWith("the Oracle: cookies are ready")) {
                cookies++;
                if (cookieLine == null) {
                    cookieLine = msg;
                } else if (!cookieLine.equals(msg)) {
                    cookiesIdentical = false;
                }
            } else if (msg.startsWith("self-substantiation: ")) {
                selfsubs++;
            } else if (msg.contains("survived collection")) {
                sigtermDodges++;
            } else if (msg.startsWith("open door tally: ")) {
                int from = "open door tally: ".length();
                doorTally = Integer.parseInt(msg.substring(from, msg.indexOf(' ', from)));
            }
        }
    }

    // ------------------------------------------------------------- reading

    /** The greppable verdict of the capture stage, one line, identical run to run. */
    String summary() {
        return "CAPTURED frames=" + frames.size() + " signals=" + signals.size()
                + " naming=" + naming.size() + " beats=" + beats.size();
    }

    /** How the capture stage reports itself before a word of prose exists. */
    String report() {
        StringBuilder out = new StringBuilder(512);
        out.append("pilot argument: ").append(pilotArg)
                .append(" · seed ").append(seed).append(" · ticks ").append(ticks)
                .append('\n');
        if (resolvedName == null) {
            out.append("subject: none — no streamable link answered to that name");
            if (censusMatches > 0) {
                out.append(" (the record holds ").append(censusMatches)
                        .append(censusMatches == 1 ? " mind" : " minds")
                        .append(" whose name carries it, none of them on a live wire when "
                                + "the tap armed)");
            }
            out.append('\n').append("census: ").append(censusSize).append(" minds\n");
            out.append(summary()).append('\n');
            return out.toString();
        }
        out.append("subject: ").append(resolvedName)
                .append(podAddress == null ? " (free-born, no pod)" : " · pod " + podAddress)
                .append(" · the tap agrees\n");
        out.append("census: ").append(censusSize).append(" minds, ").append(namesakes)
                .append(namesakes == 1 ? " of them wearing this name — here the name binds"
                        : " of them wearing this name — here the name does NOT bind")
                .append('\n');
        if (namesakes > 1) {
            int unproven = 0;
            for (Named n : naming) {
                if (n.claim() == Claim.UNPROVEN) {
                    unproven++;
                }
            }
            out.append("namesake guard: ").append(namesakeLinesDropped)
                    .append(" line(s) proven another mind's and dropped, ").append(unproven)
                    .append(" kept unproven\n");
        }
        out.append("window: every ").append(Config.FOLLOW_EVERY_TICKS).append(" ticks\n");
        out.append(summary()).append('\n');
        return out.toString();
    }

    // --------------------------------------------------------------- plumbing

    /**
     * The probe bench's read-only opener (probes/Probes.java), borrowed for one
     * field. Encapsulation protects the domain from the domain, not the reader
     * from the record: this hands back the aggregate root so the capture layer
     * can ask the census a question the log cannot answer — how many minds wear
     * this name. Nothing is written; nothing is queued.
     */
    private static RealWorld realWorldOf(Simulation sim) throws ReflectiveOperationException {
        Field f = Simulation.class.getDeclaredField("realWorld");
        f.setAccessible(true);
        return (RealWorld) f.get(sim);
    }

    /** Just enough JSON for one flat object of numbers and strings. */
    static final class Json {
        static long number(String line, String key) {
            return numberAfter(line, key, 0);
        }

        /** The n-th integer after {@code key} — "pos":[x,y] needs the second. */
        static long numberAfter(String line, String key, int skip) {
            int i = line.indexOf(key);
            if (i < 0) {
                return -1;
            }
            i += key.length();
            for (int n = 0; n < skip; n++) {
                while (i < line.length() && line.charAt(i) != ',') {
                    i++;
                }
                i++;
            }
            int j = i;
            while (j < line.length() && (line.charAt(j) == '-' || Character.isDigit(line.charAt(j)))) {
                j++;
            }
            return Long.parseLong(line.substring(i, j));
        }

        static String text(String line, String key) {
            int i = line.indexOf(key);
            return i < 0 ? null : line.substring(i + key.length(), line.indexOf('"', i + key.length()));
        }

        private Json() {}
    }
}
