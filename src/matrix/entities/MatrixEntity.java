package matrix.entities;

import matrix.core.Config;
import matrix.core.Geo;
import matrix.core.Position;
import matrix.core.World;

/**
 * Root of everything that exists inside the simulation. Pure behavior —
 * no presentation members will ever live here (D-019). Ids are assigned
 * by the World in spawn order; all iteration is id-ordered (D-010).
 * Coordinates are raw ints (#176): moving is a field write, not a mint;
 * Position remains the VALUE dialect for zone centers, exits, and rare
 * handoffs — ask pos() and pay the allocation knowingly.
 */
public abstract class MatrixEntity {
    public final int id;
    private int x;
    private int y;
    public boolean alive = true;
    /** Perception snapshot (engine-set at tick start): everyone senses the world as it WAS. */
    public int snapXCm;
    public int snapYCm;

    protected MatrixEntity(int id, Position pos) {
        this(id, pos.xCm(), pos.yCm());
    }

    /** Raw-coordinate spawn: the wrap path (SmithCopy) copies values, never objects. */
    protected MatrixEntity(int id, int xCm, int yCm) {
        this.id = id;
        this.x = xCm;
        this.y = yCm;
        this.snapXCm = xCm;
        this.snapYCm = yCm;
    }

    public abstract void tick(World w);

    /** Live x, centimeters. THE way to read a coordinate — see #176: storage is the entity's business. */
    public final int xCm() {
        return x;
    }

    /** Live y, centimeters. */
    public final int yCm() {
        return y;
    }

    /** The coordinates AS a value — allocates. For rare handoffs (fork, spawn), never per-step. */
    public final Position pos() {
        return new Position(x, y);
    }

    /** Chebyshev step toward a stored value target (zone centers, phone booths). */
    protected final void stepToward(Position target, int speedCm) {
        moveBy(clampStep(target.xCm() - x, speedCm), clampStep(target.yCm() - y, speedCm));
    }

    /** Chebyshev step toward another entity — reads its ints, mints nothing (#176). */
    protected final void stepToward(MatrixEntity target, int speedCm) {
        moveBy(clampStep(target.x - x, speedCm), clampStep(target.y - y, speedCm));
    }

    /** Contact/flee test against another entity, allocation-free. */
    protected final boolean within(MatrixEntity other, int radiusCm) {
        return Geo.within(x, y, other.x, other.y, radiusCm);
    }

    protected final void wander(World w, int speedCm) {
        int dx = w.rng().nextInt(-speedCm, speedCm + 1);
        int dy = w.rng().nextInt(-speedCm, speedCm + 1);
        moveBy(dx, dy);
    }

    /** Clamped relative move — the gaits' one door into motion (steppedBy semantics, minus the mint). */
    public final void moveBy(int dx, int dy) {
        int nx = Math.max(0, Math.min(Config.WORLD_W_CM, x + dx));
        int ny = Math.max(0, Math.min(Config.WORLD_H_CM, y + dy));
        if (nx == x && ny == y) {
            return; // blocked or idle: same place, nothing to write
        }
        x = nx;
        y = ny;
    }

    /** Absolute placement, unclamped — Drift's wrap-around and the exile's teleport set exact values. */
    public final void placeAt(int xCm, int yCm) {
        this.x = xCm;
        this.y = yCm;
    }

    private static int clampStep(int delta, int speedCm) {
        if (delta > speedCm) return speedCm;
        if (delta < -speedCm) return -speedCm;
        return delta;
    }
}
