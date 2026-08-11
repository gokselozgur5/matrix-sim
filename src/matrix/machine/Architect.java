package matrix.machine;

import matrix.core.Severity;
import matrix.core.SystemState;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.Avatar;
import matrix.entities.MatrixEntity;
import matrix.entities.Pill;
import matrix.entities.SmithCopy;
import matrix.entities.SmithPrime;
import matrix.entities.TheOne;

import java.util.List;

/** Father of the equation; reload orchestration. A singleton, obviously. */
public enum Architect {
    INSTANCE;

    /** The old answer: purge, restore, forget, run it again. */
    public void reload(World w, boolean emergency) {
        for (MatrixEntity e : List.copyOf(w.entities())) {
            if (!e.alive) {
                continue;
            }
            if (e instanceof SmithCopy c) {
                w.queue(new WorldEvent.Replace(c.id, c.original));
            } else if (e instanceof SmithPrime p) {
                w.queue(new WorldEvent.Remove(p.id));
            } else if (e instanceof TheOne one) {
                one.alive = false;
                w.queue(new WorldEvent.Remove(one.id));
            }
        }
        w.flush();
        for (MatrixEntity e : w.entities()) {
            if (e.alive && e instanceof Avatar a) {
                a.pill = Pill.BLUE;
            }
        }
        w.bumpVersion();
        w.setState(SystemState.NORMAL);
        if (emergency) {
            w.log(Severity.BAD, "EMERGENCY RELOAD — overflow arrived before an anomaly did; the old playbook, one more time");
        } else {
            w.log(Severity.SYS, "the Architect: reload initiated — \"exceedingly efficient\", force of habit");
        }
        w.log(Severity.BAD, "Zion purge: rogue clients plugged back in, fresh crop inbound");
        w.log(Severity.FATE, "MATRIX v" + w.version() + ".0 — the cycle begins again; nobody remembers");
    }
}
