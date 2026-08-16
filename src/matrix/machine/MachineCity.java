package matrix.machine;

import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.SystemState;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.MatrixEntity;
import matrix.entities.SmithCopy;
import matrix.entities.SmithPrime;
import matrix.entities.eco.Bestiary;
import matrix.entities.eco.EnvironmentProgram;

import java.util.List;

/**
 * The negotiation counterpart (the Deus Ex Machina folded in). The treaty
 * is D-001's payoff at scale: every copy swaps back to its untouched
 * original in one pass — the mass restore is a loop of identity swaps,
 * trivially correct because the type system carried the originals.
 */
public final class MachineCity {

    /**
     * The city's name, and therefore the city's fate. Minted in a probe by
     * #1010 and moved here by #659, because capitalization is identity: these
     * bytes ARE the MACHINE row, and a name that lives in the instrument
     * rather than in the world is a fact about the instrument.
     */
    public static final String NAME = "the Machine City";

    /**
     * What the machine wing answers when asked what it is (#659, leaf of
     * #350) — {@code power · precision · relentlessness}, through the same
     * door as every other resident.
     *
     * <p>STATIC, and the exception is the wing rather than a shortcut. The
     * human and program wings answer per resident because they have
     * residents; this side is singletons and statics — {@code Source} holds a
     * world and a registry, {@code Architect} is {@code enum … INSTANCE},
     * {@code SubstrateBudget} is two ints — and not one of them carries an
     * identity string. So the city answers for itself, one row, which is what
     * #1010 established when {@code souls=0} turned out to be a finding
     * rather than a gap.
     *
     * <p>Residents may append later — sentinels and harvesters, as catalog
     * data under D-015. They would answer per instance beside this row and
     * would not replace it, and this row's numbers would not move.
     *
     * <p>Derived, never stored, and no world read: the same law the other two
     * wings answer under, checked by the same counter.
     */
    public static matrix.character.Sheet sheet() {
        return matrix.character.SheetDoor.at(NAME, matrix.character.Family.MACHINE);
    }

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
        // The row is Bestiary.SUNRISE and not a Species written here (#974):
        // a species minted at a call site is a species no budget can count.
        w.queue(new WorldEvent.Spawn(
                new EnvironmentProgram(w.allocateId(), sky, Bestiary.SUNRISE)));
        w.flush();
        w.log(Severity.FATE, "Sati paints the sunrise — it was never in the spec, and nobody will delete it");
    }
}
