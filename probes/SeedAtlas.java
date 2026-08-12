import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Probe: a census of the multiverse.
 *
 * Runs each seed in [from, to] quietly and reports one row per universe:
 * when the One came, when the war overflowed, whether the treaty landed,
 * whether a second Thomas arrived inside the tick budget — and whether
 * any universe took the branch never yet seen in the wild: overflow with
 * no One alive (the Architect's emergency reload).
 *
 * Verdicts per row: FULL_ARC (through second birth) · TREATY (peace but
 * no rebirth yet) · WAR (overflow, no peace) · QUIET (no overflow) ·
 * OLD_PLAYBOOK (emergency reload seen).
 *
 * One command regenerates the table after any mechanics change:
 *   java -cp out:probes/out SeedAtlas 1 20 6000
 *
 * Usage: java -cp out:probes/out SeedAtlas [fromSeed] [toSeed] [ticks]
 */
public final class SeedAtlas {

    public static void main(String[] args) {
        matrix.Streams.utf8();
        long from = args.length > 0 ? Long.parseLong(args[0]) : 1;
        long to = args.length > 1 ? Long.parseLong(args[1]) : 20;
        long ticks = args.length > 2 ? Long.parseLong(args[2]) : 6_000;

        int fullArc = 0, treaty = 0, war = 0, quiet = 0, oldPlaybook = 0;
        long minBirth = Long.MAX_VALUE, maxBirth = -1;

        for (long seed = from; seed <= to; seed++) {
            Row r = census(seed, ticks);
            switch (r.verdict()) {
                case "OLD_PLAYBOOK" -> oldPlaybook++;
                case "FULL_ARC" -> fullArc++;
                case "TREATY" -> treaty++;
                case "WAR" -> war++;
                default -> quiet++;
            }
            if (r.birth() >= 0) {
                minBirth = Math.min(minBirth, r.birth());
                maxBirth = Math.max(maxBirth, r.birth());
            }
            System.out.println("SEED " + seed
                    + " birth=" + r.birth() + " overflow=" + r.overflow()
                    + " peace=" + r.peace() + " rebirth=" + r.rebirth()
                    + " verdict=" + r.verdict());
        }
        System.out.println("ATLAS seeds=" + from + ".." + to + " ticks=" + ticks
                + " full_arc=" + fullArc + " treaty=" + treaty + " war=" + war
                + " quiet=" + quiet + " old_playbook=" + oldPlaybook
                + " birth_min=" + (maxBirth < 0 ? -1 : minBirth)
                + " birth_max=" + maxBirth);
    }

    /**
     * One universe's census row: the four beats, and the fate they add up to.
     *
     * <p>A {@code -1} beat means "never happened inside the tick budget", which is
     * a different statement from "happened at tick 0" — hence a signed field and
     * not an {@code Optional}, so the row prints the same way it always has.
     */
    record Row(long seed, long birth, long overflow, long peace, long rebirth, String verdict) {}

    /**
     * Run one universe quietly and classify it. This is THE census classifier —
     * {@link CensusBlocks} calls this method rather than re-deriving the fate
     * ladder, so a block comparison can never be an argument about two different
     * definitions of {@code QUIET}. Changing the ladder here changes it for every
     * instrument that quotes a census number, which is the point.
     *
     * <p>Thread-safe: the {@code Simulation} it builds is private to the call and
     * shares nothing with any other, so k of these may run at once on k cores and
     * each seed still yields the byte-identical universe it yields alone.
     */
    static Row census(long seed, long ticks) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
        new Simulation(seed, buf, null).run(ticks);
        String log = buf.toString(StandardCharsets.UTF_8);

        long birth = tickOf(log, "The One is born", 0);
        long overflow = tickOf(log, "SMITH OVERFLOW", 0);
        long peace = tickOf(log, "REBOOT v", 0);
        long rebirth = birth < 0 ? -1
                : tickOf(log, "The One is born", log.indexOf("The One is born") + 1);
        boolean emergency = log.contains("EMERGENCY RELOAD");

        String verdict;
        if (emergency) {
            verdict = "OLD_PLAYBOOK";
        } else if (rebirth >= 0) {
            verdict = "FULL_ARC";
        } else if (peace >= 0) {
            verdict = "TREATY";
        } else if (overflow >= 0) {
            verdict = "WAR";
        } else {
            verdict = "QUIET";
        }
        return new Row(seed, birth, overflow, peace, rebirth, verdict);
    }

    /** Tick of the first framed line containing the needle at/after fromIndex; -1 if absent. */
    private static long tickOf(String log, String needle, int fromIndex) {
        int i = log.indexOf(needle, Math.max(0, fromIndex));
        if (i < 0) {
            return -1;
        }
        int lineStart = log.lastIndexOf('\n', i) + 1;
        if (log.charAt(lineStart) != '[') {
            return -1;
        }
        int close = log.indexOf(']', lineStart);
        try {
            return Long.parseLong(log.substring(lineStart + 1, close));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private SeedAtlas() {}
}
