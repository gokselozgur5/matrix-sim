package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.World;

/** IDS daemon: hunts rogue clients. Catch outcome per accepted D-002: 90% replug, 10% terminate. */
public final class Agent extends Program {
    public final String codename;

    public Agent(int id, Position pos, String codename) {
        super(id, pos, "security daemon " + codename);
        this.codename = codename;
    }

    @Override
    public void tick(World w) {
        Avatar prey = w.nearestRed(pos);
        if (prey == null) {
            if (w.rng().chance(0.5)) {
                wander(w, Config.AGENT_SPEED_CM);
            }
            return;
        }
        stepToward(prey.pos, Config.AGENT_SPEED_CM);
        if (pos.within(prey.pos, Config.CONTACT_RADIUS_CM)) {
            if (w.rng().chance(Config.AGENT_KILL_CHANCE)) {
                w.kill(prey, "agent " + codename);
            } else {
                prey.pill = Pill.BLUE;
                w.log(Severity.SYS, "agent " + codename + ": rogue client " + prey.pilotName
                        + " caught, plugged back in");
            }
        }
    }
}
