import matrix.Simulation;
import matrix.core.Config;
import matrix.entities.Pill;
import matrix.realworld.NeuralLink;

/**
 * Probe: does every AnomalyLedger delta equal the residue mirror?
 *
 * The v3 verification skeptic's strongest instrument, kept per Ag9: an
 * independently computed mirror — the sum of per-pill residue over links
 * that are open with a living avatar — must account for every positive
 * ledger movement. A closed or dead link accruing ANYWHERE breaks the
 * equality; that is exactly the ghost-HARDLINE class of bug, and with
 * this probe on the bench it can never return unnoticed.
 *
 * Negative movements are resets (treaty, reload) and print as RESET
 * lines; déjà-vu spikes exist only on the ops console, never in a
 * headless replay, so an unexplained positive residual is always a bug.
 *
 * Verdict: LEDGER_ANOMALIES=0 at seeds 42 and 7 over the full arc
 * (verification round, 2026-08-11).
 *
 * Usage: java -cp out:probes/out LedgerMirror [ticks] [seed]
 */
public final class LedgerMirror {

    public static void main(String[] args) throws Exception {
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        var links = Probes.links(Probes.realWorld(sim));

        long anomalies = 0;
        long prev = world.ledger().balance();
        for (long t = 0; t < ticks; t++) {
            // Membership is snapshotted BEFORE the tick (a link born this tick —
            // the One's — joins after accrual ran), state is read AFTER it (a
            // mind killed this tick was already dead at accrual). One known
            // explainable line remains: a walker whose clean exit lands exactly
            // on an accrual window closes after contributing.
            NeuralLink[] members = links.toArray(new NeuralLink[0]);
            sim.tickOnce();
            long now = world.ledger().balance();
            long delta = now - prev;
            prev = now;
            if (delta < 0) {
                System.out.println("RESET t=" + world.tick() + " to=" + now);
                continue;
            }
            if (delta == 0) {
                continue;
            }
            long mirror = 0;
            for (NeuralLink l : members) {
                if (!l.closed() && l.avatar.alive) {
                    mirror += l.avatar.pill == Pill.RED ? Config.RESIDUE_RED : Config.RESIDUE_BLUE;
                }
            }
            if (delta != mirror) {
                anomalies++;
                System.out.println("ANOMALY t=" + world.tick()
                        + " delta=" + delta + " mirror=" + mirror);
            }
        }
        System.out.println("MIRROR seed=" + seed + " ticks=" + ticks
                + " final_balance=" + world.ledger().balance());
        System.out.println("LEDGER_ANOMALIES=" + anomalies);
    }

    private LedgerMirror() {}
}
