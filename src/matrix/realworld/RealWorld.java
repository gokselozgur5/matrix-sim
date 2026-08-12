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

    /** One banked liberation: who walked, and through which door — "treaty" (the open door) or "selfsub" (D-033). */
    public record Liberation(Human human, String origin) {}

    private final PodFarm farm = new PodFarm();
    private final List<Human> humans = new ArrayList<>();
    private final List<NeuralLink> links = new ArrayList<>();
    private final List<Liberation> pendingLiberations = new ArrayList<>();
    private final World world;
    private long selfsubCount = 0;

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
                if (AcceptanceLoop.accrue(link, world.ledger())
                        && world.isPresent(link.avatar)) {
                    selfSubstantiate(link);
                }
            }
        }
        for (NeuralLink link : links) {
            if (link.observeDeath()) {
                Human h = link.human;
                world.log(Severity.BAD, "the body cannot live without the mind — "
                        + h.name + " flatlined" + (h.pod != null
                        ? " (pod " + h.pod.rackUnit + " flushed)"
                        : " (no pod to flush — they died free)"));
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
    }

    /**
     * The Kid's door (D-033): the crossing the second theorem detected
     * executes here, where the world is — the treaty door's four moves,
     * different cause, own origin tag. The presence gate at the call site
     * defers a wrapped mind's exit: while Smith wears the dream it is not
     * theirs to walk out of; the account holds, and the first window after
     * restore the door opens. Without wraps an open live link's avatar is
     * always present, so the gate costs nothing where it cannot matter.
     */
    private void selfSubstantiate(NeuralLink link) {
        link.closeClean();
        world.queue(new WorldEvent.Remove(link.avatar.id));
        selfsubCount++;
        world.log(Severity.FATE, "self-substantiation: " + link.human.name
                + " walked out of the dream — residue " + link.personalResidue
                + " >= threshold " + AcceptanceLoop.threshold(link.human.name) + ", " + link.spikes
                + " spikes in " + link.windows + " windows; no red pill was given (pod "
                + link.human.pod.rackUnit + " opens)");
        pendingLiberations.add(new Liberation(link.human, "selfsub"));
    }

    /** Monotone count of D-033 walk-outs — the METRIC selfsub= column; the root samples it (D-020, appended grammar). */
    public long selfsubCount() {
        return selfsubCount;
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
                pendingLiberations.add(new Liberation(link.human, "treaty"));
                freed++;
            }
        }
        return freed;
    }

    /**
     * The near bank of the handoff (crown #84): freed Humans wait here, in
     * liberation order, each with the door it came through — the treaty's
     * or the Kid's (#121) — until the root carries them across to Zion's
     * census. This package must never import {@code matrix.zion} (zion
     * already imports realworld) — only the composition root holds both
     * banks (D-012), so it does the carrying. Empty drains allocate nothing.
     */
    public List<Liberation> drainLiberations() {
        if (pendingLiberations.isEmpty()) {
            return List.of();
        }
        List<Liberation> out = new ArrayList<>(pendingLiberations);
        pendingLiberations.clear();
        return out;
    }

    /**
     * The realworld feed of the digest chain (D-033 addendum): the first
     * real-side state to enter it — a framed segment the root appends after
     * the entity walk. Registration order, one tuple per link. What the
     * digest sees here, the v4.5 Snapshot must retain (#179). The census
     * stays outside the chain per the #187 precedent; whether more real-side
     * state gets blessed in is open at the gate (#96, point a).
     */
    public void digestInto(matrix.core.StateSink sink) {
        sink.putCount(links.size());
        for (NeuralLink link : links) {
            sink.putLong(AcceptanceLoop.threshold(link.human.name));
            sink.putLong(link.personalResidue);
        }
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
     * Case-insensitive pilot lookup for --follow — the FIRST resolution and no
     * other (#375). Only STREAMABLE links match: open, avatar alive and present
     * in the world. A dead Thomas must not shadow the newborn one, and a mind
     * currently worn by Smith must not re-tap into an endless "lost" loop
     * (skeptic finding + its regression). The fragment is a search key, never a
     * binding: names are not unique — 196 humans wear 154 of them at seed 42 —
     * so the caller keeps the mind this returns and re-taps through
     * {@link #linkOf}, which cannot resolve to somebody else.
     */
    public NeuralLink findLink(String nameFragment) {
        String needle = nameFragment.toLowerCase(java.util.Locale.ROOT);
        for (NeuralLink link : links) {
            if (!streamable(link)) {
                continue;
            }
            if (link.human.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                return link;
            }
        }
        return null;
    }

    /**
     * The re-tap, bound to one mind (#375): that human's CURRENT link when it
     * is streamable, else null while they are dark. A Human holds at most one
     * link at a time and every jack-in installs the new one, so this resumes
     * the same person across a whole life — walked out through the treaty's
     * door and later riding a pirate signal is the same mind on a new wire,
     * and it resumes; a namesake, who is a different person however the string
     * reads, never can.
     */
    public NeuralLink linkOf(Human mind) {
        return streamable(mind.link()) ? mind.link() : null;
    }

    /**
     * The One is a role, not a name (#375): whoever wears it now. This is the
     * one subject a name-free tap may still re-arm onto, and it is not an
     * exception to the identity rule — it is a different kind of subject. The
     * world keeps the role unique by construction (a birth needs the ledger
     * overflowed AND no living One), so "another One" can never be a stranger
     * who merely shares a string. That is what keeps #107's reborn Thomas
     * resuming while every other name loses the right to re-arm.
     */
    public NeuralLink theOneLink() {
        for (NeuralLink link : links) {
            if (streamable(link) && link.avatar instanceof matrix.entities.TheOne) {
                return link;
            }
        }
        return null;
    }

    /** The tap's one predicate: an open wire, a living avatar, and a world that holds it. */
    private boolean streamable(NeuralLink link) {
        return link != null && !link.closed() && link.avatar.alive && world.isPresent(link.avatar);
    }
}
