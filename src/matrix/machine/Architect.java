package matrix.machine;

import matrix.core.Severity;
import matrix.core.SystemState;
import matrix.core.World;
import matrix.entities.Avatar;
import matrix.entities.MatrixEntity;
import matrix.entities.Pill;
import matrix.entities.SmithCopy;
import matrix.entities.SmithPrime;
import matrix.entities.TheOne;

import java.util.List;

/** Father of the equation. A singleton, obviously. */
public enum Architect {
    INSTANCE;

    public void reload(World w, boolean emergency) {
        for (MatrixEntity e : List.copyOf(w.entities())) {
            if (!e.alive) continue;
            if (e instanceof SmithCopy c) w.replace(c, c.original);
            else if (e instanceof SmithPrime p) w.remove(p);
            else if (e instanceof TheOne one) w.remove(one);
        }
        w.flushPending();
        for (MatrixEntity e : w.entities()) {
            if (e instanceof Avatar a && a.alive) a.pill = Pill.BLUE;
        }
        w.bumpVersion();
        w.setAnomaly(0);
        w.setState(SystemState.NORMAL);
        if (emergency) {
            w.log(Severity.BAD, "EMERGENCY RELOAD — overflow with no anomaly, system was on the brink");
        } else {
            w.log(Severity.SYS, "Architect: reload initiated — \"exceedingly efficient\", force of habit");
        }
        w.log(Severity.BAD, "Zion purge: rogue clients plugged back in, fresh crop inbound");
        w.log(Severity.SYS, "MATRIX v" + w.version() + ".0 — the cycle begins again, nobody remembers");
    }
}
