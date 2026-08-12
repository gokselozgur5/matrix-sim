import matrix.Simulation;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * Probe: what does the hot path actually allocate?
 *
 * D-027's budget table carries one row that was never measured —
 * "allocation-free hot path at steady state". This meter produces the
 * number: bytes allocated per tick, measured with the JDK's own
 * thread-allocation counter (exact in a single-threaded domain), in
 * three windows — steady state (post-warmup, pre-fork), cascade (the
 * infection storm), and the full arc — plus GC collection counts.
 *
 * <p>One sample of the steady window is not that number, and until #817
 * this probe took one sample. Five back-to-back runs of the
 * single-sample form gave 2,098 to 5,346 bytes/tick on one tree at one
 * seed, because 500 ticks of warmup does not finish C2 and the compiler
 * allocates into the very thread the counter is watching. The ramp is
 * not noise around a true value; it is a monotone descent to one.
 * Repeat the same window on a fresh simulation inside one JVM and the
 * per-tick figure falls through 850, 720, 650 and 460 to a floor near
 * 365 that then holds for as many repeats as you care to run. Every
 * steady number this probe ever published was six to fifteen times the
 * daemon's actual allocation, and the daemon was never asked.
 *
 * <p>So the steady window is measured {@link #STEADY_RUNS} times rather
 * than once — a fresh {@link Simulation} each time, the same seed, the
 * same tick boundaries, so every repeat dissects a byte-identical world
 * and the only thing that varies between them is the JVM. The line
 * keeps its field name and its position and carries the median; {@code
 * steady_runs}, {@code steady_min} and {@code steady_max} are appended
 * beside it. The minimum is the floor a threshold can be set against.
 * The maximum is the cold first repeat — the number this probe used to
 * print — kept on the line on purpose, so the gap between the
 * instrument and the world is visible every run instead of being
 * rediscovered.
 *
 * <p>Lengthening the warmup instead does not work, and the numbers say
 * why: at 2,000 ticks of warmup three runs still spanned 949 to 3,123,
 * because the compiler's progress is counted in invocations, not in
 * ticks. Only repeating the measurement spends invocations without
 * moving the window.
 *
 * <p>Measurement was the whole of it for two erratas: D-027 called this
 * probe the guard on its allocation row while the probe compared nothing
 * to anything and the sweep carried it as a reporting row. The two
 * figures that record names — steady bytes per tick and GC collections
 * per arc — are now compared against the bounds it set, and the run
 * closes on {@code VERDICT ALLOC_IN_BUDGET} or {@code VERDICT
 * ALLOC_OVER_BUDGET over=…}. Nothing else here is judged: the cascade
 * window and the full-run total are reported figures no record has ever
 * bounded.
 *
 * <p>The guard reads the median, not a sample, and that is what makes it
 * a guard. The tail this probe used to have — one run in roughly forty
 * printing {@code steady_bytes_per_tick=67742} at seed 1 where six
 * consecutive runs printed 4,873 to 8,077 — was a single cold sample
 * being handed to a comparison. A median of twenty-four cannot be
 * dragged over a bound by one of them; such a run now shows up as a
 * large {@code steady_max} beside an unmoved headline, which is what it
 * always was.
 *
 * <p>The bounds are D-027's own, unchanged, and the headroom is now
 * measured rather than guessed: the settled steady figure is 365-367
 * bytes/tick at seed 42 and 423-425 at seed 7, against a 32 KB bound —
 * a factor of about ninety, not the factor of four a cold sample
 * suggested. Tightening the bound is a records question and belongs
 * with the errata that set it; what was missing was the floor, and the
 * floor is on the line as {@code steady_min}.
 *
 * <p>The median is reproducible but not byte-exact: across thirteen runs
 * at seed 42 it printed 367 eleven times and 407 and 415 once each — a
 * 13% tail where the single sample had 155%. The floor beneath it is
 * steadier still, 365 to 366 on all thirteen, because compiler
 * allocation only ever adds. That is why {@code probes/bench.sh} still
 * carries this row as exempt from the determinism pass — that pass
 * compares bytes, and 13% is not zero. Quote this instrument with its
 * spread or do not quote it.
 *
 * <p>The cascade and full-arc figures come from one canonical arc run
 * after the repeats, so they inherit a warm compiler; they are still one
 * sample each and still move (602-782 at seed 42 across thirteen runs).
 * {@code gc_collections} counts collections during that arc rather than
 * the JVM's lifetime total, which is what the errata's "per full arc"
 * always meant and the only reading that survives a repeat phase.
 *
 * Usage: java -cp out:probes/out AllocMeter [seed] [scale]
 *        java -cp out:probes/out AllocMeter --selfcheck
 *
 * The optional scale multiplies Bestiary populations exactly as the
 * daemon's --scale does (#136) — the 5,000-entity row gets its own
 * allocation profile on the same instrument. Exactly as: it passes the
 * same gate ({@link matrix.core.Config#scaleRefusal}) and refuses what the
 * daemon refuses, with the daemon's sentence and the daemon's exit code
 * (#826). A budget row is evidence people paste into PR bodies; a row for
 * a city that was never seeded is well-formed, greppable and false.
 * Repeats divide by the scale, because a scaled arc costs what it costs
 * and twenty-four of them is not a bench row. The count is on the line
 * either way, and a scaled run is unjudged for the reason it always was.
 */
public final class AllocMeter {

    /** D-027 errata (2026-08-11): steady <= 32 KB/tick on the reference box. */
    static final long STEADY_BUDGET_BYTES_PER_TICK = 32 * 1024;

    /** D-027 errata (2026-08-11): <= 5 GC collections per full arc. */
    static final long GC_BUDGET_PER_ARC = 5;

    /** Steady-window repeats at scale 1; the denominator on the line (#817). */
    static final int STEADY_RUNS = 24;

    /** Floor on the repeat count, so a scaled run still has a median. */
    static final int STEADY_RUNS_MIN = 3;

    public static void main(String[] args) {
        matrix.Streams.utf8();
        if (args.length > 0 && "--selfcheck".equals(args[0])) {
            selfcheck();
            return;
        }
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
        if (args.length > 1) {
            int scale = Integer.parseInt(args[1]);
            String refusal = matrix.core.Config.scaleRefusal(scale);
            if (refusal != null) {
                System.err.println(refusal);
                System.exit(2);
            }
            matrix.core.Config.setEcoScale(scale);
        }
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long self = Thread.currentThread().getId();

        int runs = Math.max(STEADY_RUNS_MIN, STEADY_RUNS / matrix.core.Config.ecoScale());
        long[] samples = new long[runs];
        for (int r = 0; r < runs; r++) {
            Simulation repeat = new Simulation(seed, null, null);
            repeat.run(500); // warmup: boot allocations settle out of the sample
            long s0 = threads.getThreadAllocatedBytes(self);
            repeat.run(500); // steady window: ticks 500-1000, pre-fork city at routine
            long s1 = threads.getThreadAllocatedBytes(self);
            samples[r] = (s1 - s0) / 500;
        }
        long steadyMin = Arrays.stream(samples).min().orElse(0);
        long steadyMax = Arrays.stream(samples).max().orElse(0);
        Arrays.sort(samples);
        long steady = runs % 2 == 1
                ? samples[runs / 2]
                : (samples[runs / 2 - 1] + samples[runs / 2]) / 2;

        Simulation sim = new Simulation(seed, null, null);

        sim.run(500); // warmup, so the arc's windows open where they always opened

        long a0 = threads.getThreadAllocatedBytes(self);
        // The collectors have already run during the repeats, so the lifetime
        // total is no longer a per-arc count. Take the delta across the arc,
        // which is what the errata's row says and what it always wanted.
        long gc0 = gcCollections();
        sim.run(3_000); // through the steady window and on to the storm
        long a2 = threads.getThreadAllocatedBytes(self);
        sim.run(500); // cascade window: ticks 3500-4000, infection near peak
        long a3 = threads.getThreadAllocatedBytes(self);
        sim.run(2_000); // finish the arc
        long a4 = threads.getThreadAllocatedBytes(self);
        long gcCount = gcCollections() - gc0;
        int ranAt = matrix.core.Config.ecoScale();

        System.out.println("ALLOC seed=" + seed
                + " steady_bytes_per_tick=" + steady
                + " cascade_bytes_per_tick=" + (a3 - a2) / 500
                + " full_run_mb=" + (a4 - a0) / (1024 * 1024)
                + " gc_collections=" + gcCount
                // scaled runs declare themselves; the canonical line keeps its bytes
                + (ranAt == 1 ? ""
                        : " scale=" + ranAt
                                + " entities=" + sim.aliveEntities())
                // appended, never inserted (D-020): the headline keeps its name and
                // its place and gains the denominator and the spread that say how
                // much of it to believe.
                + " steady_runs=" + runs
                + " steady_min=" + steadyMin
                + " steady_max=" + steadyMax);
        System.out.println("ALLOC_NOTE window_steady=500-1000 window_cascade=3500-4000 ticks_total=6000"
                + " steady_stat=median_of_fresh_sims gc_window=arc");

        // A scaled world has no allocation budget in any record: the #136
        // errata cashed the SPEED row at x11 and set no byte bound there. A
        // verdict against the canonical bounds would be well-formed, greppable
        // and about a city these numbers were never measured in — the same
        // failure the scale gate above refuses at the door (#826).
        if (ranAt != 1) {
            System.out.println("VERDICT ALLOC_UNJUDGED scale=" + ranAt
                    + " reason=no_byte_budget_at_this_scale");
            return;
        }

        String over = breaches(steady, gcCount);
        System.out.println("ALLOC_BUDGET steady_bytes_per_tick=" + steady
                + " steady_budget=" + STEADY_BUDGET_BYTES_PER_TICK
                + " gc_collections=" + gcCount
                + " gc_budget=" + GC_BUDGET_PER_ARC);
        System.out.println(over.isEmpty() ? "VERDICT ALLOC_IN_BUDGET"
                : "VERDICT ALLOC_OVER_BUDGET over=" + over);
    }

    /** Collections across every collector, floored: a bean may report -1 for "unknown". */
    private static long gcCollections() {
        long gcCount = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
        }
        return gcCount;
    }

    /**
     * D-027's two allocation bounds, compared. Returns the breached rows in
     * table order, comma-joined, empty when the run is inside both. The
     * comparison is {@code >}, because the record says "<=".
     */
    static String breaches(long steadyBytesPerTick, long gcCollections) {
        String over = "";
        if (steadyBytesPerTick > STEADY_BUDGET_BYTES_PER_TICK) {
            over = "steady";
        }
        if (gcCollections > GC_BUDGET_PER_ARC) {
            over = over.isEmpty() ? "gc" : over + ",gc";
        }
        return over;
    }

    /**
     * Both sides of both bounds, executed with no universe at all.
     *
     * <p>A guard that never fails is indistinguishable from a guard that
     * cannot — and at today's figures (365-425 B/tick against 32 KB, 0
     * collections against 5) no run this box produces reaches the breach
     * branch. That is exactly the shape of unmeasured promise this probe was
     * written to retire, so the breach branch gets a run of its own: the bound
     * itself passes, the bound plus one breaches, and the sweep judges the
     * line. Four cases, no {@code Simulation}, no seed, milliseconds.
     */
    private static void selfcheck() {
        boolean ok = true;
        ok &= verdictCase("at_both_bounds", "-",
                breaches(STEADY_BUDGET_BYTES_PER_TICK, GC_BUDGET_PER_ARC));
        ok &= verdictCase("steady_over_by_one", "steady",
                breaches(STEADY_BUDGET_BYTES_PER_TICK + 1, GC_BUDGET_PER_ARC));
        ok &= verdictCase("gc_over_by_one", "gc",
                breaches(STEADY_BUDGET_BYTES_PER_TICK, GC_BUDGET_PER_ARC + 1));
        ok &= verdictCase("both_over_by_one", "steady,gc",
                breaches(STEADY_BUDGET_BYTES_PER_TICK + 1, GC_BUDGET_PER_ARC + 1));
        System.out.println("SELFCHECK steady_budget=" + STEADY_BUDGET_BYTES_PER_TICK
                + " gc_budget=" + GC_BUDGET_PER_ARC + " cases=4");
        System.out.println(ok ? "SELFCHECK VERDICT GUARD_FIRES" : "SELFCHECK VERDICT GUARD_DEAD");
    }

    /** One case of the comparison, printed as a fact whether it holds or not. */
    private static boolean verdictCase(String name, String want, String got) {
        String shown = got.isEmpty() ? "-" : got;
        boolean ok = want.equals(shown);
        System.out.println("CASE " + name + " want=" + want + " got=" + shown
                + (ok ? " OK" : " MISMATCH"));
        return ok;
    }

    private AllocMeter() {}
}
