package matrix.core;

/**
 * Fixed-point distance math over raw int coordinates (D-004): the one home
 * of the arithmetic. Hot paths hold ints and call here directly (#176);
 * {@link Position} is the value dialect and reaches the same code through
 * {@link Position#euclidSqCm} — a call, not a second copy (#824). Every
 * method widens to long BEFORE the subtraction, so no intermediate overflow
 * exists at any input.
 */
public final class Geo {

    public static long distSqCm(int axCm, int ayCm, int bxCm, int byCm) {
        long dx = (long) axCm - bxCm;
        long dy = (long) ayCm - byCm;
        return dx * dx + dy * dy;
    }

    /** Negative radius means nothing is within — never accidental containment. */
    public static boolean within(int axCm, int ayCm, int bxCm, int byCm, int radiusCm) {
        if (radiusCm < 0) return false;
        return withinSq(axCm, ayCm, bxCm, byCm, (long) radiusCm * radiusCm);
    }

    /**
     * The same test with the radius already squared, for the loops that hoist
     * it (#945). {@code within} squares on every call, which is why the ring
     * search, the flock's separation test and the region walk each wrote the
     * arithmetic out instead of calling here: reaching {@code within} from an
     * innermost loop would have put 7.4 million multiplies per arc back where
     * they had been hoisted out of, and a tidy that costs a measured budget is
     * a D-027 regression wearing a refactor's clothes.
     *
     * <p>So the shape the hot callers already needed exists here now, and the
     * law — widen to long BEFORE the subtraction — is implemented once rather
     * than four times. A negative squared radius cannot arise from squaring,
     * so the guard lives on {@code within} where the sign is still knowable.
     */
    public static boolean withinSq(int axCm, int ayCm, int bxCm, int byCm, long radiusSqCm) {
        return distSqCm(axCm, ayCm, bxCm, byCm) <= radiusSqCm;
    }

    public static int chebyshevCm(int axCm, int ayCm, int bxCm, int byCm) {
        long dx = Math.abs((long) axCm - bxCm);
        long dy = Math.abs((long) ayCm - byCm);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(dx, dy));
    }

    private Geo() {}
}
