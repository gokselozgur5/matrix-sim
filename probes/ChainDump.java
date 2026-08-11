import matrix.Simulation;
import matrix.core.Digest;

import java.util.List;

/**
 * Probe: the DIGEST chain of a run as plain lines, for out-of-band diffing.
 *
 * `--selftest` proves two runs agree inside one process; this probe writes
 * the chain down so two BOXES can agree — pipe to a file on each machine and
 * `diff` the files. This is the instrument behind the cross-platform proof
 * in the README (Apple-Silicon macOS ≡ x86-64 Linux at seed 42).
 *
 * Usage: java -cp out:probes/out ChainDump [ticks] [seed]
 */
public final class ChainDump {

    public static void main(String[] args) {
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 2_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        List<Digest> chain = new Simulation(seed, null, null).run(ticks);
        for (Digest d : chain) {
            System.out.println(d.format());
        }
        System.out.println("CHAIN seed=" + seed + " ticks=" + ticks + " links=" + chain.size());
    }

    private ChainDump() {}
}
