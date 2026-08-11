package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.World;

/**
 * A human presence: a proxy driven by a brain OUTSIDE the simulation.
 * This class holds no real-world references (D-013) — only the pilot's
 * name, for the narrative, and since #117 the recall order as one bit
 * of intent. Death is observed by the NeuralLink from the other side of
 * the boundary; the avatar just stops.
 */
public class Avatar extends MatrixEntity implements Chooses {
    public final String pilotName;
    public Pill pill;
    /**
     * The recall order, as the pilot hears it (#117; D-013 kept — one
     * bit of intent, no real-world reference). A recalled mind sprints
     * for the nearest exit booth every tick: the red flee pathing, minus
     * the fear check and minus the wander draw — an order outruns both.
     * The wire on the other side decides what reaching a booth is worth;
     * in here, an avatar is still just an avatar (A1) — agents neither
     * see the order nor stop hunting it.
     */
    public boolean recalled = false;

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
        if (recalled) {
            stepToward(w.places().nearestExit(xCm(), yCm()), Config.RED_SPEED_CM);
            return;
        }
        Agent threat = w.nearestAgent(xCm(), yCm());
        if (threat != null && within(threat, Config.FLEE_TRIGGER_CM)) {
            stepToward(w.places().nearestExit(xCm(), yCm()), Config.RED_SPEED_CM);
        } else if (w.rng().chance(0.9)) {
            wander(w, Config.RED_SPEED_CM);
        }
    }
}
