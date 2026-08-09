package matrix.realworld;

public final class Brain {
    public final String owner;
    private boolean alive = true;

    public Brain(String owner) {
        this.owner = owner;
    }

    public boolean alive() {
        return alive;
    }

    public void flatline() {
        alive = false;
    }
}
