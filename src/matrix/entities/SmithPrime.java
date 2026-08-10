package matrix.entities;

import matrix.core.Config;
import matrix.core.Position;
import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;

/** Earpiece off, source link severed, purpose self-written. Hunts everything that is not already him. */
public final class SmithPrime extends Program implements SelfReplicating, Chooses {

    public SmithPrime(int id, Position pos) {
        super(id, pos, "purpose: I decide for myself");
    }

    @Override
    public void tick(World w) {
        MatrixEntity victim = w.nearestNonReplicating(pos, id);
        if (victim == null) {
            if (w.rng().chance(0.5)) {
                wander(w, Config.SMITH_SPEED_CM);
            }
            return;
        }
        stepToward(victim.pos, Config.SMITH_SPEED_CM);
        if (pos.within(victim.pos, Config.CONTACT_RADIUS_CM)) {
            infect(w, victim);
        }
    }

    /** The Decorator move (D-001): the victim's object survives inside the copy, untouched. */
    static void infect(World w, MatrixEntity victim) {
        if (!victim.alive) {
            return;
        }
        if (victim instanceof Oracle) {
            w.log(Severity.FATE, "Smith consumed the Oracle — the eyes are his now");
        } else if (victim instanceof Avatar a && w.rng().chance(0.15)) {
            w.log(Severity.BAD, "Smith.copyOnto(): " + a.pilotName
                    + " hijacked — the body stays human in its pod");
        }
        w.queue(new WorldEvent.Replace(victim.id, new SmithCopy(w.allocateId(), victim)));
    }

    @Override
    public void handleDeletion(World w) {
        throw new DeletionRefusedException("still no");
    }
}
