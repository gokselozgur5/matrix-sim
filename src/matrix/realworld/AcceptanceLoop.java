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
    public static boolean accrue(NeuralLink link, AnomalyLedger ledger) {
        if (link.closed() || !link.avatar.alive) {
            return false;
        }
        if (link.avatar.pill == Pill.RED) {
            ledger.accrue(Config.RESIDUE_RED);
            return false;
        }
        ledger.accrue(Config.RESIDUE_BLUE);
        link.windows++;
        if (spikes(link.human.name, link.windows)) {
            link.personalResidue += Config.KID_SPIKE;
            link.spikes++;
        }
        return link.personalResidue >= threshold(link.human.name);
    }

    /**
     * Fate draws NOTHING — the open point (c) ruling, decided by data: the
     * rng-drawn variant flipped canonical seed 42 to QUIET. The breaking
     * point is a pure function of the NAME (String.hashCode is fixed by
     * the JLS — same fate on every JVM; D-010 tier one, pinned rather than
     * trusted by probes/SealHygiene), and the rng stream never hears
     * about it. Fate was always in the name. RED exclusion doubles as
     * structural armor: The One and every pirate are RED, so the fated
     * and the visitors can never self-substantiate.
     */
    public static long threshold(String name) {
        return Config.KID_BASE + Math.floorMod(name.hashCode(), Config.KID_JITTER);
    }

    /**
     * The spike pattern, derived per (name, window). The mix must be
     * NONLINEAR: an affine map times an odd constant is a bijection mod
     * 2^k, which makes every link spike exactly once per DENOM windows —
     * no tail, no crossings, dead code (measured: 0 events in 33
     * universes). The murmur3 finalizer restores avalanche, so
     * (name, window) pairs behave independently and the Poisson tail the
     * mechanic lives on exists again. Wraps by JLS int law — same fate
     * on every JVM.
     */
    static boolean spikes(String name, long window) {
        int h = name.hashCode() ^ (int) (window * 0x9E3779B9L);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return Math.floorMod(h, Config.KID_SPIKE_DENOM) == 0;
    }
}
