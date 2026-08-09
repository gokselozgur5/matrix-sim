package matrix.entities;

import matrix.core.Ansi;
import matrix.core.Severity;
import matrix.core.World;

/** Deprecated processes that refused deletion. The source of all mythology. */
public final class ExileProgram extends Program {

    public enum Kind {
        MEROVINGIAN("Merovingian"),
        TWIN("the Twins"),
        VAMPIRE("vampire v2.1"),
        WEREWOLF("werewolf v1.9"),
        KEYMAKER("Keymaker"),
        TRAINMAN("Trainman");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public final Kind kind;

    public ExileProgram(Kind kind, int x, int y) {
        super("exile: " + kind.label, x, y);
        this.kind = kind;
    }

    @Override
    public void tick(World w) {
        if (w.rng().chance(0.15)) wander(w);
    }

    @Override
    public void handleDeletion(World w) {
        x = w.rng().nextInt(World.W);
        y = w.rng().nextInt(World.H);
        w.log(Severity.PUR, "exile \"" + kind.label + "\" swallowed SIGTERM — hiding in a corner");
    }

    @Override
    public char glyph() {
        return 'e';
    }

    @Override
    public String color(World w) {
        return Ansi.MAG;
    }

    @Override
    public int renderPriority() {
        return 3;
    }
}
