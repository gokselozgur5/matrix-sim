package matrix.entities.behavior;

import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.EnvironmentProgram;

/** Aimless drift for cats and strays. */
public final class WanderMovement implements Movement {
    public static final WanderMovement INSTANCE = new WanderMovement();

    private WanderMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        int speed = speedOf(self);
        self.pos = self.pos.steppedBy(
                w.rng().nextInt(-speed, speed + 1),
                w.rng().nextInt(-speed, speed + 1));
    }

    static int speedOf(MatrixEntity self) {
        return self instanceof EnvironmentProgram e ? e.species.speedCm() : 120;
    }
}
