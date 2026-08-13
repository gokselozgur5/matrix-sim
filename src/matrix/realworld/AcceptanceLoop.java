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
        if (spikes(link.human.birthKey, link.windows)) {
            link.personalResidue += Config.KID_SPIKE;
            link.spikes++;
        }
        return link.personalResidue >= threshold(link.human.birthKey);
    }

    /**
     * The birth event, mixed once into the die that fate rolls against
     * (#373, executing the Architect's #212 law: <em>the die is keyed to
     * the birth event in the record, never the current name — renaming is
     * not rebirth</em>). Five facts make a birth: which universe (seed),
     * when (tick), where (rack unit), which body in the ledger of births
     * (id), and who (name). Only the last of them used to matter, and that
     * is precisely why exactly one name in four hundred could ever cross:
     * the key had 400 possible values, the seed decided only WHETHER an
     * Otto Aydin was grown, never who may walk out.
     *
     * <p>Each field enters through its own splitmix64 finalizer round, so
     * two births that differ in a single bit anywhere — one tick, one rack
     * over, the seed next door — get unrelated fates. Pure, total, and
     * drawing NOTHING: long arithmetic wraps by JLS law and String.hashCode
     * is JLS-specified, so the same birth is the same fate on every JVM,
     * and the rng stream still never hears about any of it.
     */
    public static long birthKey(long seed, long birthTick, String rackUnit, int id, String name) {
        long k = mix64(0x9E3779B97F4A7C15L ^ seed);
        k = mix64(k ^ birthTick);
        k = mix64(k ^ rackUnit.hashCode());
        k = mix64(k ^ ((long) id << 32 | (name.hashCode() & 0xFFFF_FFFFL)));
        return k;
    }

    /**
     * Fate draws NOTHING — the open point (c) ruling, decided by data: the
     * rng-drawn variant flipped canonical seed 42 to QUIET. The breaking
     * point is a pure function of the BIRTH KEY (see {@link #birthKey}),
     * and the rng stream never hears about it. It reads the key's HIGH
     * word; the spike pattern reads the low one, so a mind's bar and its
     * luck are two independent faces of one birth. The key still runs on
     * String.hashCode — for the rack unit and the name — which is fixed by
     * the JLS, D-010 tier one, and pinned rather than trusted by
     * probes/SealHygiene. RED exclusion doubles as structural armor: The
     * One and every pirate are RED, so the fated
     * and the visitors can never self-substantiate.
     */
    public static long threshold(long birthKey) {
        return Config.KID_BASE + Math.floorMod((int) (birthKey >>> 32), Config.KID_JITTER);
    }

    /**
     * The spike pattern, derived per (birth, window). The mix must be
     * NONLINEAR: an affine map times an odd constant is a bijection mod
     * 2^k, which makes every link spike exactly once per DENOM windows —
     * no tail, no crossings, dead code (measured: 0 events in 33
     * universes). The murmur3 finalizer restores avalanche, so
     * (birth, window) pairs behave independently and the Poisson tail the
     * mechanic lives on exists again. That lesson outlived the key it was
     * learned on: only the left operand changed, from a name's hash to the
     * birth's low word, and the finalizer that earns the tail is untouched.
     * Wraps by JLS int law — same fate on every JVM.
     */
    static boolean spikes(long birthKey, long window) {
        int h = (int) birthKey ^ (int) (window * 0x9E3779B9L);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return Math.floorMod(h, Config.KID_SPIKE_DENOM) == 0;
    }

    /** The 64-bit sibling of the finalizer above — same lesson, one word wider. */
    private static long mix64(long z) {
        z ^= z >>> 33;
        z *= 0xFF51AFD7ED558CCDL;
        z ^= z >>> 33;
        z *= 0xC4CEB9FE1A85EC53L;
        z ^= z >>> 33;
        return z;
    }
}
