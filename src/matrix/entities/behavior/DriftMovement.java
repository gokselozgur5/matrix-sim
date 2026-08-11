package matrix.entities.behavior;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.World;
import matrix.entities.MatrixEntity;

/** Field flow: rain falls one way, recycles at the ground. */
public final class DriftMovement implements Movement {
    public static final DriftMovement INSTANCE = new DriftMovement();

    private DriftMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        int speed = WanderMovement.speedOf(self);
        int jitter = w.rng().nextInt(-speed / 4, speed / 4 + 1);
        int x = Math.floorMod(self.xCm() + jitter, Config.WORLD_W_CM + 1);
        int y = self.yCm() + speed;
        if (y > Config.WORLD_H_CM) {
            y = 0;
        }
        self.pos = new Position(x, y);
    }
}
