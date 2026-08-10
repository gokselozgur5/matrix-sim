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
