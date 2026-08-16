package matrix.entities;

import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;

/**
 * Pure software citizen; purpose-bound. Deletion is a template method:
 * the Source calls it under the D-025 protocol; the default is quiet
 * compliance; subclasses may hide (exiles) or refuse loudly (one does).
 */
public abstract class Program extends MatrixEntity {

    /**
     * What this program answers when asked what it is (#1312, half of #660).
     *
     * <p>A program's sheet derives from its PURPOSE STRING, and that is
     * D-014's monument expressed rather than special-cased: Smith's derives
     * from {@code "purpose: I decide for myself"}, the self-authored string
     * deriving the fate. No branch names him, no subclass overrides this, and
     * the licence is visible in the data rather than in an {@code if}.
     *
     * <p>DERIVED, NEVER STORED, and read at the door — the same law the human
     * wing answers under (#658), for the same reason: a cached derived value
     * is a second source of truth. {@code purpose} is {@code final}, so the
     * identity cannot move under the sheet.
     *
     * <p>THE STOLEN IDENTITY IS NOT HERE. A {@code SmithCopy} wears a
     * victim's face and is a {@code MatrixEntity}, never a {@code Program} —
     * it is a Decorator over the victim's own object (D-001), so it inherits
     * nothing from this method and cannot be answered by it. #660 keeps that
     * half: one fenced read-through branch, the Decorator untouched. This
     * method is deliberately unable to reach it.
     */
    public matrix.character.Sheet sheet() {
        return matrix.character.SheetDoor.at(purpose, matrix.character.Family.PROGRAM);
    }

    public final String purpose;

    protected Program(int id, Position pos, String purpose) {
        super(id, pos);
        this.purpose = purpose;
    }

    public void handleDeletion(World w) {
        alive = false;
        w.queue(new WorldEvent.Remove(id));
        w.log(Severity.TRACE, "GC: \"" + purpose + "\" returned to the Source, deleted");
    }
}
