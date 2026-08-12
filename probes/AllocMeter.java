import matrix.Simulation;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

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
 * The optimization rule is strict (measured optimization is
 * contractual, the digest is the referee), so this probe is the
 * mandatory FIRST HALF of any allocation work: the fix PR must quote
 * this baseline and beat it with byte-identical digests.
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
 * <p>The bounds are D-027's own, unchanged, and the headroom is large on
 * purpose — the loudest steady figure this instrument has produced is
 * ~8.5 KB/tick against a 32 KB bound. Tightening it is not available
 * until the instrument's own noise floor is measured (#916): the steady
 * window moves by a third between two runs of the same tree, and a
 * threshold set inside that spread fires on weather.
 *
 * <p>Even at 4x margin the steady figure has a tail, and it has been
 * seen to clear the bound on a tree with no allocation change in it:
 * one run in roughly forty printed {@code steady_bytes_per_tick=67742}
 * at seed 1, where six consecutive runs of the same command printed
 * 4,873 to 8,077. Read a red row the way {@code tools/release.sh} says
 * to read a red bench row — re-run it before believing it — and read
 * the cascade figure beside it: that window sits 3,000 ticks past the
 * warmup and does not move (657-2,010 across every run taken here,
 * including 715 inside the 67,742 outlier). A real allocation
 * regression moves both windows; a JIT that lost a race moves only the
 * first.
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
 */
public final class AllocMeter {

    /** D-027 errata (2026-08-11): steady <= 32 KB/tick on the reference box. */
    static final long STEADY_BUDGET_BYTES_PER_TICK = 32 * 1024;

    /** D-027 errata (2026-08-11): <= 5 GC collections per full arc. */
    static final long GC_BUDGET_PER_ARC = 5;

    public static void main(String[] args) {
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

        Simulation sim = new Simulation(seed, null, null);

        sim.run(500); // warmup: JIT + boot allocations settle out of the sample

        long a0 = threads.getThreadAllocatedBytes(self);
        sim.run(500); // steady window: ticks 500-1000, pre-fork city at routine
        long a1 = threads.getThreadAllocatedBytes(self);
        sim.run(2_500); // advance to the storm
        long a2 = threads.getThreadAllocatedBytes(self);
        sim.run(500); // cascade window: ticks 3500-4000, infection near peak
        long a3 = threads.getThreadAllocatedBytes(self);
        sim.run(2_000); // finish the arc
        long a4 = threads.getThreadAllocatedBytes(self);

        long gcCount = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
        }

        long steady = (a1 - a0) / 500;
        int ranAt = matrix.core.Config.ecoScale();

        System.out.println("ALLOC seed=" + seed
                + " steady_bytes_per_tick=" + steady
                + " cascade_bytes_per_tick=" + (a3 - a2) / 500
                + " full_run_mb=" + (a4 - a0) / (1024 * 1024)
                + " gc_collections=" + gcCount
                // scaled runs declare themselves; the canonical line keeps its bytes
                + (ranAt == 1 ? ""
                        : " scale=" + ranAt
                                + " entities=" + sim.aliveEntities()));
        System.out.println("ALLOC_NOTE window_steady=500-1000 window_cascade=3500-4000 ticks_total=6000");

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
     * cannot — and at today's figures (~2-8.5 KB/tick against 32 KB, 0-1
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
