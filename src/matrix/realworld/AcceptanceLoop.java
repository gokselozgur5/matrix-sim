package matrix.realworld;

import matrix.core.AnomalyLedger;
import matrix.core.Config;
import matrix.core.Rng;
import matrix.entities.Pill;

/**
 * The dream is negotiated, not pushed (D-022). Every accrual window each
 * live link's mind accepts the proposed frame — never completely. The
 * unaccepted remainder is resistance residue, and it flows to the ledger.
 * Kept deliberately minimal per the accepted record: per-pill base rates;
 * awake minds strain the simulation harder than sleeping ones.
 *
 * Second theorem (D-033): base residue flows to the global ledger
 * untouched; the personal account is spike-only — per window a BLUE link
 * may take KID_SPIKE on its own tab, one chance draw per live blue link,
 * in link registration order. The One is the ledger's global overflow;
 * the Kid is one link's local overflow — same bookkeeping, two scales.
 * RED is excluded by the addendum's invariant: the hardline is their
 * exit, and their base rate would make crossing deterministic.
 */
public final class AcceptanceLoop {

    private AcceptanceLoop() {}

    /**
     * Returns true while the personal account stands at or past the mind's
     * threshold — the walk-out is executable. The caller owns the door, and
     * defers it while the dream is not the mind's own (a wrapped original
     * keeps disbelieving underneath; the account holds until restore).
     */
    public static boolean accrue(NeuralLink link, AnomalyLedger ledger, Rng rng) {
        if (link.closed() || !link.avatar.alive) {
            return false;
        }
        if (link.avatar.pill == Pill.RED) {
            ledger.accrue(Config.RESIDUE_RED);
            return false;
        }
        ledger.accrue(Config.RESIDUE_BLUE);
        link.windows++;
        if (rng.chance(Config.KID_SPIKE_CHANCE)) {
            link.personalResidue += Config.KID_SPIKE;
            link.spikes++;
        }
        return link.personalResidue >= link.human.threshold;
    }
}
