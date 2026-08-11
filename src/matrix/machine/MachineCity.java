package matrix.machine;

import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.SystemState;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.MatrixEntity;
import matrix.entities.SmithCopy;
import matrix.entities.SmithPrime;
import matrix.entities.eco.EnvironmentProgram;
import matrix.entities.eco.Kingdom;
import matrix.entities.eco.MovementKind;
import matrix.entities.eco.Species;

import java.util.List;

/**
 * The negotiation counterpart (the Deus Ex Machina folded in). The treaty
 * is D-001's payoff at scale: every copy swaps back to its untouched
 * original in one pass — the mass restore is a loop of identity swaps,
 * trivially correct because the type system carried the originals.
 */
public final class MachineCity {

    private MachineCity() {}

    public static void executeTreaty(World w) {
        int restored = 0;
        int primes = 0;
        for (MatrixEntity e : List.copyOf(w.entities())) {
            if (!e.alive) {
                continue;
            }
            if (e instanceof SmithCopy c) {
                w.queue(new WorldEvent.Replace(c.id, c.original));
                restored++;
            } else if (e instanceof SmithPrime p) {
                w.queue(new WorldEvent.Remove(p.id));
                primes++;
            }
        }
        w.flush();
        w.bumpVersion();
        w.setState(SystemState.PEACE);
        w.log(Severity.OK, "delete broadcast complete — " + restored + " originals restored, "
                + primes + " prime erased");
        w.log(Severity.FATE, "The One is carried to the Source — he deleted, and was deleted; the equation reads zero");
        w.log(Severity.FATE, "REBOOT v" + w.version() + ".0 — peace protocol active, the door stays open");
        Position sky = new Position(w.places().zones().get(0).center().xCm(), 0);
        w.queue(new WorldEvent.Spawn(new EnvironmentProgram(w.allocateId(), sky,
                new Species("sunrise", Kingdom.OBJECT, MovementKind.ROOTED, 16, 1, 0))));
        w.flush();
        w.log(Severity.FATE, "Sati paints the sunrise — it was never in the spec, and nobody will delete it");
    }
}
