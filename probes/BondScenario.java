import matrix.Simulation;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Probe: the Room 303 clause, forced — the miracle, and the refusal after it.
 *
 * The clause cannot be proven by a canonical run in which no bonded mind
 * dies twice. Seed 42 gives the FIRING for free (a woven edge, a partner's
 * death, a brain that lives), but the second half of the ruling — the same
 * edge REFUSING to pay again — needs a world where the resurrected are
 * hunted again. This probe scripts that world.
 *
 * <p>The pressure is the ops console's own, not a back door: {@code agent}
 * deploys one more IDS daemon, exactly as an operator may at any time. More
 * daemons means more contact with awakened minds, means more of the 10%
 * kill roll landing, means a resurrected mind eventually dies a second
 * time. Nothing here reaches into the world's state — no reflection writes,
 * no field pokes, no forged marks. If the clause could be made to fire
 * twice on one edge, THIS is the run that would do it.
 *
 * <p><b>The defaults are a measurement, not a guess.</b> The probe shipped
 * at {@code 20000 42 24} and printed {@code refused_spent=0 VERDICT
 * NOT_DEMONSTRATED} — eight firings on eight different edges and not one
 * second death to refuse, because a mind the clause saves is caught and
 * plugged back in the next tick nine times out of ten and stops being prey.
 * The scale that reaches the refusal is 40,000 ticks and 60 daemons: 24
 * firings, 11 spent-edge refusals, 10 stand-downs. An instrument whose own
 * defaults miss its own verdict is a promise, so the defaults are the
 * arguments that reach it and the shipped run is the judged one.
 *
 * <p><b>What the verdict asserts.</b> Two things, both read off the world's
 * own log, because the clause's contract is a log contract:
 * <ol>
 * <li>the unwriting — no {@code cannot live without the mind} line for a
 *     mind on the tick the clause just saved them. That the death was not
 *     written is the whole of #377, and it is checked per (tick, name) pair
 *     rather than by a count;</li>
 * <li>once per edge — at least one {@code refused — the edge is spent}, so
 *     the guard is watched refusing and not merely believed.</li>
 * </ol>
 * What it deliberately does NOT assert is that a saved mind never flatlines
 * again. The clause is one payment, not immunity: past the refusal the
 * second death is written exactly as D-013 says, and eleven of them are.
 *
 * Usage: java -cp out:probes/out BondScenario [ticks] [seed] [daemon_cap]
 */
public final class BondScenario {

    /** One more daemon every this many ticks, once the first miracle has happened. */
    private static final int PRESSURE_EVERY_TICKS = 100;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 40_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
        int daemonCap = args.length > 2 ? Integer.parseInt(args[2]) : 60;

        // The stream is read as it is written, one line at a time. A probe
        // that re-scans a growing buffer every tick is quadratic in the run
        // length, which for a 40,000-tick scenario is the difference between
        // a minute and an afternoon.
        Tally tally = new Tally();
        Simulation sim = new Simulation(seed, tally, null);
        int deployed = 0;

        for (long t = 1; t <= ticks; t++) {
            sim.tickOnce();
            // The pressure starts only AFTER the first miracle, so the
            // firing itself is the canonical one and only the aftermath is
            // scripted. The scenario tests the SECOND death, not the first.
            if (tally.fired > 0 && deployed < daemonCap && t % PRESSURE_EVERY_TICKS == 0) {
                sim.commandAgent();
                deployed++;
            }
        }
        tally.flush2();

        System.out.println("SCENARIO seed=" + seed + " ticks=" + ticks
                + " daemons_deployed=" + deployed
                + " fired=" + tally.fired
                + " refused_spent=" + tally.refusedSpent
                + " refused_fated=" + tally.refusedFated
                + " stand_downs=" + tally.standDowns
                + " flatlines=" + tally.flatlines
                + " written_anyway=" + tally.writtenAnyway);
        if (tally.writtenAnyway > 0) {
            System.out.println("VERDICT UNWRITING_BROKEN");
        } else if (tally.fired > 0 && tally.refusedSpent > 0) {
            System.out.println("VERDICT ONCE_PER_EDGE_HELD");
        } else {
            System.out.println("VERDICT NOT_DEMONSTRATED");
        }
    }

    /** A sink that counts the clause's lines as the world speaks them. */
    private static final class Tally extends OutputStream {
        private final StringBuilder line = new StringBuilder();
        /** {@code <tick>|<name>} for every mind the clause saved, on the tick it saved them. */
        private final Set<String> savedThisTick = new HashSet<>();
        int fired, refusedSpent, refusedFated, standDowns, flatlines, writtenAnyway;

        @Override
        public void write(int b) {
            if (b == '\n') {
                take(line.toString());
                line.setLength(0);
                return;
            }
            line.append((char) (b & 0xFF));
        }

        void flush2() {
            if (line.length() > 0) {
                take(line.toString());
                line.setLength(0);
            }
        }

        private void take(String raw) {
            String s = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            if (s.contains("Room 303: ") && s.contains("paid it back")) {
                fired++;
                savedThisTick.add(tickOf(s) + "|" + between(s, "paid it back for ", " —"));
                System.out.println("FIRING " + s);
            } else if (s.contains("Room 303: refused") && s.contains("spent")) {
                refusedSpent++;
                System.out.println("REFUSAL " + s);
            } else if (s.contains("Room 303: refused") && s.contains("fated")) {
                refusedFated++;
                System.out.println("REFUSAL " + s);
            } else if (s.contains("already answered on an older edge")) {
                standDowns++;
            } else if (s.contains("cannot live without the mind")) {
                flatlines++;
                // The death the clause unwrote must not also be written. A
                // LATER flatline for the same mind is lawful — the mark is
                // spent and D-013 runs — so the pair is (tick, name) and not
                // the name alone.
                String key = tickOf(s) + "|" + between(s, "the mind — ", " flatlined");
                if (savedThisTick.contains(key)) {
                    writtenAnyway++;
                    System.out.println("BROKEN the unwriting did not hold: " + s);
                }
            }
        }

        /** The bracketed tick every event line opens with: {@code [001850] FATE …}. */
        private static String tickOf(String s) {
            return between(s, "[", "]");
        }

        private static String between(String s, String open, String close) {
            int a = s.indexOf(open);
            if (a < 0) {
                return "?";
            }
            a += open.length();
            int b = s.indexOf(close, a);
            return b < 0 ? "?" : s.substring(a, b);
        }
    }

    private BondScenario() {}
}
