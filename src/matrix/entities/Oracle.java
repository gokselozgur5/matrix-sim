package matrix.entities;

import matrix.core.Config;
import matrix.core.Event;
import matrix.core.EventBus;
import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.World;

/**
 * Observer program: counts awakenings on the bus, bakes cookies.
 * The listener only counts — emission happens in tick(), because
 * publishing while receiving is a hard error (EventBus law).
 */
public final class Oracle extends Program {
    private int awakeningsSeen = 0;
    private int cookiesBaked = 0;

    public Oracle(int id, Position pos, EventBus bus) {
        super(id, pos, "analysis of the human psyche");
        bus.subscribe(this::listen);
    }

    private void listen(Event e) {
        if (e.sev() == Severity.OK && e.msg().startsWith("red pill:")) {
            awakeningsSeen++;
        }
    }

    @Override
    public void tick(World w) {
        if (awakeningsSeen / Config.COOKIES_EVERY_AWAKENINGS > cookiesBaked) {
            cookiesBaked++;
            w.log(Severity.FATE, "the Oracle: cookies are ready — \"you've already made the choice; "
                    + "you're here to understand why\"");
        }
        if (w.rng().chance(0.2)) {
            wander(w, Config.BLUE_SPEED_CM);
        }
    }
}
