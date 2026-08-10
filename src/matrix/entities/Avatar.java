package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.World;

/**
 * A human presence: a proxy driven by a brain OUTSIDE the simulation.
 * This class holds no real-world references (D-013) — only the pilot's
 * name, for the narrative. Death is observed by the NeuralLink from the
 * other side of the boundary; the avatar just stops.
 */
public final class Avatar extends MatrixEntity implements Chooses {
    public final String pilotName;
    public Pill pill;

    public Avatar(int id, Position pos, String pilotName, Pill pill) {
        super(id, pos);
        this.pilotName = pilotName;
        this.pill = pill;
    }

    @Override
    public void tick(World w) {
        if (pill == Pill.BLUE) {
            matrix.entities.behavior.CommuteMovement.INSTANCE.move(this, w);
            return;
        }
        Agent threat = w.nearestAgent(pos);
        if (threat != null && pos.within(threat.pos, Config.FLEE_TRIGGER_CM)) {
            stepToward(w.places().nearestExit(pos), Config.RED_SPEED_CM);
        } else if (w.rng().chance(0.9)) {
            wander(w, Config.RED_SPEED_CM);
        }
    }
}
