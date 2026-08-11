package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.World;

/**
 * A deprecated process that refuses to die QUIETLY: unlike Smith it does
 * not throw — it hides. handleDeletion completes with the exile still
 * alive, and the Source registers the survivor as an orphan (D-025).
 * Folklore is the users noticing.
 */
public final class ExileProgram extends Program {
    public final ExileKind kind;

    public ExileProgram(int id, Position pos, ExileKind kind) {
        super(id, pos, "exile: " + kind.label);
        this.kind = kind;
    }

    @Override
    public void tick(World w) {
        if (w.rng().chance(0.15)) {
            wander(w, Config.BLUE_SPEED_CM);
        }
    }

    @Override
    public void handleDeletion(World w) {
        placeAt(w.rng().nextInt(Config.WORLD_W_CM + 1), w.rng().nextInt(Config.WORLD_H_CM + 1));
        w.log(Severity.MYTH, "exile " + kind.label + " swallowed the SIGTERM — gone to ground");
    }
}
