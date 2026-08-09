package matrix.realworld;

public final class Pod {
    public final String rackUnit;
    public final Brain brain;
    private boolean occupied = true;

    public Pod(String rackUnit, Brain brain) {
        this.rackUnit = rackUnit;
        this.brain = brain;
    }

    public boolean occupied() {
        return occupied;
    }

    public void flush() {
        occupied = false;
    }
}
