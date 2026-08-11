package matrix;

import matrix.zion.Zion;

/**
 * The free city as one system node (D-031) — the fence event itself: a
 * third implementor, arriving as an addition, never a refactor. A thin
 * adapter mirroring RealWorldSystem; all domain logic lives in Zion.
 */
public final class ZionSystem implements SystemNode {
    private final Zion zion;

    public ZionSystem(Zion zion) {
        this.zion = zion;
    }

    @Override
    public String name() {
        return "zion";
    }

    @Override
    public void tick(long tick) {
        zion.tick(tick);
    }
}
