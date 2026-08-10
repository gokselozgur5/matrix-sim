package matrix.core;

/**
 * Fixed-point city coordinate: centimeters as ints (D-004).
 * No floats anywhere in position math; all arithmetic widens to long
 * BEFORE subtracting, so no intermediate overflow exists at any input.
 */
public record Position(int xCm, int yCm) {

    public int chebyshevCm(Position o) {
        long dx = Math.abs((long) xCm - o.xCm);
        long dy = Math.abs((long) yCm - o.yCm);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(dx, dy));
    }

    public long euclidSqCm(Position o) {
        long dx = (long) xCm - o.xCm;
        long dy = (long) yCm - o.yCm;
        return dx * dx + dy * dy;
    }

    /** Negative radius means nothing is within — never accidental containment. */
    public boolean within(Position o, int radiusCm) {
        if (radiusCm < 0) return false;
        return euclidSqCm(o) <= (long) radiusCm * radiusCm;
    }
}
