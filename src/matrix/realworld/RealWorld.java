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
        for (NeuralLink link : links) {
            if (link.observeDeath()) {
                world.log(Severity.BAD, "the body cannot live without the mind — "
                        + link.human.name + " flatlined (pod " + link.human.pod.rackUnit + " flushed)");
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
    }

    /** Case-insensitive pilot lookup for --follow. */
    public NeuralLink findLink(String nameFragment) {
        String needle = nameFragment.toLowerCase(java.util.Locale.ROOT);
        for (NeuralLink link : links) {
            if (link.human.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                return link;
            }
        }
        return null;
    }
}
