package matrix.core;

/**
 * Fixed-point city coordinate: centimeters as ints (D-004) — the VALUE
 * dialect, for coordinates that are stored rather than moved: zone centers,
 * phone-booth exits, rare handoffs. Moving entities carry raw ints and ask
 * {@link Geo} directly (#176).
 *
 * <p>No floats anywhere in position math, and no arithmetic of its own: the
 * fixed-point law lives once, in {@link Geo}, and this record asks it (#824).
 */
public record Position(int xCm, int yCm) {

    /** Squared distance between two stored values — {@link Geo#distSqCm}, in the value dialect. */
    public long euclidSqCm(Position o) {
        return Geo.distSqCm(xCm, yCm, o.xCm, o.yCm);
    }
}
