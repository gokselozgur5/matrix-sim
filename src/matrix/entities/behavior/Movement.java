package matrix.entities.behavior;

import matrix.core.World;
import matrix.entities.MatrixEntity;

/**
 * The gait strategy contract (D-016). Strategies are stateless singletons;
 * whatever memory a gait needs (a heading, a waypoint phase) lives on the
 * entity that walks. Avatars may borrow gaits too — COMMUTE drives the
 * blue-pill routine.
 */
public interface Movement {
    void move(MatrixEntity self, World w);
}
