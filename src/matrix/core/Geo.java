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
        return distSqCm(axCm, ayCm, bxCm, byCm) <= (long) radiusCm * radiusCm;
    }

    public static int chebyshevCm(int axCm, int ayCm, int bxCm, int byCm) {
        long dx = Math.abs((long) axCm - bxCm);
        long dy = Math.abs((long) ayCm - byCm);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(dx, dy));
    }

    private Geo() {}
}
