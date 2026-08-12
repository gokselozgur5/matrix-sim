import matrix.core.Config;
import matrix.core.Geo;
import matrix.core.PlaceGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * D-047's fold: three captured feeds become one day, in four movements.
 *
 * <ol>
 *   <li><b>The sleeper's morning</b> — the world they woke into, and the first
 *       thing the wire ever showed of them.</li>
 *   <li><b>The wars seen from a window</b> — the record's beats and the three
 *       numbers the glass held while each one happened.</li>
 *   <li><b>The door, if they walked</b>.</li>
 *   <li><b>The dark, if the wire cut</b>.</li>
 * </ol>
 *
 * <b>Every movement is true when it is empty.</b> That is the design, not a
 * fallback: "they did not walk" and "the wire held" are sentences the page
 * owes, because a reader who only ever sees pages about people to whom things
 * happened learns nothing about a world whose default is peace. Where the feed
 * is silent the silence is WRITTEN DOWN — a gap in the frames is a sentence,
 * not a smoothed-over join.
 *
 * <h2>The output is facts, not prose</h2>
 *
 * The fold emits {@link Fact}s: a movement, a kind, a tick and the record's own
 * strings and numbers. It never writes a sentence. That boundary is what lets a
 * voice be swapped or audited later — and what lets a diff, rather than a
 * careful reader, catch a narrator that quietly rounds a distance or drops a
 * tick.
 *
 * <h2>No invented facts</h2>
 *
 * Every fact here derives from a line {@link Capture} caught. Where a name is
 * shared by more than one mind, a line the capture layer could not prove is the
 * pilot's arrives as UNPROVEN and stays that way through the fold: it never
 * gets to assert that they walked or that their wire was cut.
 */
final class Fold {

    /** One derived fact: which movement it belongs to, what kind it is, and the record's own values. */
    record Fact(String movement, String kind, long tick, List<String> args) {
        static Fact of(String movement, String kind, long tick, String... args) {
            return new Fact(movement, kind, tick, List.of(args));
        }

        /** The fact stream's wire form — stable, greppable, and identical under every voice. */
        String line() {
            StringBuilder sb = new StringBuilder(96);
            sb.append("FACT ").append(movement).append(' ').append(kind).append(" t=").append(tick);
            for (String a : args) {
                sb.append(" | ").append(a);
            }
            return sb.toString();
        }

        String arg(int i) {
            return i < args.size() ? args.get(i) : "";
        }

        long num(int i) {
            return Long.parseLong(arg(i));
        }
    }

    static final String I = "I";
    static final String II = "II";
    static final String III = "III";
    static final String IV = "IV";

