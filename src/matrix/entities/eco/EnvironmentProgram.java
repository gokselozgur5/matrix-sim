package matrix.entities.eco;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.RegionMap;
import matrix.core.Scheduler;
import matrix.core.World;
import matrix.entities.Program;
import matrix.entities.behavior.CommuteMovement;
import matrix.entities.behavior.DriftMovement;
import matrix.entities.behavior.FlockMovement;
import matrix.entities.behavior.RootedMovement;
import matrix.entities.behavior.SwarmMovement;
import matrix.entities.behavior.WanderMovement;

/**
 * THE class behind every bird, flower, insect and raindrop (D-015):
 * one class, twelve species, zero subclasses. The gait is selected by
 * the catalog row (D-016); the cadence by the scheduling wheel (D-018)
 * — stretched by LOD_COLD_STRETCH while the snapshot lies in a COLD
 * region (D-024 P1, #131): the cold city dreams slower, and a skipped
 * tick skips its rng draws too. After LOD_PARK_AFTER_TICKS of nobody
 * watching, the region parks outright (P2, #132): catalog residents fold
 * into the RegionMap aggregate and leave the walk until attention brings
 * them back — same ids, freshly drawn faces. A healthy environment
 * program is invisible — it never logs.
 */
public final class EnvironmentProgram extends Program {
    public final Species species;
    public int headingX;
    public int headingY;

    public EnvironmentProgram(int id, Position pos, Species species) {
        super(id, pos, species.id() + " program");
        this.species = species;
    }

    @Override
    public void tick(World w) {
        if (!Scheduler.due(w.tick(), species.tickPeriod(), id)) {
            return;
        }
        RegionMap regions = w.regions();
        if (!regions.isHot(regions.regionAt(snapXCm, snapYCm))
                && !Scheduler.due(w.tick(), species.tickPeriod() * Config.LOD_COLD_STRETCH, id)) {
            // COLD: only every LOD_COLD_STRETCH-th due tick survives — and the
            // skipped tick draws nothing, so the degraded film is its own film.
            return;
        }
        switch (species.movement()) {
            case FLOCK -> FlockMovement.INSTANCE.move(this, w);
            case SWARM -> SwarmMovement.INSTANCE.move(this, w);
            case ROOTED -> RootedMovement.INSTANCE.move(this, w);
            case DRIFT -> DriftMovement.INSTANCE.move(this, w);
            case WANDER -> WanderMovement.INSTANCE.move(this, w);
            case COMMUTE -> CommuteMovement.INSTANCE.move(this, w);
        }
    }
}
