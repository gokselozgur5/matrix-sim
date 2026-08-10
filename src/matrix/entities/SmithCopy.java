package matrix.entities;

import matrix.core.Config;
import matrix.core.World;

/**
 * Decorator (D-001): the hijacked victim's original object is kept inside,
 * untouched. Delete the Smith and the original snaps back — the mass
 * restore of the finale is type-guaranteed, not remembered.
 */
public final class SmithCopy extends MatrixEntity implements SelfReplicating {
    public final MatrixEntity original;

    public SmithCopy(int id, MatrixEntity original) {
        super(id, original.pos);
        this.original = original;
    }

    @Override
    public void tick(World w) {
        MatrixEntity victim = w.nearestNonReplicating(pos, id);
        if (victim == null) {
            return;
        }
        if (w.rng().chance(0.7)) {
            stepToward(victim.pos, Config.COPY_SPEED_CM);
        }
        if (pos.within(victim.pos, Config.CONTACT_RADIUS_CM)) {
            SmithPrime.infect(w, victim);
        }
    }
}
