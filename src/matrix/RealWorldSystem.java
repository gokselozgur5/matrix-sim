package matrix;

import matrix.realworld.Bond;
import matrix.realworld.RealWorld;

/** The biological side as one system node (D-031): the mind-body rule runs here (D-013). */
public final class RealWorldSystem implements SystemNode {
    private final RealWorld realWorld;
    private final Bond.Registry bonds;

    public RealWorldSystem(RealWorld realWorld, Bond.Registry bonds) {
        this.realWorld = realWorld;
        this.bonds = bonds;
    }

    @Override
    public String name() {
        return "realworld";
    }

    /**
     * Order inside the node is law, not taste: the heart runs BEFORE the
     * death rule. An edge forming is bookkeeping and could stand anywhere,
     * but an edge that will one day have to answer for a death (#325) must
     * be asked BEFORE D-013 writes it — a rule cannot be excepted after it
     * has already run. Putting the two calls in this order once means the
     * clause never needs a second opinion about when it gets to speak.
     */
    @Override
    public void tick(long tick) {
        bonds.tick(tick);
        realWorld.tick(tick);
    }
}
