package matrix.entities;

import matrix.core.World;

public abstract class MatrixEntity {
    private static int nextId = 1;

    public final int id = nextId++;
    public int x;
    public int y;
    public boolean alive = true;

    protected MatrixEntity(int x, int y) {
        this.x = World.clamp(x, 0, World.W - 1);
        this.y = World.clamp(y, 0, World.H - 1);
    }

    public abstract void tick(World w);

    public abstract char glyph();

    public abstract String color(World w);

    public abstract int renderPriority();

    protected void wander(World w) {
        x = World.clamp(x + w.rng().step(), 0, World.W - 1);
        y = World.clamp(y + w.rng().step(), 0, World.H - 1);
    }

    protected void stepToward(World w, MatrixEntity t) {
        x = World.clamp(x + Integer.signum(t.x - x), 0, World.W - 1);
        y = World.clamp(y + Integer.signum(t.y - y), 0, World.H - 1);
    }

    protected void stepAway(World w, MatrixEntity t) {
        x = World.clamp(x - Integer.signum(t.x - x) + w.rng().step(), 0, World.W - 1);
        y = World.clamp(y - Integer.signum(t.y - y), 0, World.H - 1);
    }

    public int dist(MatrixEntity o) {
        return Math.max(Math.abs(x - o.x), Math.abs(y - o.y));
    }
}
