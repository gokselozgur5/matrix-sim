package matrix.entities;

import matrix.core.Ansi;
import matrix.core.Severity;
import matrix.core.SystemState;
import matrix.core.World;

/** IDS daemon: hunts rogue clients. Earpiece in, linked to the Source. */
public class Agent extends Program {
    public final String name;

    public Agent(String name, int x, int y) {
        super("security daemon " + name, x, y);
        this.name = name;
    }

    @Override
    public void tick(World w) {
        if (w.state() == SystemState.PEACE) {
            if (w.rng().chance(0.3)) wander(w);
            return;
        }
        MatrixEntity prey = w.nearest(this,
                e -> e instanceof Avatar a && a.pill == Pill.RED && !(e instanceof TheOne));
        if (prey == null) {
            if (w.rng().chance(0.6)) wander(w);
            return;
        }
        stepToward(w, prey);
        if (dist(prey) <= 1) {
            Avatar caught = (Avatar) prey;
            if (w.rng().chance(0.10)) {
                caught.die(w);
                w.log(Severity.BAD, "agent " + name + ": session terminated — the hard way");
            } else {
                caught.pill = Pill.BLUE;
                if (w.rng().chance(0.35)) {
                    w.log(Severity.SYS, "agent " + name + ": rogue node caught, plugged back in");
                }
            }
        }
    }

    @Override
    public char glyph() {
        return 'A';
    }

    @Override
    public String color(World w) {
        return Ansi.WHITE;
    }

    @Override
    public int renderPriority() {
        return 6;
    }
}
