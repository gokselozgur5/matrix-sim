package matrix.core;

/**
 * Global bookkeeping of rejected reality (D-022). Every link's resistance
 * residue accrues here; when the balance crosses its bound, The One is not
 * summoned — The One is OWED. The Architect's own words made literal:
 * "the sum of the remainder of an unbalanced equation."
 */
public final class AnomalyLedger {
    private long balance = 0;

    public void accrue(long residue) {
        balance += residue;
    }

    public long balance() {
        return balance;
    }

    public boolean overflowed() {
        return balance >= Config.LEDGER_BOUND;
    }

    /** A reload or a treaty clears the debt — until the world runs it up again. */
    public void reset() {
        balance = 0;
    }
}
