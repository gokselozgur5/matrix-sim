package matrix.entities;

import matrix.core.Position;

/** Pure software citizen; purpose-bound. The deletion protocol arrives with v2.0 (D-003/D-025). */
public abstract class Program extends MatrixEntity {
    public final String purpose;

    protected Program(int id, Position pos, String purpose) {
        super(id, pos);
        this.purpose = purpose;
    }
}
