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
 * Usage: java -cp out:probes/out AllocMeter [seed]
 */
public final class AllocMeter {

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
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

        System.out.println("ALLOC seed=" + seed
                + " steady_bytes_per_tick=" + (a1 - a0) / 500
                + " cascade_bytes_per_tick=" + (a3 - a2) / 500
                + " full_run_mb=" + (a4 - a0) / (1024 * 1024)
                + " gc_collections=" + gcCount);
        System.out.println("ALLOC_NOTE window_steady=500-1000 window_cascade=3500-4000 ticks_total=6000");
    }

    private AllocMeter() {}
}
