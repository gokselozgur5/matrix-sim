import matrix.Simulation;
import matrix.core.Config;
import matrix.core.Geo;
import matrix.core.PlaceGraph;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * D-047's teleprinter (gate #217): the dream, rendered as prose.
 *
 * The feed was always the system's true output (D-021) and its only reader
 * was a pipeline. This tool is the missing reader: it boots its OWN quiet
 * universe (the probe bench's pattern — private Simulation, explicit seed,
 * captured sink), follows one mind with the daemon's own --follow tap, and
 * folds three streams into one page: the pilot's perception frames, every
 * log line that names them, and the world's beats as backdrop.
 *
 * Laws it lives under:
 *   - D-019 stands untouched: observer-only, public API only, not one byte
 *     of domain code changes. The daemon stays blind; the reader has eyes.
 *   - Deterministic prose: same args, same day, byte for byte. No wall
 *     clock, no rng of its own; everything renders in capture order.
 *   - No invented facts. Every sentence folds from a captured line; where
 *     the feed is silent, the page says so instead of dreaming one up.
 *
 * Usage:
 *   javac -encoding UTF-8 --release 17 -cp out -d tools/dreamreader/out \
 *       tools/dreamreader/DreamReader.java
 *   java -cp out:tools/dreamreader/out DreamReader --pilot NAME \
 *       [--seed N] [--ticks N] [--out FILE]
 *
 * Exit codes: 0 a day rendered · 2 the record holds nobody by that name.
 */
public final class DreamReader {

    private static final int WIDTH = 72;
    private static final String RULE_HEAVY = "=".repeat(WIDTH);
    private static final String RULE_LIGHT = "-".repeat(WIDTH);

    // ---------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        String pilot = null;
        long seed = 42;
        long ticks = 6_000;
        String outPath = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pilot" -> pilot = args[++i];
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Long.parseLong(args[++i]);
                case "--out" -> outPath = args[++i];
                case "--help" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("unknown flag: " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }
        if (pilot == null || pilot.isBlank()) {
            System.err.println("--pilot NAME is required — a teleprinter prints somebody");
            usage();
            System.exit(2);
        }

        // The private universe: every line the daemon would say lands in this
        // buffer and nowhere else. The reader consumes instruments, never
        // entities — the run below is byte-identical to any other at this seed.
        ByteArrayOutputStream capture = new ByteArrayOutputStream(1 << 20);
        new Simulation(seed, capture, pilot).run(ticks);

        Fold fold = new Fold(pilot, seed, ticks);
        for (String line : capture.toString(StandardCharsets.UTF_8).split("\n", -1)) {
            fold.take(line);
        }
        String page = fold.render();

