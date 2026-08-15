package matrix.entities.behavior;

import matrix.core.Config;
import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.EnvironmentProgram;

import java.util.ArrayList;
import java.util.List;

/**
 * Insect boil: jitter plus weak pull toward the nearest of your own kind.
 *
 * <p>Holds its own {@link #NEIGHBORS} list for the world to fill (#823). It is
 * this gait's list and no other's — {@code FlockMovement} cannot rewrite an
 * answer this one is still walking, which is the property that stopped being
 * true for free when both of them shared the hash's buffer.
 *
 * <p>One list per THREAD, for {@code FlockMovement}'s reason and found in the
 * same sweep (#1135): the instance is a singleton, so "this gait's list" was
 * one list for every {@code Simulation} in the JVM, not one per world. Only
 * FlockMovement was observed throwing, because a flock queries more often than
 * a swarm — which makes this one the quieter half of the same defect and not
 * the safer half.
 */
public final class SwarmMovement implements Movement {
    public static final SwarmMovement INSTANCE = new SwarmMovement();

    /**
     * Filled and drained inside one call to {@link #move}; never escapes it,
     * and never leaves the thread that filled it.
     */
    private static final ThreadLocal<List<MatrixEntity>> NEIGHBORS =
            ThreadLocal.withInitial(ArrayList::new);

    private SwarmMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        int speed = WanderMovement.speedOf(self);
        int dx = w.rng().nextInt(-speed, speed + 1);
        int dy = w.rng().nextInt(-speed, speed + 1);
        if (self instanceof EnvironmentProgram me) {
            List<MatrixEntity> near = w.nearbyInto(self, Config.SWARM_RADIUS_CM, NEIGHBORS.get());
            for (MatrixEntity n : near) {
                if (n != self && n instanceof EnvironmentProgram other
                        && other.species.id().equals(me.species.id())) {
                    dx += Integer.signum(n.snapXCm - self.snapXCm) * (speed / 2);
                    dy += Integer.signum(n.snapYCm - self.snapYCm) * (speed / 2);
                    break;
                }
            }
        }
        self.moveBy(dx, dy);
    }
}
