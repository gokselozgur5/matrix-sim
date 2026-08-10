package matrix.entities.behavior;

import matrix.core.World;
import matrix.entities.MatrixEntity;

/** Does not move. Flowers hold the line. */
public final class RootedMovement implements Movement {
    public static final RootedMovement INSTANCE = new RootedMovement();

    private RootedMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        // The whole point.
    }
}
