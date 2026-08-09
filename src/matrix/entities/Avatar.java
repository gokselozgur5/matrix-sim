package matrix.entities;

import matrix.core.Ansi;
import matrix.core.Severity;
import matrix.core.SystemState;
import matrix.core.World;
import matrix.realworld.Brain;

/**
 * The human presence inside the Matrix. Consciousness is NOT here — it runs
 * on the brain in its pod; this object is just a Proxy driven by live I/O.
 */
public class Avatar extends MatrixEntity implements Chooses {
    public final Brain brain;
    public Pill pill;

    public Avatar(Brain brain, Pill pill, int x, int y) {
        super(x, y);
        this.brain = brain;
        this.pill = pill;
    }

    @Override
    public void tick(World w) {
        if (pill == Pill.BLUE) {
            if (w.rng().chance(0.35)) wander(w);
            return;
        }
        MatrixEntity threat = w.nearest(this, e -> e instanceof SelfReplicating
                || (e instanceof Agent && w.state() == SystemState.NORMAL));
        if (threat != null && dist(threat) < 8) {
            stepAway(w, threat);
        } else if (w.rng().chance(0.9)) {
            wander(w);
        }
        MatrixEntity sleeper = w.nearest(this, e -> e instanceof Avatar a && a.pill == Pill.BLUE);
        if (sleeper != null && dist(sleeper) <= 1 && w.rng().chance(0.12)) {
            ((Avatar) sleeper).pill = Pill.RED;
            w.addAnomaly(4);
            if (w.rng().chance(0.3)) {
                w.log(Severity.OK, "red pill: a node woke up and dropped off the cluster");
            }
        }
    }

    /** "The body cannot live without the mind" — if the avatar dies, the brain follows. */
    public void die(World w) {
        if (!alive) return;
        w.remove(this);
        brain.flatline();
        w.pods().flush(w, brain);
    }

    @Override
    public char glyph() {
        return pill == Pill.RED ? 'r' : 'o';
    }

    @Override
    public String color(World w) {
        return pill == Pill.RED ? Ansi.RED : Ansi.BLUE;
    }

    @Override
    public int renderPriority() {
        return pill == Pill.RED ? 5 : 2;
    }
}
