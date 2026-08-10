package matrix.core;

/**
 * Fixed-point city coordinate: centimeters as ints (D-004).
 * No floats anywhere in position math — squared distances use long to stay exact.
 */
public record Position(int xCm, int yCm) {

    public int chebyshevCm(Position o) {
        return Math.max(Math.abs(xCm - o.xCm), Math.abs(yCm - o.yCm));
    }

    public long euclidSqCm(Position o) {
        long dx = xCm - o.xCm;
        long dy = yCm - o.yCm;
        return dx * dx + dy * dy;
    }

    public boolean within(Position o, int radiusCm) {
        return euclidSqCm(o) <= (long) radiusCm * radiusCm;
    }
}
