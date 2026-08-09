package matrix.entities;

import matrix.core.Ansi;
import matrix.core.World;

/**
 * Decorator: the victim's original object is kept inside, untouched.
 * Delete the Smith and the original snaps back — the mass restore
 * in the finale depends on this.
 */
public final class SmithCopy extends MatrixEntity implements SelfReplicating {
    public final MatrixEntity original;

    public SmithCopy(MatrixEntity original) {
        super(original.x, original.y);
        this.original = original;
    }

    @Override
    public void tick(World w) {
        MatrixEntity victim = w.nearest(this,
                e -> !(e instanceof SelfReplicating) && !(e instanceof TheOne));
        if (victim == null) return;
        if (w.rng().chance(0.6)) stepToward(w, victim);
        if (dist(victim) <= 1) SmithPrime.infect(w, victim);
    }

    @Override
    public char glyph() {
        return 'S';
    }

    @Override
    public String color(World w) {
        return Ansi.BGREEN;
    }

    @Override
    public int renderPriority() {
        return 7;
    }
}
