package matrix.entities.behavior;

import matrix.core.Config;
import matrix.core.PlaceGraph;
import matrix.core.Position;
import matrix.core.World;
import matrix.entities.MatrixEntity;

/**
 * The blue-pill routine: home to work to home. Predictability is the
 * point — the schedule is a pure function of the tick and the entity id,
 * so a sleeping mind needs no memory to live its loop.
 */
public final class CommuteMovement implements Movement {
    public static final CommuteMovement INSTANCE = new CommuteMovement();

    private CommuteMovement() {}

    @Override
    public void move(MatrixEntity self, World w) {
        PlaceGraph places = w.places();
        int zones = places.zones().size();
        Position home = places.zones().get(Math.floorMod(self.id, zones)).center();
        Position work = places.zones().get(Math.floorMod(self.id + 3, zones)).center();
        boolean workward = (w.tick() / Config.COMMUTE_SWITCH_TICKS) % 2 == 0;
        Position dest = workward ? work : home;
        if (self.pos.within(dest, Config.COMMUTE_ARRIVE_CM)) {
            return;
        }
        int speed = WanderMovement.speedOf(self);
        self.pos = self.pos.steppedBy(
                clampStep(dest.xCm() - self.pos.xCm(), speed),
                clampStep(dest.yCm() - self.pos.yCm(), speed));
    }

    private static int clampStep(int delta, int speed) {
        if (delta > speed) return speed;
        if (delta < -speed) return -speed;
        return delta;
    }
}
