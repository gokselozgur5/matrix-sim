package matrix;

import matrix.realworld.RealWorld;

/** The biological side as one system node (D-031): the mind-body rule runs here (D-013). */
public final class RealWorldSystem implements SystemNode {
    private final RealWorld realWorld;

    public RealWorldSystem(RealWorld realWorld) {
        this.realWorld = realWorld;
    }

    @Override
    public String name() {
        return "realworld";
    }

    @Override
    public void tick(long tick) {
        realWorld.tick(tick);
    }
}
