package matrix.entities;

import matrix.core.Severity;
import matrix.core.World;

/**
 * Pure software: its hardware lives in Machine City, it has no pod.
 * The deletion protocol is a template method — the Source calls it,
 * subclasses may object.
 */
public abstract class Program extends MatrixEntity {
    public final String purpose;

    protected Program(String purpose, int x, int y) {
        super(x, y);
        this.purpose = purpose;
    }

    public void handleDeletion(World w) {
        w.remove(this);
        w.log(Severity.DIM, "GC: \"" + purpose + "\" returned to the Source, deleted");
    }
}
