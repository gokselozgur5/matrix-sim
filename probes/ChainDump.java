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
 * Usage: java -cp out:probes/out ChainDump [ticks] [seed] [scale]
 *
 * <p>The optional scale multiplies Bestiary populations exactly as the
 * daemon's --scale does (#136), through the same gate
 * ({@link matrix.core.Config#scaleRefusal}) with the daemon's sentence and
 * the daemon's exit code (#826), and written through the dial's only door
 * ({@link matrix.core.Config#setEcoScale}). #882 called this "the third door
 * — the one nobody has written yet"; this is that door, and it was born
 * knowing the law rather than taught it after a bug. The gate is asked
 * first so the refusal is a sentence and an exit code, not a stack trace.
 *
 * <p>Why a probe that dumps a chain needed the dial at all (#518): the
 * cross-process determinism proof is this probe run twice and diffed, and
 * until now it could only be run in the canonical world. So the scaled half
 * of the rung's determinism evidence had no instrument — `--selftest` proves
 * two runs inside ONE process, where a stale static or a cached derivation
 * of the dial is shared by both runs and cancels out. Two processes do not
 * share it. That is the difference between the two proofs, and only one of
 * them could be commanded at scale.
 */
public final class ChainDump {

    public static void main(String[] args) {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 2_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
        if (args.length > 2) {
            int scale = Integer.parseInt(args[2]);
            String refusal = matrix.core.Config.scaleRefusal(scale);
            if (refusal != null) {
                System.err.println(refusal);
                System.exit(Probes.Outcome.REFUSED.code());
            }
            matrix.core.Config.setEcoScale(scale);
        }

        Simulation sim = new Simulation(seed, null, null);
        List<Digest> chain = sim.run(ticks);
        for (Digest d : chain) {
            System.out.println(d.format());
        }
        // The trailer declares the world; at scale 1 it appends nothing, so
        // the chains this probe has already published stay byte-identical
        // and the cross-box diff keeps its meaning.
        System.out.println("CHAIN seed=" + seed + " ticks=" + ticks + " links=" + chain.size()
                + matrix.core.Config.scaleTag(sim.aliveEntities()));
    }

    private ChainDump() {}
}
