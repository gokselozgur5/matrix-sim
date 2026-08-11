package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;

/**
 * The anomaly given a body. Human line, elevated permissions: where every
 * other intent passes validation, The One's commits — a cure is a Replace
 * that hands the victim's untouched original back to the world (D-001's
 * payoff). He cannot delete a prime alone; that takes a treaty.
 */
public final class TheOne extends Avatar {

    public TheOne(int id, Position pos, String pilotName) {
        super(id, pos, pilotName, Pill.RED);
    }

    @Override
    public void tick(World w) {
        MatrixEntity target = nearestReplicating(w);
        if (target == null) {
            if (w.rng().chance(0.6)) {
                wander(w, Config.RED_SPEED_CM);
            }
            return;
        }
        stepToward(target.pos, Config.ONE_SPEED_CM);
        if (pos.within(target.pos, Config.CONTACT_RADIUS_CM) && target instanceof SmithCopy copy) {
            w.queue(new WorldEvent.Replace(copy.id, copy.original));
            if (w.rng().chance(0.25)) {
                w.log(Severity.FATE, "The One: a copy deleted, an original restored");
            }
        }
    }

    private MatrixEntity nearestReplicating(World w) {
        MatrixEntity best = null;
        long bestD = Long.MAX_VALUE;
        for (MatrixEntity e : w.entities()) {
            if (e.alive && e instanceof SelfReplicating) {
                long d = pos.euclidSqCm(e.pos);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }
}