    private final Capture c;
    private final List<PlaceGraph.Zone> zones =
            new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM).zones();
    private final List<Fact> facts = new ArrayList<>();

    private Fold(Capture capture) {
        this.c = capture;
    }

    /** Fold a capture into its facts, once, in capture order. */
    static List<Fact> of(Capture capture) {
        Fold f = new Fold(capture);
        f.run();
        return List.copyOf(f.facts);
    }

    private void add(String movement, String kind, long tick, String... args) {
        facts.add(Fact.of(movement, kind, tick, args));
    }

    /** The four movements, in order, with the name the page gives each. */
    static String title(String movement) {
        return switch (movement) {
            case I -> "THE SLEEPER'S MORNING";
            case II -> "THE WARS SEEN FROM A WINDOW";
            case III -> "THE DOOR, IF THEY WALKED";
            case IV -> "THE DARK, IF THE WIRE CUT";
            default -> movement;
        };
    }

    /**
     * The fold stated flatly: every fact under its movement, one per line.
     * This is not a debug mode — it is the page a machine should read, and it
     * is what a narrator will later have to render without changing a single
     * value on it.
     */
    static String plain(List<Fact> facts) {
        StringBuilder out = new StringBuilder(1 << 14);
        Fact subject = facts.get(0);
        out.append("DREAM READER — the fold, unvoiced\n");
        out.append("pilot: ").append(subject.arg(0))
                .append(" · resolved: ")
                .append(subject.arg(1).isEmpty() ? "(nobody)" : subject.arg(1))
                .append(" · seed ").append(subject.arg(2))
                .append(" · ticks ").append(subject.arg(3)).append("\n\n");
        String movement = "";
        for (Fact f : facts) {
            if (f.movement().equals("HEAD")) {
                continue;
            }
            if (!f.movement().equals(movement)) {
                movement = f.movement();
                out.append(movement.equals("END") ? "END.\n"
                        : movement + ". " + title(movement) + "\n");
            }
            out.append("  ").append(f.kind());
            if (f.tick() >= 0) {
                out.append(" t=").append(f.tick());
            }
            for (String a : f.args()) {
                out.append(" | ").append(a);
            }
            out.append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ run

    private void run() {
        masthead();
        if (c.resolvedName == null) {
            add(I, "nobody", -1, c.pilotArg, String.valueOf(c.censusMatches));
            add("END", "end", -1, "nobody");
            return;
        }
        movementI();
        movementII();
        movementIII();
        movementIV();
        add("END", "end", -1, "day");
    }

    private void masthead() {
        add("HEAD", "subject", -1, c.pilotArg,
                c.resolvedName == null ? "" : c.resolvedName,
                String.valueOf(c.seed), String.valueOf(c.ticks));
        add("HEAD", "provenance", -1,
                String.valueOf(c.frames.size()), String.valueOf(c.signals.size()),
                String.valueOf(c.naming.size()), String.valueOf(c.beats.size()));
        if (c.resolvedName != null) {
            add("HEAD", "binding", -1, String.valueOf(c.namesakes),
                    c.podAddress == null ? "" : c.podAddress,
                    String.valueOf(c.namesakeLinesDropped));
        }
    }

    // ------------------------------------------------- I. the sleeper's morning

    private void movementI() {
        for (Capture.Line l : c.boot) {
            String kind = bootKind(l.msg());
            if (kind != null) {
                add(I, kind, l.tick(), l.sev(), l.msg());
            }
        }
        if (c.frames.isEmpty()) {
            add(I, "never_found", -1, c.resolvedName, String.valueOf(c.ticks));
            return;
        }
        Capture.Frame f = c.frames.get(0);
        add(I, "first_window", f.tick(), c.resolvedName, zoneAt(f), f.pill(),
                String.valueOf(f.agentCm()), String.valueOf(f.exitCm()),
                String.valueOf(Config.FOLLOW_EVERY_TICKS));
    }

    private static String bootKind(String msg) {
        if (msg.startsWith("MATRIX v") && msg.contains(" boot — ")) return "boot";
        if (msg.startsWith("exit nodes online: ")) return "exits";
        if (msg.startsWith("compute model: ")) return "compute";
        return null;
    }

    // ----------------------------------------- II. the wars seen from a window

    /**
     * One walk over every captured item in capture order. Frames drive the
     * window; beats and named lines interleave exactly where the record put
     * them, so a beat is always narrated against the glass as it stood.
     */
    private void movementII() {
        List<Object> stream = new ArrayList<>();
        stream.addAll(c.frames);
        stream.addAll(c.signals);
        stream.addAll(c.beats);
        stream.addAll(c.naming);
        stream.sort((a, b) -> Integer.compare(seqOf(a), seqOf(b)));

        boolean[] zoneSeen = new boolean[zones.size()];
        int zoneAt = -1;
        int zoneChanges = 0;
        Capture.Frame last = null;
        boolean streamOpen = false;
        boolean sawSignal = false;
        long minAgentCm = Long.MAX_VALUE, minAgentTick = -1;
        long minExitCm = Long.MAX_VALUE, minExitTick = -1;
        int items = 0;

        for (Object item : stream) {
            if (item instanceof Capture.Frame f) {
                if (!streamOpen && sawSignal) {
                    add(II, "feed_resumes", f.tick(), c.resolvedName);
                    items++;
                } else if (last != null && f.tick() - last.tick() > Config.FOLLOW_EVERY_TICKS) {
                    add(II, "feed_gap", f.tick(), c.resolvedName, String.valueOf(last.tick()));
                    items++;
                }
                streamOpen = true;
                if (f.agentCm() >= 0 && f.agentCm() < minAgentCm) {
                    minAgentCm = f.agentCm();
                    minAgentTick = f.tick();
                }
                if (f.exitCm() < minExitCm) {
                    minExitCm = f.exitCm();
                    minExitTick = f.tick();
                }
                if (last != null && !last.pill().equals(f.pill())) {
                    add(II, "pill_turns", f.tick(), f.pill().toLowerCase(Locale.ROOT));
                    items++;
                }
                int z = nearestZone(f.xCm(), f.yCm());
                if (zoneAt == -1) {
                    zoneAt = z;
                    zoneSeen[z] = true;
                } else if (z != zoneAt) {
                    zoneAt = z;
                    add(II, "zone", f.tick(), zoneName(z),
                            zoneSeen[z] ? "again" : "first", String.valueOf(zoneChanges));
                    zoneSeen[z] = true;
                    zoneChanges++;
                    items++;
                }
                last = f;
            } else if (item instanceof Capture.Signal s) {
                sawSignal = true;
                streamOpen = false;
                // the signal's own sentence belongs to III or IV, not here
            } else if (item instanceof Capture.Line beat) {
                String kind = Beats.kindOf(beat.msg());
                add(II, "beat", beat.tick(), beat.sev(), beat.msg(), kind == null ? "" : kind);
                items++;
                Capture.Frame w = windowAt(beat.tick());
                if (w != null) {
                    add(II, "beat_window", w.tick(), zoneAt(w),
                            String.valueOf(w.agentCm()), String.valueOf(w.exitCm()),
                            kind == null ? "" : kind);
                } else {
                    add(II, "beat_window_dark", beat.tick(), c.resolvedName);
                }
            } else if (item instanceof Capture.Named n) {
                if (movementOf(n.line().msg()) == II) {
                    add(II, "named", n.line().tick(), n.line().sev(), n.line().msg(),
                            n.claim().name());
                    items++;
                }
            }
        }

        if (items == 0) {
            add(II, "quiet", -1);
        }
        counted(minAgentCm, minAgentTick, minExitCm, minExitTick);
    }

    private void counted(long minAgentCm, long minAgentTick, long minExitCm, long minExitTick) {
        Capture.Tally t = c.tally;
        List<String> args = new ArrayList<>();
        args.add("red_pills=" + t.redPills);
        args.add("recaptures=" + t.recaptures);
        args.add("copies=" + t.copies);
        args.add("hijacks=" + t.hijacks);
        args.add("flatlines=" + t.flatlines);
        args.add("selfsubs=" + t.selfsubs);
        args.add("door_tally=" + t.doorTally);
        args.add("sigterm_dodges=" + t.sigtermDodges);
        args.add("cookies=" + t.cookies);
        args.add("cookies_identical=" + t.cookiesIdentical);
        facts.add(new Fact(II, "counted", -1, List.copyOf(args)));
        if (minAgentTick >= 0) {
            add(II, "closest_agent", minAgentTick, String.valueOf(minAgentCm));
        }
        if (minExitTick >= 0) {
            add(II, "nearest_door", minExitTick, String.valueOf(minExitCm));
        }
        if (c.naming.isEmpty()) {
            add(II, "never_named", -1, c.resolvedName);
        }
    }

    // --------------------------------------------- III. the door, if they walked

    private void movementIII() {
        Capture.Signal ended = lastSignal("ended");
        List<Capture.Named> door = namedIn(III);
        Capture.Named proven = null;
        int unproven = 0;
        for (Capture.Named n : door) {
            if (n.claim() == Capture.Claim.MINE) {
                proven = proven == null ? n : proven;
            } else {
                unproven++;
            }
        }
        // The tap's own signal binds to the MIND (#765), so it may assert on
        // its own; a line that merely carries the name may not.
        if (proven == null && ended == null) {
            add(III, "did_not_walk", -1, c.resolvedName, String.valueOf(c.tally.doorTally));
            if (unproven > 0) {
                add(III, "unproven_door", -1, String.valueOf(unproven), c.resolvedName,
                        String.valueOf(c.namesakes));
            }
            Capture.Frame last = c.frames.isEmpty() ? null : c.frames.get(c.frames.size() - 1);
            if (last != null && last.tick() == lastWindowTick()) {
                add(III, "still_live", c.ticks, zoneAt(last), last.pill().toLowerCase(Locale.ROOT));
            }
            return;
        }
        add(III, "walked", proven == null ? ended.tick() : proven.line().tick(), c.resolvedName);
        if (proven != null) {
            add(III, "door_line", proven.line().tick(), proven.line().sev(), proven.line().msg(),
                    Beats.kindOf(proven.line().msg()) == null ? "" : "beat");
            Capture.Frame w = windowAt(proven.line().tick());
            if (w != null) {
                add(III, "last_window", w.tick(), zoneAt(w), String.valueOf(w.agentCm()),
                        String.valueOf(w.exitCm()), w.pill().toLowerCase(Locale.ROOT));
            }
        }
        if (ended != null) {
            add(III, "signal_end", ended.tick(), ended.text());
            add(III, "silence_after", ended.tick(), c.resolvedName, String.valueOf(c.ticks),
                    proven != null && proven.line().msg().startsWith("self-substantiation")
                            ? "selfsub" : "treaty");
        }
    }

    // ------------------------------------------- IV. the dark, if the wire cut

    private void movementIV() {
        Capture.Signal lost = lastSignal("lost");
        List<Capture.Named> dark = namedIn(IV);
        List<Capture.Named> proven = new ArrayList<>();
        int unproven = 0;
        for (Capture.Named n : dark) {
            if (n.claim() == Capture.Claim.MINE) {
                proven.add(n);
            } else {
                unproven++;
            }
        }
        if (proven.isEmpty() && lost == null) {
            add(IV, "wire_held", -1, c.resolvedName);
            if (unproven > 0) {
                add(IV, "unproven_dark", -1, String.valueOf(unproven), c.resolvedName,
                        String.valueOf(c.namesakes));
            }
            add(IV, "dark_elsewhere", -1, String.valueOf(c.tally.flatlines),
                    String.valueOf(c.tally.hijacks));
            return;
        }
        add(IV, "wire_cut", proven.isEmpty() ? lost.tick() : proven.get(0).line().tick(),
                c.resolvedName);
        for (Capture.Named n : proven) {
            add(IV, "dark_line", n.line().tick(), n.line().sev(), n.line().msg());
        }
        if (unproven > 0) {
            add(IV, "unproven_dark", -1, String.valueOf(unproven), c.resolvedName,
                    String.valueOf(c.namesakes));
        }
        if (lost != null) {
            add(IV, "signal_lost", lost.tick(), lost.text());
        }
        long from = lost != null ? lost.tick() : proven.get(proven.size() - 1).line().tick();
        long resumed = -1;
        for (Capture.Frame f : c.frames) {
            if (f.tick() > from) {
                resumed = f.tick();
                break;
            }
        }
        if (resumed >= 0) {
            add(IV, "resumed", resumed);
        } else {
            add(IV, "never_resumed", from);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Which movement a line that carries the pilot's name belongs to. */
    private static String movementOf(String msg) {
        if (msg.contains("walked out")) {
            return III;
        }
        if (msg.contains(" flatlined") || msg.contains(" hijacked") || msg.contains(" terminated")
                || msg.contains("goes down with the ship") || msg.contains("the reboot cut the wire")) {
            return IV;
        }
        return II;
    }

    private List<Capture.Named> namedIn(String movement) {
        List<Capture.Named> out = new ArrayList<>();
        for (Capture.Named n : c.naming) {
            if (movementOf(n.line().msg()).equals(movement)) {
                out.add(n);
            }
        }
        return out;
    }

    private Capture.Signal lastSignal(String prefix) {
        Capture.Signal found = null;
        for (Capture.Signal s : c.signals) {
            if (s.text().startsWith(prefix)) {
                found = s;
            }
        }
        return found;
    }

    /** What the pilot's own feed last showed at or before this tick. */
    private Capture.Frame windowAt(long tick) {
        Capture.Frame found = null;
        for (Capture.Frame f : c.frames) {
            if (f.tick() <= tick) {
                found = f;
            } else {
                break;
            }
        }
        return found;
    }

    private long lastWindowTick() {
        return (c.ticks / Config.FOLLOW_EVERY_TICKS) * Config.FOLLOW_EVERY_TICKS;
    }

    private static int seqOf(Object item) {
        if (item instanceof Capture.Frame f) return f.seq();
        if (item instanceof Capture.Signal s) return s.seq();
        if (item instanceof Capture.Line l) return l.seq();
        if (item instanceof Capture.Named n) return n.line().seq();
        return Integer.MAX_VALUE;
    }

    private String zoneAt(Capture.Frame f) {
        return zoneName(nearestZone(f.xCm(), f.yCm()));
    }

    private int nearestZone(int xCm, int yCm) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < zones.size(); i++) {
            long d = Geo.distSqCm(xCm, yCm, zones.get(i).center().xCm(), zones.get(i).center().yCm());
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private String zoneName(int i) {
        String n = zones.get(i).name();
        return n.contains("district") ? "the " + n : n;
    }
}
