package matrix.character;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Sheets derive from identity — nobody rolls dice at a christening. This is
 * the {@code matrix.realworld.AcceptanceLoop} grammar, promoted from one
 * stat to a whole sheet: {@code threshold()} proved fate can be a pure
 * function of the name, and {@code spikes()} proved the mix must be
 * NONLINEAR (an affine map times an odd constant is a bijection mod 2^k,
 * which once made every link spike exactly once per window batch — no tail,
 * no crossings, dead code; the murmur3 finalizer restores avalanche). Both
 * precedents are load-bearing here.
 *
 * <h2>The mixer boundary</h2>
 *
 * The birth-seed ruling (#212, 2026-08-11) carries a hygiene clause — <i>the
 * die derives from our own digest mixing, never from anything JVM-shaped</i>
 * — and it collides with a shipped invariant: the repo's first stat mixes
 * with {@code String.hashCode}, which a migration must reproduce byte for
 * byte (a migration that changes a number is not a migration). Both cannot
 * be true of one call site, so the boundary is drawn and made greppable:
 *
 * <ul>
 *   <li>The <b>migrated</b> site — {@code AcceptanceLoop.threshold} and
 *       {@code spikes}, the Kid's band — keeps its exact existing mix under
 *       the two-die law. Its numbers are canon and are not this package's
 *       to move.</li>
 *   <li>Every <b>new</b> axis derives here, through our own mixer: FNV-1a
 *       over the UTF-8 bytes of the name at birth, salted per (family, axis)
 *       and murmur3-finalized. No platform string hashing exists anywhere
 *       in {@code matrix.character} — not in the derivation, not in a
 *       hand-written {@code equals} either (see {@link Sheet}).</li>
 * </ul>
 *
 * FNV-1a over bytes is strictly stronger than the JLS guarantee it
 * replaces: {@code String.hashCode} is stable because a specification says
 * so, while this mix is stable because it is arithmetic over bytes we
 * define — reproducible outside the JVM entirely, which is the only real
 * proof that a number is ours.
 *
 * <h2>What is mixed</h2>
 *
 * The <b>name at birth</b>, never the current name: renaming is not
 * rebirth, and the favor economy's identity papers cannot launder fate.
 * This package states that invariant and takes the birth name as its
 * argument; the substrate that makes birth-invariance structural (a birth
 * record to key to) is #342's, and until it lands the caller owes the
 * discipline. Derivation inputs are birth-invariants only — no world state,
 * no clock, no draw. Reads are policed by declaration, draws by DrawMeter.
 *
 * <p>Consequences, in order of importance: same name + family is the same
 * sheet on every JVM and in every language forever; deriving a sheet
 * consumes zero draws (the D-033 lesson — the rng-drawn variant of the
 * Kid's threshold flipped canonical seed 42 to QUIET); and namesakes share
 * a sheet by construction, which is canon — the census already proved
 * namesakes are real (seed 42: 196 humans, 154 distinct names), and
 * identical identity earning identical fate is the name-hash pattern's
 * whole claim.
 */
public final class Sheets {

    /** FNV-1a 32-bit offset basis — the fold's starting state. */
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;

    /** FNV-1a 32-bit prime — one multiply per byte. */
    private static final int FNV_PRIME = 0x01000193;

    /** 2^32 / phi: the golden-ratio constant that spreads the (family, axis) pack. */
    private static final int GOLDEN = 0x9E3779B9;

    private Sheets() {}

    /**
     * The whole sheet for {@code nameAtBirth} as a member of {@code family}:
     * one derived value 1..10 per axis, in the family's canonical axis
     * order. Pure, total, and byte-defined — call it a thousand times on a
     * thousand boxes and diff nothing.
     */
    public static Sheet derive(String nameAtBirth, Family family) {
        List<Integer> values = new ArrayList<>(family.axisCount());
        for (int axis = 0; axis < family.axisCount(); axis++) {
            values.add(1 + Math.floorMod(mix(nameAtBirth, family, axis), 10));
        }
        return new Sheet(family, nameAtBirth, values);
    }

    /**
     * The 32-bit word one axis bands from: the name's FNV-1a fold XOR a
     * golden-ratio-spread (family ordinal, axis index) pack, murmur3
     * finalized. The salt packs family and axis into disjoint bits (both are
     * tiny), the golden-ratio multiply spreads the pack across the word, and
     * the finalizer makes the triples behave independently — without it,
     * adjacent axes of one name would move in lockstep, exactly the affine
     * trap {@code spikes()} documents. Wraps by JLS int law.
     *
     * <p>Package-private so the bench can measure the mixer itself
     * (avalanche, cross-axis correlation) rather than only its banded
     * output: a mixer nobody can measure is a mixer nobody has checked.
     */
    static int mix(String nameAtBirth, Family family, int axis) {
        return avalanche(fnv1a(nameAtBirth) ^ (((family.ordinal() << 8) | axis) * GOLDEN));
    }

    /**
     * FNV-1a over the UTF-8 bytes of a name. Bytes, not chars, and not the
     * platform charset: the encoding is part of the number, so a name with
     * an umlaut in it derives the same sheet whatever the box's locale says.
     */
    static int fnv1a(String s) {
        int h = FNV_OFFSET_BASIS;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xFF);
            h *= FNV_PRIME;
        }
        return h;
    }

    /**
     * The murmur3 finalizer, verbatim from the house precedent — the
     * avalanche stage D-033 paid for in the spike pattern's dead tail.
     */
    static int avalanche(int h) {
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }
}
