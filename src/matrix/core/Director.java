package matrix.core;

import matrix.entities.Avatar;
import matrix.entities.Pill;

import java.util.List;

/** v1.0 narrative: the awakening cadence that keeps the chase alive. The full arc arrives with v3.0. */
public final class Director {
    private final World world;

    public Director(World world) {
        this.world = world;
    }

    public void tick(long t) {
        if (t % Config.AWAKEN_EVERY_TICKS != 0) {
            return;
        }
        if (world.count(Pill.RED) >= Config.RED_CAP) {
            return;
        }
        List<Avatar> blues = world.aliveAvatars(Pill.BLUE);
        if (blues.isEmpty()) {
            return;
        }
        Avatar chosen = blues.get(world.rng().nextInt(blues.size()));
        chosen.pill = Pill.RED;
        world.log(Severity.OK, "red pill: " + chosen.pilotName + " woke up and dropped off the cluster");
    }
}
