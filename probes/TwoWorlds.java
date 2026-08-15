import matrix.Simulation;
import matrix.core.Digest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Probe: are two universes in one JVM actually two universes?
 *
 * <p>The repository's determinism evidence is all about one world — {@code
 * --selftest} runs the daemon twice in one process, {@code ChainDump} lets two
 * BOXES compare. Neither asks whether two worlds ALIVE AT THE SAME TIME in one
 * process are independent, and the answer was no: {@code FlockMovement} and
 * {@code SwarmMovement} are singletons whose neighbour scratch buffer was an
 * INSTANCE field, so every {@code Simulation} in the JVM shared one list
 * (#1135). One world's iteration was cleared by another world's tick.
 *
 * <p>That surfaced as a {@code ConcurrentModificationException} out of
 * {@code FlockMovement.move} when {@code LedgerMirror --sweep} ran a seed range
 * in parallel — and the exception is the LUCKY outcome. The unlucky one is a
 * refill landing between the fill and the read: a bird handed a neighbour set
 * from another universe, no exception, a different world, a different seal.
 *
 * <p>So the confirmation cannot be "it did not throw". It is the only statement
 * that would have caught the silent form: <b>run each seed alone, run them all
 * again concurrently, and demand the chains be identical link for link.</b> A
 * shared buffer that merely got lucky on the CME still moves a chain.
 *
 * <pre>
 * java -cp out:probes/out TwoWorlds [ticks] [seed...]   default: 2000, seeds 42 7 4 13
 * </pre>
 *
 * <p>Judged, so it owes an exit code (#1093): 0 when every parallel chain equals
 * its own serial chain, 1 on the first that does not.
 */
public final class TwoWorlds {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 2_000;
        List<Long> seeds = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            seeds.add(Long.parseLong(args[i]));
        }
        if (seeds.isEmpty()) {
            seeds = List.of(42L, 7L, 4L, 13L);
        }

        // Serial first, one at a time, nothing else alive: this is the answer
        // every other instrument in the tree already agrees with, and it is the
        // baseline precisely because it is the uncontested one.
        Map<Long, String> alone = new LinkedHashMap<>();
        for (long seed : seeds) {
            alone.put(seed, chainOf(seed, ticks));
        }

        // Then all of them at once. More threads than cores is deliberate: the
        // failure is an interleaving, and oversubscription is how you buy them.
        ExecutorService pool = Executors.newFixedThreadPool(seeds.size());
        List<Future<String>> together = new ArrayList<>();
        for (long seed : seeds) {
            final long s = seed;
            together.add(pool.submit(() -> chainOf(s, ticks)));
        }
        pool.shutdown();

        int diverged = 0;
        for (int i = 0; i < seeds.size(); i++) {
            long seed = seeds.get(i);
            String parallel;
            try {
                parallel = together.get(i).get();
            } catch (Exception e) {
                // A throw IS a divergence, and naming it beats a stack trace:
                // the CME this probe exists for arrives exactly here.
                diverged++;
                System.out.println("TWOWORLDS seed=" + seed + " THREW " + e.getCause());
                continue;
            }
            String serial = alone.get(seed);
            boolean equal = serial.equals(parallel);
            if (!equal) {
                diverged++;
            }
            System.out.println("TWOWORLDS seed=" + seed
                    + " links=" + parallel.split("\n").length
                    + " " + (equal ? "EQUAL" : "DIVERGED at " + firstUnequalLink(serial, parallel)));
        }
        Probes.leave("VERDICT " + (diverged == 0 ? "WORLDS_INDEPENDENT" : "WORLDS_SHARE_STATE")
                + " ticks=" + ticks + " worlds=" + seeds.size() + " diverged=" + diverged,
                diverged == 0);
    }

    /** One universe's whole chain as text — the finest-grained thing two runs can differ in. */
    private static String chainOf(long seed, long ticks) {
        Simulation sim = new Simulation(seed, null, null);
        StringBuilder sb = new StringBuilder();
        for (Digest d : sim.run(ticks)) {
            sb.append(d.format()).append('\n');
        }
        return sb.toString();
    }

    /** Which link first disagrees — the only one that carries information; the rest are its echo. */
    private static String firstUnequalLink(String serial, String parallel) {
        String[] a = serial.split("\n");
        String[] b = parallel.split("\n");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (!a[i].equals(b[i])) {
                return "link " + (i + 1) + ": " + a[i] + " vs " + b[i];
            }
        }
        return "length " + a.length + " vs " + b.length;
    }

    private TwoWorlds() {}
}
