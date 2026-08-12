import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

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
 * After the rows comes the summary block, and it is sized to what a census
 * entry publishes rather than to what fits on one line: BEAT carries each
 * beat's min/median/max with the population it is taken over, ROSTER carries
 * the seed list behind every verdict including the empty ones, METHOD states
 * how a beat that never arrived is counted, and ATLAS carries the fate totals.
 *
 * ATLAS stays LAST. Standing census entries select it with `| tail -1` — three
 * such command lines are in the chapter today, plus every archived thread that
 * quotes one — and a summary that grows must not move the line they read.
 *
 * One command regenerates a census entry after any mechanics change — the
 * summary block, without the per-universe rows it was computed from:
 *   java -cp out:probes/out SeedAtlas 1 100 6000 | grep -v '^SEED '
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
        List<Row> rows = new ArrayList<>();

        for (long seed = from; seed <= to; seed++) {
            Row r = census(seed, ticks);
            rows.add(r);
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
        summarise(rows, ticks);
        System.out.println("ATLAS seeds=" + from + ".." + to + " ticks=" + ticks
                + " full_arc=" + fullArc + " treaty=" + treaty + " war=" + war
                + " quiet=" + quiet + " old_playbook=" + oldPlaybook
                + " birth_min=" + (maxBirth < 0 ? -1 : minBirth)
                + " birth_max=" + maxBirth);
    }

    /** The five fates, in the order the ATLAS line counts them. */
    private static final String[] VERDICTS = {"FULL_ARC", "TREATY", "WAR", "QUIET", "OLD_PLAYBOOK"};

    /**
     * The part of the sweep a census entry publishes.
     *
     * <p>Entry 1 publishes three medians and two seed lists that no invocation of
     * this probe could produce, so two thirds of that entry has no stated method
     * (#845). These lines are the method: every figure the chapter prints, printed
     * by the run that measured it, with the population named on the same line.
     */
    private static void summarise(List<Row> rows, long ticks) {
        beat(rows, ticks, "birth", Row::birth);
        beat(rows, ticks, "overflow", Row::overflow);
        beat(rows, ticks, "peace", Row::peace);
        beat(rows, ticks, "rebirth", Row::rebirth);
        for (String verdict : VERDICTS) {
            StringBuilder seeds = new StringBuilder();
            int n = 0;
            for (Row r : rows) {
                if (r.verdict().equals(verdict)) {
                    n++;
                    seeds.append(seeds.length() == 0 ? "" : ",").append(r.seed());
                }
            }
            System.out.println("ROSTER verdict=" + verdict + " n=" + n
                    + " seeds=" + (n == 0 ? "none" : seeds));
        }
        System.out.println("METHOD min/median/max are over the reached rows alone;"
                + " the never rows are the -1 beats, which did not arrive by ticks=" + ticks
                + " and are censored, not early. median_all is the same statistic over all n"
                + " rows with every censored row ranked above every observed tick, and reports"
                + " a bound rather than a number when the middle of the sample never arrived."
                + " A median without its population is two numbers.");
    }

    /**
     * One beat's distribution over both of its honest populations.
     *
     * <p>{@code min}, {@code median} and {@code max} describe the universes that
     * REACHED the beat; {@code median_all} describes the whole sample. The two
     * differ by more than rounding whenever anything is censored — the seeds that
     * never overflowed are the seeds whose war was latest or never, so ranking
     * them low (which is where the {@code -1} sentinel sorts) moves the median the
     * wrong way. Publishing one number without naming which population it came
     * from is what let "median overflow" mean two things at once.
     */
    private static void beat(List<Row> rows, long ticks, String name, ToLongFunction<Row> pick) {
        long[] reached = rows.stream().mapToLong(pick).filter(t -> t >= 0).sorted().toArray();
        int n = rows.size();
        int k = reached.length;
        System.out.println("BEAT beat=" + name + " n=" + n + " reached=" + k + " never=" + (n - k)
                + " min=" + (k == 0 ? "none" : String.valueOf(reached[0]))
                + " median=" + medianAt(reached, k, ticks)
                + " max=" + (k == 0 ? "none" : String.valueOf(reached[k - 1]))
                + " median_all=" + medianAt(reached, n, ticks));
    }

    /**
     * The median of a sample of {@code m} rows whose reached ticks are {@code sorted}
     * and whose remaining {@code m - sorted.length} rows are right-censored at
     * {@code ticks} — they rank above every observed tick because "had not arrived
     * by the budget" is a statement about lateness.
     *
     * <p>Same convention both times it is called, so {@code median} and
     * {@code median_all} differ only in their population and never in their
     * arithmetic. When a middle rank lands in the censored block the median is not
     * a number this run can name, and the line prints the bound it can: greater
     * than the last observed middle, or greater than the budget when both middles
     * are censored.
     */
    private static String medianAt(long[] sorted, int m, long ticks) {
        if (m == 0) {
            return "none";
        }
        int lo = (m - 1) / 2, hi = m / 2, k = sorted.length;
        if (hi < k) {
            double v = (sorted[lo] + sorted[hi]) / 2.0;
            return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        }
        return ">" + (lo < k ? sorted[lo] : ticks);
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
