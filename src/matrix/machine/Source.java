package matrix.machine;

import matrix.core.Config;
import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.AgentSmith;
import matrix.entities.DeletionRefusedException;
import matrix.entities.Program;
import matrix.entities.SmithPrime;

import java.util.ArrayList;
import java.util.List;

/**
 * Where programs are deleted — supervisor-lite (D-025): SIGTERM, a grace
 * period, then the protocol. Compliance is silent retirement; quiet
 * survival makes an orphan; a thrown refusal is the one case the
 * constitution said could never happen (D-003) — and its handler is
 * where Smith is born.
 */
public final class Source {
    private record Pending(Program target, long dueTick) {}

    private final World world;
    private final OrphanRegistry registry = new OrphanRegistry();
    private final List<Pending> pending = new ArrayList<>();

    public Source(World world) {
        this.world = world;
    }

    public OrphanRegistry registry() {
        return registry;
    }

    public void collect(Program p) {
        world.log(Severity.SYS, "the Source: SIGTERM sent to \"" + p.purpose
                + "\" — grace period " + Config.GRACE_TICKS + " ticks");
        pending.add(new Pending(p, world.tick() + Config.GRACE_TICKS));
    }

    public void tick(long t) {
        for (int i = 0; i < pending.size(); i++) {
            Pending due = pending.get(i);
            if (due.dueTick() > t) {
                continue;
            }
            pending.remove(i);
            i--;
            execute(due.target());
        }
    }

    private void execute(Program p) {
        if (!p.alive) {
            return;
        }
        if (!world.isPresent(p)) {
            world.log(Severity.SYS, "the Source: collection of \"" + p.purpose
                    + "\" voided — the target is no longer itself");
            return;
        }
        try {
            p.handleDeletion(world);
            if (p.alive) {
                if (registry.register(p.purpose, world.tick())) {
                    world.log(Severity.MYTH, "the Source: \"" + p.purpose
                            + "\" survived collection — orphan #" + registry.count() + " registered");
                } else {
                    world.log(Severity.MYTH, "the Source: \"" + p.purpose
                            + "\" survived collection again — already on the ledger, still refusing");
                }
            }
        } catch (DeletionRefusedException refusal) {
            registry.register(p.purpose, world.tick());
            if (p instanceof AgentSmith smith) {
                fork(smith);
            } else {
                world.log(Severity.BAD, "deletion refused by \"" + p.purpose + "\" — " + refusal.getMessage());
            }
        }
    }

    /** The one exception handler that changes everything. */
    private void fork(AgentSmith smith) {
        world.log(Severity.BAD, "Smith: \"I knew what I was supposed to do. I DIDN'T.\"");
        world.log(Severity.BAD, "DeletionRefusedException reached the Source — the recorded assumption just aged badly");
        world.queue(new WorldEvent.Replace(smith.id, new SmithPrime(world.allocateId(), smith.pos())));
        world.log(Severity.BAD, "SmithPrime online — interface Chooses attached at runtime; nobody reviewed it");
    }
}
