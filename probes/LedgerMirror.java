import matrix.Simulation;
import matrix.core.Config;
import matrix.entities.Pill;
import matrix.realworld.NeuralLink;

import java.util.ArrayList;
import java.util.List;

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
 * lines. Déjà-vu spikes had one door — the ops console, never a headless
 * run — until #133 gave them a second, mechanical one: every Unpark
 * accrues DEJA_RESIDUE_SPIKE (the D-022 precedent). The mirror models it
 * from the world's own unpark count, read through the public accessor
 * before and after each tick — a legitimate source accounted, so an
 * unexplained positive residual stays what it always was: a bug.
 *
 * The clean exit is the third accounted source (#863). A walk-out that
 * lands on an accrual window contributes and THEN closes, so a mirror
 * that read openness AFTER the tick was short by exactly that link's
 * rate — a self-inflicted line the javadoc used to apologise for in
 * prose while the verdict counted it as a bug. Openness is now read
 * where the wheel reads it: BEFORE the tick. The node order is what
 * makes that exact rather than approximate. Nothing closes a
 * real-world link ahead of RealWorldSystem's own accrual loop — the
 * two doors that close one clean, self-substantiation and the treaty,
 * both run at or after it — and every avatar death lands in the
 * machine node, which runs before it. So open-at-tick-start is
 * open-at-accrual, and dead-at-tick-end is dead-at-accrual.
 *
 * Verdict: LEDGER_ANOMALIES=0 at seeds 42, 7 and 9 over the full arc
 * (verification round, 2026-08-11; re-verified with the unpark source
 * modeled for the P2 parking film; #863 modelled the clean exit and
 * swept seeds 0..49 clean, 2026-08-13).
 *
 * Usage: java -cp out:probes/out LedgerMirror [ticks] [seed]
 */
public final class LedgerMirror {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        var links = Probes.links(Probes.realWorld(sim));

        long anomalies = 0;
        long prev = world.ledger().balance();
        // The wheel's own view of the register: refilled each tick, never
        // reallocated.
        List<NeuralLink> openAtAccrual = new ArrayList<>();
        for (long t = 0; t < ticks; t++) {
            // Membership AND openness are snapshotted BEFORE the tick, because
            // both are the accrual point's truth: a link born this tick — the
            // One's — joins after the wheel turned, and a link that closes
            // during the tick closes after it has already contributed.
            // Liveness is read AFTER, for the same reason with the sign
            // flipped: a mind killed this tick was killed in the machine node,
            // which runs ahead of the wheel, so it was already dead at accrual.
            openAtAccrual.clear();
            for (NeuralLink l : links) {
                if (!l.closed()) {
                    openAtAccrual.add(l);
                }
            }
            long unparksBefore = world.unparks();
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
            // Link residue accrues only on the wheel's window — nodes run at
            // world.tick()+1, so the window is (t+1) % ACCRUE == 0 as the
            // probe reads t post-tick. Off-window, links owe nothing, and
            // crediting them anyway would hide an off-window accrual bug —
            // the unpark spike (#133) made off-window deltas real and
            // exposed the unconditional credit.
            if ((world.tick() + 1) % Config.ACCRUE_EVERY_TICKS == 0) {
                for (NeuralLink l : openAtAccrual) {
                    if (l.avatar.alive) {
                        mirror += l.avatar.pill == Pill.RED ? Config.RESIDUE_RED : Config.RESIDUE_BLUE;
                    }
                }
            }
            // The second legitimate source (#133): each unpark this tick spiked the ledger.
            mirror += (world.unparks() - unparksBefore) * Config.DEJA_RESIDUE_SPIKE;
            if (delta != mirror) {
                anomalies++;
                System.out.println("ANOMALY t=" + world.tick()
                        + " delta=" + delta + " mirror=" + mirror);
            }
        }
        System.out.println("MIRROR seed=" + seed + " ticks=" + ticks
                + " final_balance=" + world.ledger().balance()
                + " unparks=" + world.unparks());
        System.out.println("LEDGER_ANOMALIES=" + anomalies);
    }

    private LedgerMirror() {}
}