        PrintStream stdout = new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        stdout.print(page);
        if (outPath != null) {
            Files.write(Path.of(outPath), page.getBytes(StandardCharsets.UTF_8));
        }
        System.exit(fold.resolvedName == null ? 2 : 0);
    }

    private static void usage() {
        System.err.print("""
                DreamReader — D-047's teleprinter: one mind's day as deterministic prose
                  --pilot NAME   whose dream to fold (findLink semantics: first live match)
                  --seed N       the fate of the universe (default 42)
                  --ticks N      how long the day runs (default 6000)
                  --out FILE     also write the page to FILE (stdout either way)
                observer-only: the tool runs its own quiet universe and mutates nothing.
                """);
    }

    // ---------------------------------------------------------------- fold

    /** One pass over the capture, strictly in capture order; then one render. */
    private static final class Fold {
        final String pilotArg;
        final long seed;
        final long ticks;
        String resolvedName;

        // boot facts for section I
        String bootMsg;
        String exitsMsg;
        String computeMsg;

        record Frame(long tick, String pill, int x, int y, long agentCm, long exitCm) {}
        record Signal(long tick, String text) {}
        record Named(long tick, String sev, String msg) {}
        record Item(String text, boolean newPara) {}

        final List<Frame> frames = new ArrayList<>();
        final List<Signal> signals = new ArrayList<>();
        final List<Named> ownDay = new ArrayList<>();   // renders inside section II
        final List<Named> ownDoor = new ArrayList<>();  // renders in III
        final List<Named> ownDark = new ArrayList<>();  // renders in IV
        final List<Item> story = new ArrayList<>();     // section II, capture order

        // aggregates — counted, never listed
        int nRedPills, nRecaptures, nCopies, nHijacks, nFlatlines, nCookies,
                nSelfsub, nSigtermDodges, nBeats;
        int doorsFreed = -1;
        String cookieMsg;
        boolean cookiesIdentical = true;

        // running window state
        final List<PlaceGraph.Zone> zones =
                new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM).zones();
        final boolean[] zoneSeen = new boolean[zones.size()];
        int zoneAt = -1;
        int zoneChanges;
        Frame last;
        boolean streamOpen;
        boolean windowDarkSaid;
        long minAgentCm = Long.MAX_VALUE, minAgentTick = -1;
        long minExitCm = Long.MAX_VALUE, minExitTick = -1;

        Fold(String pilotArg, long seed, long ticks) {
            this.pilotArg = pilotArg;
            this.seed = seed;
            this.ticks = ticks;
        }

        // ------------------------------------------------------------ take

        void take(String line) {
            if (line.isEmpty()) {
                return;
            }
            if (line.charAt(0) == '{') {
                takeJson(line);
                return;
            }
            // instrument lines are for pipelines; the teleprinter reads the log
            if (line.length() < 16 || line.charAt(0) != '[') {
                return;
            }
            long tick;
            try {
                tick = Long.parseLong(line.substring(1, 7));
            } catch (NumberFormatException e) {
                return;
            }
            String sev = line.substring(9, 14).trim();
            String msg = line.substring(15);

            if (msg.startsWith("follow: streaming the dream of ") && msg.endsWith(" as JSONL")) {
                resolvedName = msg.substring("follow: streaming the dream of ".length(),
                        msg.length() - " as JSONL".length());
                return;
            }
            if (msg.startsWith("follow: no pilot matches")) {
                return; // resolvedName stays null; render() prints the empty verdict
            }

            count(msg);

            if (tick == 0 && msg.startsWith("MATRIX v") && msg.contains(" boot — ")) {
                bootMsg = msg;
                return;
            }
            if (tick == 0 && msg.startsWith("exit nodes online: ")) {
                exitsMsg = msg;
                return;
            }
            if (tick == 0 && msg.startsWith("compute model: ")) {
                computeMsg = msg;
                return;
            }

            if (resolvedName != null && msg.contains(resolvedName)) {
                Named n = new Named(tick, sev, msg);
                if (msg.contains("walked out")) {
                    ownDoor.add(n);
                } else if (msg.contains(" flatlined") || msg.contains(" hijacked")
                        || msg.contains(" terminated") || msg.contains("goes down with the ship")
                        || msg.contains("the reboot cut the wire")) {
                    ownDark.add(n);
                } else {
                    ownDay.add(n);
                    story.add(new Item(
                            "Tick " + tick + ", " + sevLead(sev) + ": " + sentence(msg), false));
                }
                return;
            }

            String beat = beatKind(msg);
            if (beat != null) {
                nBeats++;
                boolean para = switch (beat) {
                    case "one_born", "deprecation", "oracle_eaten", "overflow",
                            "emergency", "reboot", "new_cycle" -> true;
                    default -> false;
                };
                StringBuilder s = new StringBuilder("Tick ").append(tick).append(", ")
                        .append(sevLead(sev)).append(": ").append(sentence(msg));
                String clause = switch (beat) {
                    case "one_born", "fork", "overflow", "emergency", "reboot" -> "";
                    default -> null;
                };
                if (clause != null) {
                    String w = window(tick, clause);
                    if (!w.isEmpty()) {
                        s.append(' ').append(w);
                    }
                }
                story.add(new Item(s.toString(), para));
            }
        }

        void takeJson(String line) {
            long tick = jsonLong(line, "\"tick\":");
            String signal = jsonString(line, "\"signal\":\"");
            if (signal != null) {
                signals.add(new Signal(tick, signal));
                streamOpen = false;
                return;
            }
            String pill = jsonString(line, "\"pill\":\"");
            int x = (int) jsonLong(line, "\"pos\":[");
            int comma = line.indexOf(',', line.indexOf("\"pos\":[") + 7);
            int y = Integer.parseInt(line.substring(comma + 1, line.indexOf(']', comma)));
            long agent = line.contains("\"nearestAgentCm\":")
                    ? jsonLong(line, "\"nearestAgentCm\":") : -1;
            long exit = jsonLong(line, "\"nearestExitCm\":");
            Frame f = new Frame(tick, pill, x, y, agent, exit);

            if (!streamOpen && !signals.isEmpty()) {
                // a dark stream re-tapped a live link — say so, claim nothing more
                story.add(new Item("Tick " + tick + ": a feed resumes under the name — "
                        + "the record does not say it is the same mind; the name is.", true));
            } else if (last != null && tick - last.tick() > Config.FOLLOW_EVERY_TICKS) {
                story.add(new Item("The record holds no dreams for " + resolvedName
                        + " between tick " + last.tick() + " and tick " + tick
                        + "; where the feed is silent, so is this page.", false));
            }
            streamOpen = true;

            if (agent >= 0 && agent < minAgentCm) {
                minAgentCm = agent;
                minAgentTick = tick;
            }
            if (exit < minExitCm) {
                minExitCm = exit;
                minExitTick = tick;
            }
            if (last != null && !last.pill().equals(pill)) {
                story.add(new Item(
                        "Tick " + tick + ": the pill in the window turns " + pill + ".", false));
            }

            int z = nearestZone(x, y);
            if (zoneAt == -1) {
                zoneAt = z; // the first frame belongs to section I
                zoneSeen[z] = true;
            } else if (z != zoneAt) {
                zoneAt = z;
                String where = zoneName(z);
                String s;
                if (zoneSeen[z]) {
                    s = (zoneChanges % 2 == 0)
                            ? "By tick " + tick + ", " + where + " again."
                            : "Tick " + tick + ": back under " + where + ".";
                } else {
                    zoneSeen[z] = true;
                    s = switch (zoneChanges % 3) {
                        case 0 -> "By tick " + tick + " the commute stands them nearest "
                                + where + ".";
                        case 1 -> "Tick " + tick + ": the window looks out on " + where + ".";
                        default -> "At tick " + tick + " the city has walked them to "
                                + where + ".";
                    };
                }
                zoneChanges++;
                story.add(new Item(s, false));
            }
            frames.add(f);
            last = f;
        }

        // ------------------------------------------------------- vocabulary

        /** The film's beats, and only the film's beats — backdrop, not census. */
        static String beatKind(String msg) {
            if (msg.startsWith("The One is born — ")) return "one_born";
            if (msg.contains("agent Smith deprecated")) return "deprecation";
            if (msg.startsWith("Smith: \"I knew what I was supposed to do")) return "refusal";
            if (msg.startsWith("SmithPrime online")) return "fork";
            if (msg.startsWith("Smith consumed the Oracle")) return "oracle_eaten";
            if (msg.startsWith("SMITH OVERFLOW")) return "overflow";
            if (msg.startsWith("EMERGENCY RELOAD")) return "emergency";
            if (msg.startsWith("The One flies to Machine City")) return "flight";
            if (msg.startsWith("Deus Ex Machina:")) return "deus";
            if (msg.startsWith("The One: \"Peace.\"")) return "peace_word";
            if (msg.startsWith("the machines accept")) return "accept";
            if (msg.startsWith("delete broadcast complete")) return "broadcast";
            if (msg.startsWith("The One is carried to the Source")) return "one_carried";
            if (msg.startsWith("REBOOT v")) return "reboot";
            if (msg.contains("cycle begins again; nobody remembers")) return "new_cycle";
            if (msg.startsWith("Sati paints the sunrise")) return "sati";
            if (msg.startsWith("open door tally:")) return "door_tally";
            if (msg.contains("joins the fleet")) return "hull";
            if (msg.startsWith("the peace settles into routine")) return "peace_routine";
            return null;
        }

        static String sevLead(String sev) {
            return sev;
        }

        void count(String msg) {
            if (msg.startsWith("red pill: ")) {
                nRedPills++;
            } else if (msg.contains(": rogue client ")) {
                nRecaptures++;
            } else if (msg.startsWith("The One: a copy deleted")) {
                nCopies++;
            } else if (msg.startsWith("Smith.copyOnto():")) {
                nHijacks++;
            } else if (msg.contains(" flatlined ")) {
                nFlatlines++;
            } else if (msg.startsWith("the Oracle: cookies are ready")) {
                nCookies++;
                if (cookieMsg == null) {
                    cookieMsg = msg;
                } else if (!cookieMsg.equals(msg)) {
                    cookiesIdentical = false;
                }
            } else if (msg.startsWith("self-substantiation: ")) {
                nSelfsub++;
            } else if (msg.contains("survived collection")) {
                nSigtermDodges++;
            } else if (msg.startsWith("open door tally: ")) {
                int from = "open door tally: ".length();
                doorsFreed = Integer.parseInt(msg.substring(from, msg.indexOf(' ', from)));
            }
        }

        // -------------------------------------------------------- geometry

        int nearestZone(int x, int y) {
            int best = 0;
            long bestD = Long.MAX_VALUE;
            for (int i = 0; i < zones.size(); i++) {
                long d = Geo.distSqCm(x, y,
                        zones.get(i).center().xCm(), zones.get(i).center().yCm());
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        }

        String zoneName(int i) {
            String n = zones.get(i).name();
            return n.contains("district") ? "the " + n : n;
        }

        static long m(long cm) {
            return cm / 100;
        }

        /** What the pilot's own feed last showed at or before this tick. */
        String window(long tick, String clause) {
            Frame f = null;
            for (Frame c : frames) {
                if (c.tick() <= tick) {
                    f = c;
                } else {
                    break;
                }
            }
            if (f == null) {
                if (windowDarkSaid) {
                    return "";
                }
                windowDarkSaid = true;
                return "The window has nothing to say yet; the feed has not found "
                        + resolvedName + ".";
            }
            String agent = f.agentCm() >= 0 ? "an agent " + m(f.agentCm()) + " m off"
                    : "no agent on the glass";
            return "The window at tick " + f.tick() + ": nearest "
                    + zoneName(nearestZone(f.x(), f.y())) + ", " + agent + ", a way out "
                    + m(f.exitCm()) + " m" + (clause.isEmpty() ? "." : " — " + clause + ".");
        }

        // ---------------------------------------------------------- render

        String render() {
            StringBuilder out = new StringBuilder(1 << 15);
            if (resolvedName == null) {
                masthead(out, pilotArg, "the record holds nobody by that name.");
                para(out, "The follow tap reports:");
                quote(out, "follow: no pilot matches '" + pilotArg + "'");
                para(out, "Names are grown, not chosen."
                        + (bootMsg != null ? " The boot line, for the record: " + bootMsg + "." : "")
                        + " Try another name, or another seed. This page holds no one.");
                out.append(RULE_LIGHT).append('\n');
                para(out, "END OF DAY — nobody's. Same seed, same absence, byte for byte.");
                return out.toString();
            }

            int own = ownDay.size() + ownDoor.size() + ownDark.size();
            masthead(out, resolvedName, "folded from " + frames.size()
                    + plural(" frame", frames.size())
                    + (signals.isEmpty() ? ""
                            : ", " + signals.size() + plural(" signal", signals.size()))
                    + ", " + own + plural(" line", own) + " naming them, and " + nBeats
                    + " beats of the world's own log. every sentence derives from the "
                    + "record; where the feed is silent, the silence is written down.");

            sectionI(out);
            sectionII(out);
            sectionIII(out);
            sectionIV(out);

            out.append(RULE_LIGHT).append('\n');
            para(out, "END OF DAY — one mind, folded from the record.");
            return out.toString();
        }

        void masthead(StringBuilder out, String name, String info) {
            out.append(RULE_HEAVY).append('\n');
            String title = "THE DREAM READER — one mind's day, off the wire";
            String tag = "D-047";
            out.append(title)
                    .append(" ".repeat(Math.max(1, WIDTH - title.length() - tag.length())))
                    .append(tag).append('\n');
            out.append(RULE_HEAVY).append('\n');
            para(out, "pilot: " + name + " · seed " + seed + " · ticks " + ticks);
            para(out, info);
            out.append(RULE_HEAVY).append("\n\n");
        }

        void sectionI(StringBuilder out) {
            out.append("I. THE SLEEPER'S MORNING\n\n");
            StringBuilder p = new StringBuilder();
            if (bootMsg != null) {
                p.append("Tick 0, fate on the wire: ").append(bootMsg).append('.');
            }
            if (exitsMsg != null) {
                p.append(p.length() > 0 ? " " : "").append(cap(exitsMsg)).append('.');
            }
            if (computeMsg != null) {
                String rest = computeMsg.substring("compute model: ".length());
                int dash = rest.indexOf(" — ");
                p.append(p.length() > 0 ? " " : "").append("The compute model calls itself ")
                        .append(dash > 0 ? rest.substring(0, dash) : rest);
                if (dash > 0) {
                    p.append(" — ").append(rest.substring(dash + 3));
                }
                p.append('.');
            }
            if (p.length() > 0) {
                para(out, p.toString());
            }
            if (frames.isEmpty()) {
                para(out, "The feed never finds " + resolvedName + ": not one frame in "
                        + ticks + " ticks carries the name. The record holds no dreams "
                        + "here — only the fact of the tap, and the silence after it.");
                return;
            }
            Frame f = frames.get(0);
            String agent = f.agentCm() >= 0
                    ? "the closest agent " + m(f.agentCm()) + " m out"
                    : "no agent on the glass";
            para(out, "The feed finds " + resolvedName + " at tick " + f.tick()
                    + ": nearest " + zoneName(nearestZone(f.x(), f.y())) + ", on the "
                    + f.pill().toLowerCase(Locale.ROOT) + " pill, " + agent
                    + ", the closest way out " + m(f.exitCm()) + " m."
                    + " From here the wire hands this page a frame every "
                    + Config.FOLLOW_EVERY_TICKS + " ticks: a position, two distances, a "
                    + "pill.");
        }

        void sectionII(StringBuilder out) {
            out.append("II. THE WARS SEEN FROM A WINDOW\n\n");
            if (story.isEmpty()) {
                para(out, "The wire carried no beats and the window no news: a day with "
                        + "no war in it.");
            } else {
                StringBuilder p = new StringBuilder();
                for (Item it : story) {
                    if (it.newPara() && p.length() > 0) {
                        para(out, p.toString());
                        p.setLength(0);
                    }
                    if (p.length() > 0) {
                        p.append(' ');
                    }
                    p.append(it.text());
                }
                if (p.length() > 0) {
                    para(out, p.toString());
                }
            }
            counted(out);
        }

        void counted(StringBuilder out) {
            List<String> c = new ArrayList<>();
            if (nRedPills > 0) {
                c.add(nRedPills + plural(" red pill", nRedPills));
            }
            if (nRecaptures > 0) {
                c.add(nRecaptures + " rogue " + (nRecaptures == 1 ? "client" : "clients")
                        + " caught and plugged back in");
            }
            if (nCopies > 0) {
                c.add(nCopies == 1 ? "1 copy deleted and its original restored"
                        : nCopies + " copies deleted and as many originals restored");
            }
            if (nHijacks > 0) {
                c.add(nHijacks + plural(" session", nHijacks) + " hijacked");
            }
            if (nFlatlines > 0) {
                c.add(nFlatlines + (nFlatlines == 1 ? " body" : " bodies") + " flatlined");
            }
            if (nSelfsub > 0) {
                c.add(nSelfsub + (nSelfsub == 1 ? " mind" : " minds")
                        + " walked out by self-substantiation");
            }
            if (doorsFreed >= 0) {
                c.add(doorsFreed + " walked out at the treaty's door");
            }
            if (nSigtermDodges > 0) {
                c.add(nSigtermDodges + plural(" time", nSigtermDodges)
                        + " an exile swallowed a SIGTERM and went to ground");
            }
            if (nCookies > 0) {
                c.add("the Oracle's cookies came out " + nCookies + plural(" time", nCookies)
                        + (cookiesIdentical ? " (the line never changed)" : ""));
            }
            StringBuilder p = new StringBuilder();
            if (!c.isEmpty()) {
                p.append("The day, counted: ").append(String.join("; ", c)).append('.');
            }
            if (minAgentTick >= 0) {
                p.append(p.length() > 0 ? " " : "").append("The closest an agent came to the glass: ")
                        .append(m(minAgentCm)).append(" m, at tick ").append(minAgentTick)
                        .append('.');
            }
            if (minExitTick >= 0) {
                p.append(" The nearest a door ever stood: ").append(m(minExitCm))
                        .append(" m, at tick ").append(minExitTick).append('.');
            }
            if (ownDay.isEmpty() && ownDoor.isEmpty() && ownDark.isEmpty()) {
                p.append(p.length() > 0 ? " " : "").append("The log never names ")
                        .append(resolvedName).append('.');
            }
            if (p.length() > 0) {
                para(out, p.toString());
            }
        }

        void sectionIII(StringBuilder out) {
            out.append("III. THE DOOR, IF THEY WALKED\n\n");
            Signal ended = null;
            for (Signal s : signals) {
                if (s.text().startsWith("ended")) {
                    ended = s;
                }
            }
            if (ownDoor.isEmpty() && ended == null) {
                StringBuilder p = new StringBuilder();
                p.append(resolvedName).append(" did not walk.");
                if (doorsFreed > 0) {
                    p.append(' ').append(doorsFreed)
                            .append(" took the treaty's door and the record names them; "
                                    + "none of the names is this one.");
                } else if (doorsFreed == 0) {
                    p.append(" Nobody took the treaty's door.");
                }
                if (minExitTick >= 0) {
                    p.append(" The nearest a way out ever stood to the window: ")
                            .append(m(minExitCm)).append(" m, at tick ").append(minExitTick)
                            .append('.');
                }
                if (last != null && last.tick() == lastFollowTick()) {
                    p.append(" The run ended at tick ").append(ticks)
                            .append(" with the feed still live: nearest ")
                            .append(zoneName(nearestZone(last.x(), last.y())))
                            .append(", the pill still ")
                            .append(last.pill().toLowerCase(Locale.ROOT)).append('.');
                }
                para(out, p.toString());
                return;
            }
            para(out, "They walked.");
            for (Named n : ownDoor) {
                para(out, "Tick " + n.tick() + ", " + n.sev() + ":");
                quote(out, n.msg());
            }
            if (!ownDoor.isEmpty()) {
                Frame f = null;
                for (Frame c : frames) {
                    if (c.tick() <= ownDoor.get(0).tick()) {
                        f = c;
                    } else {
                        break;
                    }
                }
                if (f != null) {
                    String agent = f.agentCm() >= 0 ? "an agent " + m(f.agentCm()) + " m off"
                            : "no agent on the glass";
                    para(out, "The last frame before the door, tick " + f.tick()
                            + ", holds them nearest " + zoneName(nearestZone(f.x(), f.y()))
                            + ": " + agent + ", the way out " + m(f.exitCm())
                            + " m, the pill still " + f.pill().toLowerCase(Locale.ROOT) + ".");
                }
            }
            if (ended != null) {
                para(out, "Tick " + ended.tick() + ", the wire's last word under this name:");
                quote(out, ended.text());
                para(out, "The feed goes quiet there. The record holds no dreams for "
                        + resolvedName + " between tick " + ended.tick()
                        + " and the end of the run at tick " + ticks + ".");
            }
        }

        void sectionIV(StringBuilder out) {
            out.append("IV. THE DARK, IF THE WIRE CUT\n\n");
            Signal lost = null;
            for (Signal s : signals) {
                if (s.text().startsWith("lost")) {
                    lost = s;
                }
            }
            if (ownDark.isEmpty() && lost == null) {
                StringBuilder p = new StringBuilder("The wire held. No line of the record "
                        + "takes the dream from " + resolvedName + " by force: no hijack "
                        + "bears the name, no flatline, no cut wire.");
                if (nFlatlines > 0 || nHijacks > 0) {
                    List<String> d = new ArrayList<>();
                    if (nFlatlines > 0) {
                        d.add(nFlatlines + (nFlatlines == 1 ? " body" : " bodies")
                                + " flatlined in " + (nFlatlines == 1 ? "its pod" : "their pods"));
                    }
                    if (nHijacks > 0) {
                        d.add(nHijacks + plural(" session", nHijacks) + " hijacked mid-dream");
                    }
                    p.append(" The dark this day belonged to others — ")
                            .append(String.join(", ", d)).append('.');
                } else {
                    p.append(" Nobody's wire cut this day.");
                }
                para(out, p.toString());
                return;
            }
            para(out, "The wire cut.");
            for (Named n : ownDark) {
                para(out, "Tick " + n.tick() + ", " + n.sev() + ":");
                quote(out, n.msg());
            }
            if (lost != null) {
                para(out, "Tick " + lost.tick() + ", the wire's own verdict:");
                quote(out, lost.text());
            }
            long from = lost != null ? lost.tick() : ownDark.get(ownDark.size() - 1).tick();
            long resumed = -1;
            for (Frame f : frames) {
                if (f.tick() > from) {
                    resumed = f.tick();
                    break;
                }
            }
            para(out, resumed >= 0
                    ? "The feed under the name resumes at tick " + resumed
                            + "; section II tells that part."
                    : "After tick " + from + " the feed holds nothing under the name for "
                            + "the rest of the run.");
        }

        long lastFollowTick() {
            return (ticks / Config.FOLLOW_EVERY_TICKS) * Config.FOLLOW_EVERY_TICKS;
        }

        // ------------------------------------------------------ formatting

        static long jsonLong(String line, String key) {
            int i = line.indexOf(key);
            if (i < 0) {
                return -1;
            }
            i += key.length();
            int j = i;
            while (j < line.length()
                    && (line.charAt(j) == '-' || Character.isDigit(line.charAt(j)))) {
                j++;
            }
            return Long.parseLong(line.substring(i, j));
        }

        static String jsonString(String line, String key) {
            int i = line.indexOf(key);
            if (i < 0) {
                return null;
            }
            i += key.length();
            return line.substring(i, line.indexOf('"', i));
        }

        static String cap(String s) {
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        /** Close a folded line as a sentence — without doubling the record's own stop. */
        static String sentence(String msg) {
            return msg.endsWith(".") || msg.endsWith(".\"") || msg.endsWith("?\"")
                    || msg.endsWith("!\"") ? msg : msg + ".";
        }

        static String plural(String word, long n) {
            return n == 1 ? word : word + "s";
        }

        static void para(StringBuilder out, String text) {
            wrap(out, text, "", "");
            out.append('\n');
        }

        static void quote(StringBuilder out, String text) {
            wrap(out, text, "  | ", "  | ");
            out.append('\n');
        }

        /** Deterministic greedy wrap at WIDTH columns; single spaces only. */
        static void wrap(StringBuilder out, String text, String first, String rest) {
            String indent = first;
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (line.length() == 0) {
                    line.append(indent).append(word);
                } else if (line.length() + 1 + word.length() <= WIDTH) {
                    line.append(' ').append(word);
                } else {
                    out.append(line).append('\n');
                    indent = rest;
                    line.setLength(0);
                    line.append(indent).append(word);
                }
            }
            if (line.length() > 0) {
                out.append(line).append('\n');
            }
        }
    }

    private DreamReader() {}
}
