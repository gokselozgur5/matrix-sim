package matrix.realworld;

/** Life-support rack slot. Hosts a Human; does not own their fate (aggregation, D-011). */
public final class Pod {
    public final String rackUnit;
    private boolean occupied = true;

    public Pod(String rackUnit) {
        this.rackUnit = rackUnit;
    }

    public boolean occupied() {
        return occupied;
    }

    public void flush() {
        occupied = false;
    }
}
