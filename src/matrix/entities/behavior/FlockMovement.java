package matrix.entities.behavior;

import matrix.core.Config;
import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.EnvironmentProgram;
import matrix.entities.eco.Kingdom;

import java.util.List;

/**
 * Boids — the one gait with real math, in pure fixed-point integers:
 * separation from the too-close, cohesion toward the neighborhood
 * centroid, alignment with the neighbors' headings. The heading memory
 * lives on the entity; this class stays a stateless singleton.
 */
public final class FlockMovement implements Movement {
    public static final FlockMovement INSTANCE = new FlockMovement();

    private FlockMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        if (!(self instanceof EnvironmentProgram me) || me.species.kingdom() != Kingdom.FAUNA_BIRD) {
            WanderMovement.INSTANCE.move(self, w);
            return;
        }
        int speed = me.species.speedCm();
        long sumX = 0, sumY = 0, headX = 0, headY = 0;
        int sepX = 0, sepY = 0, count = 0;
        long sep2 = (long) Config.FLOCK_SEPARATION_CM * Config.FLOCK_SEPARATION_CM;
        List<MatrixEntity> near = w.nearby(self, Config.FLOCK_NEIGHBOR_RADIUS_CM);
        for (MatrixEntity n : near) {
            if (n == self || !(n instanceof EnvironmentProgram other)
                    || other.species.kingdom() != Kingdom.FAUNA_BIRD) {
                continue;
            }
            count++;
            sumX += n.snapXCm;
            sumY += n.snapYCm;
            headX += other.headingX;
            headY += other.headingY;
            long ddx = (long) self.snapXCm - n.snapXCm;
            long ddy = (long) self.snapYCm - n.snapYCm;
            if (ddx * ddx + ddy * ddy <= sep2) {
                sepX += Integer.signum(self.snapXCm - n.snapXCm);
                sepY += Integer.signum(self.snapYCm - n.snapYCm);
            }
            if (count >= Config.FLOCK_MAX_NEIGHBORS) {
                break;
            }
        }
        int dx;
        int dy;
        if (count == 0) {
            dx = w.rng().nextInt(-speed, speed + 1);
            dy = w.rng().nextInt(-speed, speed + 1);
        } else {
            int cohX = Integer.signum((int) (sumX / count) - self.snapXCm);
            int cohY = Integer.signum((int) (sumY / count) - self.snapYCm);
            int alnX = Integer.signum((int) headX);
            int alnY = Integer.signum((int) headY);
            dx = (sepX * 2 + cohX + alnX) * (speed / 3);
            dy = (sepY * 2 + cohY + alnY) * (speed / 3);
            if (dx == 0 && dy == 0) {
                dx = w.rng().nextInt(-speed / 2, speed / 2 + 1);
                dy = w.rng().nextInt(-speed / 2, speed / 2 + 1);
            }
        }
        me.headingX = clampStep(dx, speed);
        me.headingY = clampStep(dy, speed);
        self.moveBy(me.headingX, me.headingY);
    }

    private static int clampStep(int delta, int speed) {
        if (delta > speed) return speed;
        if (delta < -speed) return -speed;
        return delta;
    }
}
