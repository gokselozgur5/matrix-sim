package matrix.entities;

import matrix.core.Ansi;
import matrix.core.Severity;
import matrix.core.World;

/** Observer: listens to the EventBus, counts awakenings, bakes cookies. */
public final class Oracle extends Program {
    private int awakeningsSeen = 0;
    private int nextCookieAt = 6;

    public Oracle(World w, int x, int y) {
        super("analysis of the human psyche", x, y);
        w.bus().subscribe(ev -> {
            if (ev.sev() == Severity.OK) awakeningsSeen++;
        });
    }

    @Override
    public void tick(World w) {
        if (awakeningsSeen >= nextCookieAt) {
            nextCookieAt += 6;
            w.log(Severity.GOLD, "Oracle: cookies are ready — \"you've already made the choice, you're here to understand why\"");
        }
        if (w.rng().chance(0.2)) wander(w);
    }

    @Override
    public char glyph() {
        return 'O';
    }

    @Override
    public String color(World w) {
        return Ansi.GOLD;
    }

    @Override
    public int renderPriority() {
        return 4;
    }
}
