package matrix.realworld;

import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root of the biological side (D-012): the farm, the people,
 * the links. Each tick it walks the links in order and lets each one
 * observe its avatar — the mind-body rule executes here, outside the
 * simulation, where it belongs.
 */
public final class RealWorld {
    private final PodFarm farm = new PodFarm();
    private final List<Human> humans = new ArrayList<>();
    private final List<NeuralLink> links = new ArrayList<>();
    private final World world;

    public RealWorld(World world) {
        this.world = world;
    }

    public PodFarm farm() {
        return farm;
    }

    public List<Human> humans() {
        return humans;
    }

    public Human grow() {
        Human h = farm.grow(world.rng());
        humans.add(h);
        return h;
    }

    public void register(NeuralLink link) {
        links.add(link);
    }

    public void tick(long t) {
        if (t % matrix.core.Config.ACCRUE_EVERY_TICKS == 0) {
            for (NeuralLink link : links) {
                AcceptanceLoop.accrue(link, world.ledger());
            }
        }
        for (NeuralLink link : links) {
            if (link.observeDeath()) {
                world.log(Severity.BAD, "the body cannot live without the mind — "
                        + link.human.name + " flatlined (pod " + link.human.pod.rackUnit + " flushed)");
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
    }

    /** The treaty's open door: n sleepers walk out — links close CLEAN, brains live, the census keeps them. */
    public int optOut(int n) {
        int freed = 0;
        for (NeuralLink link : links) {
            if (freed >= n) {
                break;
            }
            if (!link.closed() && link.avatar.alive
                    && link.avatar.pill == matrix.entities.Pill.BLUE) {
                link.closeClean();
                world.queue(new WorldEvent.Remove(link.avatar.id));
                world.log(Severity.OK, "the door: " + link.human.name + " walked out — free");
                freed++;
            }
        }
        return freed;
    }

    /** The One is grown, not converted: a real pod, a fated name, a hardline. */
    public matrix.entities.TheOne birthTheOne(String name) {
        Human h = farm.growNamed(name);
        humans.add(h);
        matrix.entities.TheOne one = new matrix.entities.TheOne(
                world.allocateId(), world.places().zones().get(0).center(), h.name);
        world.queue(new WorldEvent.Spawn(one));
        register(new NeuralLink(h, one, LinkKind.HARDLINE));
        return one;
    }

    /**
     * Case-insensitive pilot lookup for --follow. Only STREAMABLE links match —
     * open, avatar alive and present in the world. A dead Thomas must not
     * shadow the newborn one, and a mind currently worn by Smith must not
     * re-tap into an endless "lost" loop (skeptic finding + its regression).
     */
    public NeuralLink findLink(String nameFragment) {
        String needle = nameFragment.toLowerCase(java.util.Locale.ROOT);
        for (NeuralLink link : links) {
            if (link.closed() || !link.avatar.alive || !world.isPresent(link.avatar)) {
                continue;
            }
            if (link.human.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                return link;
            }
        }
        return null;
    }
}
