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
        // HOW MANY TICKS WERE ACTUALLY EXAMINED (#1427, one of #1373's
        // twenty-five). `breaches` counts what went wrong, and a count of
        // wrongs is zero both when the cap held everywhere and when nothing was
        // looked at — so this probe reported the awakening cap intact over a
        // universe it never ticked:
        //
        //     $ java -cp out:probes/out CapSentinel 0 42
        //     SENTINEL seed=42 ticks=0 cap=20
        //     CAP_BREACHES=0                              exit 0
        //
        // No fixture needed it: a tick budget of zero is an ordinary argument.
        long samples = 0;
        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            samples++;
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
        // The population rides the census and the emptiness rides the verdict
        // (#1221): `samples=` moves with the tick budget the caller chose, and a
        // count on an exact-line row is a number people edit until the lane is
        // quiet — while a run that examined no tick must not print the line that
        // means the cap held.
        System.out.println("SENTINEL seed=" + seed + " ticks=" + ticks
                + " cap=" + Config.RED_CAP + " samples=" + samples);
        Probes.leave("CAP_BREACHES=" + breaches + " samples_none=" + (samples == 0 ? 1 : 0),
                breaches == 0 && samples > 0);
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
