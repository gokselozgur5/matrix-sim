package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.World;

/**
 * Root of everything that exists inside the simulation. Pure behavior —
 * no presentation members will ever live here (D-019). Ids are assigned
 * by the World in spawn order; all iteration is id-ordered (D-010).
 */
public abstract class MatrixEntity {
    public final int id;
    public Position pos;
    public boolean alive = true;
    /** Perception snapshot (engine-set at tick start): everyone senses the world as it WAS. */
    public int snapXCm;
    public int snapYCm;

    protected MatrixEntity(int id, Position pos) {
        this.id = id;
        this.pos = pos;
        this.snapXCm = pos.xCm();
        this.snapYCm = pos.yCm();
    }

    public abstract void tick(World w);

    /** Live x, centimeters. THE way to read a coordinate — see #176: storage is the entity's business. */
    public final int xCm() {
        return pos.xCm();
    }

    /** Live y, centimeters. */
    public final int yCm() {
        return pos.yCm();
    }

    /** Chebyshev step: each axis moves at most speedCm toward the target, clamped to the city. */
    protected final void stepToward(Position target, int speedCm) {
        int dx = clampStep(target.xCm() - pos.xCm(), speedCm);
        int dy = clampStep(target.yCm() - pos.yCm(), speedCm);
        moveBy(dx, dy);
    }

    protected final void wander(World w, int speedCm) {
        int dx = w.rng().nextInt(-speedCm, speedCm + 1);
        int dy = w.rng().nextInt(-speedCm, speedCm + 1);
        moveBy(dx, dy);
    }

    private void moveBy(int dx, int dy) {
        int x = Math.max(0, Math.min(Config.WORLD_W_CM, pos.xCm() + dx));
        int y = Math.max(0, Math.min(Config.WORLD_H_CM, pos.yCm() + dy));
        if (x == pos.xCm() && y == pos.yCm()) {
            return; // blocked or idle: same place, no fresh Position (AllocMeter)
        }
        pos = new Position(x, y);
    }

    private static int clampStep(int delta, int speedCm) {
        if (delta > speedCm) return speedCm;
        if (delta < -speedCm) return -speedCm;
        return delta;
    }
}
