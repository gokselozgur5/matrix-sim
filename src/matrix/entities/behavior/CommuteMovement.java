package matrix.entities.behavior;

import matrix.core.Config;
import matrix.core.Geo;
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
        int homeIdx = Math.floorMod(self.id, zones);
        Position home = places.zones().get(homeIdx).center();
        Position work = places.nearestOtherZone(homeIdx).center();
        boolean workward = (w.tick() / Config.COMMUTE_SWITCH_TICKS) % 2 == 0;
        Position dest = workward ? work : home;
        if (Geo.within(self.xCm(), self.yCm(), dest.xCm(), dest.yCm(), Config.COMMUTE_ARRIVE_CM)) {
            return;
        }
        int speed = WanderMovement.speedOf(self);
        self.moveBy(
                clampStep(dest.xCm() - self.xCm(), speed),
                clampStep(dest.yCm() - self.yCm(), speed));
    }

    private static int clampStep(int delta, int speed) {
        if (delta > speed) return speed;
        if (delta < -speed) return -speed;
        return delta;
    }
}
