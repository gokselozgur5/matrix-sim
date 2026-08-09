package matrix.entities;

import matrix.core.Ansi;
import matrix.core.Severity;
import matrix.core.World;
import matrix.realworld.Brain;

public final class TheOne extends Avatar {

    public TheOne(Brain brain, int x, int y) {
        super(brain, Pill.RED, x, y);
    }

    @Override
    public void tick(World w) {
        MatrixEntity target = w.nearest(this, e -> e instanceof SelfReplicating);
        if (target == null) {
            if (w.rng().chance(0.6)) wander(w);
            return;
        }
        stepToward(w, target);
        if (dist(target) <= 1 && target instanceof SmithCopy copy) {
            w.replace(copy, copy.original);
            if (w.rng().chance(0.3)) {
                w.log(Severity.GOLD, "Neo: copy deleted, original mind restored");
            }
        }
    }

    @Override
    public char glyph() {
        return '@';
    }

    @Override
    public String color(World w) {
        return Ansi.BOLD + Ansi.GOLD;
    }

    @Override
    public int renderPriority() {
        return 9;
    }
}
