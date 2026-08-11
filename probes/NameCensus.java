import matrix.Simulation;
import matrix.realworld.RealWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * Probe: are grown pilot names unique at a seed?
 *
 * Names come from a finite combinatorial pool, so namesakes are expected at
 * population scale — but every name-keyed feature (--follow, log forensics)
 * must know the truth. First run (seed 42): 196 humans, 154 distinct names.
 *
 * Usage: java -cp out:probes/out NameCensus [seed]
 */
public final class NameCensus {

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
        Simulation sim = new Simulation(seed, null, null);
        RealWorld rw = Probes.realWorld(sim);
        Map<String, Integer> counts = new HashMap<>();
        for (var h : rw.humans()) {
            counts.merge(h.name, 1, Integer::sum);
        }
        counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println("DUP " + e.getKey() + " x" + e.getValue()));
        System.out.println("CENSUS seed=" + seed + " humans=" + rw.humans().size()
                + " distinct=" + counts.size());
    }

    private NameCensus() {}
}
