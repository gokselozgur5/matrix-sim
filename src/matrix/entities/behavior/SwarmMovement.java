package matrix.entities.behavior;

import matrix.core.Config;
import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.EnvironmentProgram;

import java.util.List;

/** Insect boil: jitter plus weak pull toward the nearest of your own kind. */
public final class SwarmMovement implements Movement {
    public static final SwarmMovement INSTANCE = new SwarmMovement();

    private SwarmMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        int speed = WanderMovement.speedOf(self);
        int dx = w.rng().nextInt(-speed, speed + 1);
        int dy = w.rng().nextInt(-speed, speed + 1);
        if (self instanceof EnvironmentProgram me) {
            List<MatrixEntity> near = w.nearby(self, Config.SWARM_RADIUS_CM);
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
