package matrix.realworld;

import matrix.core.AnomalyLedger;
import matrix.core.Config;
import matrix.entities.Pill;

/**
 * The dream is negotiated, not pushed (D-022). Every accrual window each
 * live link's mind accepts the proposed frame — never completely. The
 * unaccepted remainder is resistance residue, and it flows to the ledger.
 * Kept deliberately minimal per the accepted record: per-pill base rates;
 * awake minds strain the simulation harder than sleeping ones.
 */
public final class AcceptanceLoop {

    private AcceptanceLoop() {}

    public static void accrue(NeuralLink link, AnomalyLedger ledger) {
        if (link.closed() || !link.avatar.alive) {
            return;
        }
        ledger.accrue(link.avatar.pill == Pill.RED
                ? Config.RESIDUE_RED
                : Config.RESIDUE_BLUE);
    }
}
