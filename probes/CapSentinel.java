import matrix.Simulation;
import matrix.core.Config;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.entities.SmithCopy;
import matrix.entities.TheOne;

/**
 * Probe: the awakening cap counts every awake mind — including latent ones.
 *
 * The v3 fix round's third invariant, kept per Ag9: present reds plus reds
 * wrapped inside SmithCopies never exceed RED_CAP, with one blessed
 * exception — the fated One, whose ledger-birth is exempt from the
 * awakening gate (a debt is not an awakening). The bug this guards
 * against: a treaty mass-restore snapping red=37 into a city capped at
 * 20, because wrapped minds were invisible to the gate.
 *
 * Verification-round result to reproduce: 0 breaches at seeds 42 and 7.
 *
 * Usage: java -cp out:probes/out CapSentinel [ticks] [seed]
 */
public final class CapSentinel {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        var links = Probes.links(Probes.realWorld(sim));

        long breaches = 0;
        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            int present = 0, wrapped = 0, fated = 0, visitors = 0;
            for (var e : world.entities()) {
                if (!e.alive) {
                    continue;
                }
                if (e instanceof TheOne) {
                    fated++;
                } else if (e instanceof Avatar a && a.pill == Pill.RED) {
                    // The visitor rule (D-032 era): the cap is the RESIDENT
                    // awakening economy. A pirate's link lives rig-side, so a
                    // red with no RealWorld link is a visitor — outside the
                    // invariant, exactly as the Matrix (A1-blind to links)
                    // pays for their presence by under-awakening instead.
                    if (hasResidentLink(links, a)) {
                        present++;
                    } else {
                        visitors++;
                    }
                } else if (e instanceof SmithCopy c
                        && c.original instanceof Avatar w && w.pill == Pill.RED
                        && !(c.original instanceof TheOne)) {
                    wrapped++;
                }
            }
            if (present + wrapped > Config.RED_CAP) {
                breaches++;
                System.out.println("BREACH t=" + world.tick() + " present=" + present
                        + " wrapped=" + wrapped + " visitors=" + visitors
                        + " fated=" + fated + " cap=" + Config.RED_CAP);
            }
        }
        System.out.println("SENTINEL seed=" + seed + " ticks=" + ticks + " cap=" + Config.RED_CAP);
        System.out.println("CAP_BREACHES=" + breaches);
    }

    private static boolean hasResidentLink(
            java.util.List<matrix.realworld.NeuralLink> links, Avatar a) {
        for (var l : links) {
            if (l.avatar == a) {
                return true;
            }
        }
        return false;
    }

    private CapSentinel() {}
}
