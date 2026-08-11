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
        long from = args.length > 0 ? Long.parseLong(args[0]) : 1;
        long to = args.length > 1 ? Long.parseLong(args[1]) : 20;
        long ticks = args.length > 2 ? Long.parseLong(args[2]) : 6_000;

        int fullArc = 0, treaty = 0, war = 0, quiet = 0, oldPlaybook = 0;
        long minBirth = Long.MAX_VALUE, maxBirth = -1;

        for (long seed = from; seed <= to; seed++) {
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
                oldPlaybook++;
            } else if (rebirth >= 0) {
                verdict = "FULL_ARC";
                fullArc++;
            } else if (peace >= 0) {
                verdict = "TREATY";
                treaty++;
            } else if (overflow >= 0) {
                verdict = "WAR";
                war++;
            } else {
                verdict = "QUIET";
                quiet++;
            }
            if (birth >= 0) {
                minBirth = Math.min(minBirth, birth);
                maxBirth = Math.max(maxBirth, birth);
            }
            System.out.println("SEED " + seed
                    + " birth=" + birth + " overflow=" + overflow
                    + " peace=" + peace + " rebirth=" + rebirth
                    + " verdict=" + verdict);
        }
        System.out.println("ATLAS seeds=" + from + ".." + to + " ticks=" + ticks
                + " full_arc=" + fullArc + " treaty=" + treaty + " war=" + war
                + " quiet=" + quiet + " old_playbook=" + oldPlaybook
                + " birth_min=" + (maxBirth < 0 ? -1 : minBirth)
                + " birth_max=" + maxBirth);
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
