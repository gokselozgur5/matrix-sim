package matrix.character;

/**
 * Sheets derive from identity — nobody rolls dice at a christening. This is
 * the {@code matrix.realworld.AcceptanceLoop} grammar, promoted from one
 * stat to a whole sheet: {@code threshold()} proved fate can be a pure
 * function of the NAME ({@code String.hashCode} is fixed by the JLS — same
 * fate on every JVM), and {@code spikes()} proved the mix must be NONLINEAR
 * (the murmur3 finalizer restores avalanche, so nearby inputs land far
 * apart). Both precedents are load-bearing here: name, family, and axis are
 * mixed exactly in that house style, and the rng stream never hears about
 * any of it.
 *
 * <p>Consequences, in order of importance: same name + family is the same
 * sheet on every JVM forever; deriving a sheet consumes zero draws (the
 * D-033 lesson — the rng-drawn variant of the Kid's threshold flipped
 * canonical seed 42 to QUIET); and namesakes share a sheet by construction,
 * which is canon — the census already proved namesakes are real (seed 42:
 * 196 humans, 154 distinct names), and identical identity earning identical
 * fate is the name-hash pattern's whole claim.
 */
public final class Sheets {

    private Sheets() {}

    /**
     * The whole sheet for {@code name} as a member of {@code family}: one
     * derived value 1..10 per axis, in the family's canonical axis order.
     * Pure, total, and JLS-stable — call it a thousand times on a thousand
     * boxes and diff nothing.
     */
    public static Sheet derive(String name, Family family) {
        int[] values = new int[family.axisCount()];
        for (int axis = 0; axis < values.length; axis++) {
            values[axis] = value(name, family, axis);
        }
        return new Sheet(family, name, values);
    }

    /**
     * One axis value in 1..10, murmur-mixed from (name hash, family ordinal,
     * axis index). The salt packs family and axis into disjoint bits (ordinal
     * and index are both tiny), the golden-ratio constant spreads the pack,
     * and the murmur3 finalizer makes the triples behave independently —
     * without it, adjacent axes of one name would move in lockstep, exactly
     * the affine trap {@code spikes()} documents. Wraps by JLS int law.
     */
    private static int value(String name, Family family, int axis) {
        int h = name.hashCode() ^ (int) (((family.ordinal() << 8) | axis) * 0x9E3779B9L);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return 1 + Math.floorMod(h, 10);
    }
}
