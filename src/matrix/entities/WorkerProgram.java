package matrix.entities;

import matrix.core.Ansi;
import matrix.core.World;

/** A program doing its job is invisible — hence the faint rendering. */
public class WorkerProgram extends Program {
    public final boolean sati;

    public WorkerProgram(String purpose, int x, int y) {
        this(purpose, x, y, false);
    }

    public WorkerProgram(String purpose, int x, int y, boolean sati) {
        super(purpose, x, y);
        this.sati = sati;
    }

    @Override
    public void tick(World w) {
        if (w.rng().chance(0.25)) wander(w);
    }

    @Override
    public char glyph() {
        return sati ? '*' : '.';
    }

    @Override
    public String color(World w) {
        if (sati) return Ansi.BOLD + Ansi.GOLD;
        return w.dejaFlash() > 0 ? Ansi.BGREEN : Ansi.DGREEN;
    }

    @Override
    public int renderPriority() {
        return sati ? 4 : 1;
    }
}
