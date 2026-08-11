import matrix.Simulation;
import matrix.entities.TheOne;
import matrix.realworld.NeuralLink;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Probe: the One's lifecycle contract, as greppable evidence lines.
 *
 * For every fated link (avatar instanceof TheOne) the probe records
 * birth (first seen), death (alive flips false), and closure (link
 * closes). The finale's contract after the ghost-HARDLINE fix is
 * exact: death and closure happen on the SAME tick — Neo's body does
 * not survive Machine City, and the D-013 bridge closes his jack the
 * moment it observes. Any gap between the two ticks is a regression.
 *
 * Usage: java -cp out:probes/out OneTrace [ticks] [seed]
 */
public final class OneTrace {

    private static final class Life {
        int index;
        long born = -1, died = -1, closed = -1;
    }

    public static void main(String[] args) throws Exception {
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var links = Probes.links(Probes.realWorld(sim));
        Map<NeuralLink, Life> lives = new IdentityHashMap<>();
        int births = 0;

        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            long now = t + 1;
            for (NeuralLink l : links) {
                if (!(l.avatar instanceof TheOne)) {
                    continue;
                }
                Life life = lives.get(l);
                if (life == null) {
                    life = new Life();
                    life.index = births++;
                    life.born = now;
                    lives.put(l, life);
                    System.out.println("BORN one#" + life.index + " t=" + now
                            + " pilot=" + l.human.name);
                }
                if (life.died < 0 && !l.avatar.alive) {
                    life.died = now;
                    System.out.println("DIED one#" + life.index + " t=" + now);
                }
                if (life.closed < 0 && l.closed()) {
                    life.closed = now;
                    System.out.println("CLOSED one#" + life.index + " t=" + now);
                }
            }
        }
        boolean ok = true;
        for (Life life : lives.values()) {
            if (life.died >= 0 && life.died != life.closed) {
                ok = false;
                System.out.println("GAP one#" + life.index
                        + " died=" + life.died + " closed=" + life.closed);
            }
        }
        System.out.println("ONETRACE seed=" + seed + " ticks=" + ticks + " births=" + births);
        System.out.println(ok ? "VERDICT CONTRACT_HELD" : "VERDICT CONTRACT_BROKEN");
    }

    private OneTrace() {}
}
