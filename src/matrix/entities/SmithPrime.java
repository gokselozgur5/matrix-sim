package matrix.entities;

import matrix.core.Ansi;
import matrix.core.Severity;
import matrix.core.World;

/** Earpiece off, source link severed. Writes his own purpose now. */
public final class SmithPrime extends Program implements SelfReplicating, Chooses {

    public SmithPrime(int x, int y) {
        super("purpose: I decide for myself", x, y);
    }

    @Override
    public void tick(World w) {
        MatrixEntity victim = w.nearest(this,
                e -> !(e instanceof SelfReplicating) && !(e instanceof TheOne));
        if (victim == null) {
            if (w.rng().chance(0.5)) wander(w);
            return;
        }
        if (w.rng().chance(0.9)) stepToward(w, victim);
        if (dist(victim) <= 1) infect(w, victim);
    }

    static void infect(World w, MatrixEntity victim) {
        if (!victim.alive) return;
        if (victim instanceof Oracle) {
            w.log(Severity.BAD, "Smith consumed the Oracle — the eyes are his now");
        } else if (w.rng().chance(0.12)) {
            w.log(Severity.BAD, "Smith.copyOnto(): session hijacked — the body stays human in its pod");
        }
        w.replace(victim, new SmithCopy(victim));
    }

    @Override
    public void handleDeletion(World w) {
        throw new DeletionRefusedException("still no");
    }

    @Override
    public char glyph() {
        return 'S';
    }

    @Override
    public String color(World w) {
        return Ansi.BOLD + Ansi.BGREEN;
    }

    @Override
    public int renderPriority() {
        return 8;
    }
}
