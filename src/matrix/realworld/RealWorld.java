package matrix.realworld;

import matrix.core.Config;
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

    /**
     * One freed mind's petition account — D-033 read backwards, per the #216
     * addendum: acceptance running NEGATIVE accrues toward the inward door
     * exactly as disbelief accrues toward the outward one. Two doors, one
     * loop, opposite signs. Walked in liberation order; {@code living}
     * mirrors the brain so a death is seen exactly once, and {@code filed}
     * makes a petition a one-time act — a mind asks to go back once, and
     * then the answer is somebody else's (D-046 step two).
     */
    private static final class Petition {
        final Human mind;
        long account;
        boolean living = true;
        boolean filed;

        Petition(Human mind) {
            this.mind = mind;
        }
    }

    private final PodFarm farm = new PodFarm();
    private final List<Human> humans = new ArrayList<>();
    private final List<NeuralLink> links = new ArrayList<>();
    private final List<Liberation> pendingLiberations = new ArrayList<>();
    /** Petition accounts, one per freed mind, in liberation order — the census lane of D-046's open point (a). */
    private final List<Petition> petitions = new ArrayList<>();
    /** Filed petitions waiting for the far bank: names only, the scalar the bridge carries (A1). */
    private final List<String> pendingPetitions = new ArrayList<>();
    private final World world;
    private long selfsubCount = 0;
    private long petitionCount = 0;

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
                        + h.name + " flatlined" + Pod.flushClause(h));
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
     *
     * <p>The pod clause comes from {@link Pod#opensClause} (#813, moved to
     * the slot it describes by #849). It used to be written here, inline and
     * unguarded, in a door built expressly for a citizen who may have no pod:
     * the first free-born mind to walk out would have died inside its own
     * FATE line.
     */
    private void selfSubstantiate(NeuralLink link) {
        link.closeClean();
        world.queue(new WorldEvent.Remove(link.avatar.id));
        selfsubCount++;
        world.log(Severity.FATE, "self-substantiation: " + link.human.name
                + " walked out of the dream — residue " + link.personalResidue
                + " >= threshold " + AcceptanceLoop.threshold(link.human.name) + ", " + link.spikes
                + " spikes in " + link.windows + " windows; no red pill was given "
                + Pod.opensClause(link.human));
        bank(new Liberation(link.human, "selfsub"));
    }

    /**
     * One walk out, banked on both books at once: the handoff queue the root
     * drains into the census, and the petition account this mind now holds.
     * Every freed mind gets an account the moment it is free — the door's
     * inward direction is not a second population, it is the SAME population
     * read with the opposite sign.
     */
    private void bank(Liberation freed) {
        pendingLiberations.add(freed);
        petitions.add(new Petition(freed.human()));
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
                bank(new Liberation(link.human, "treaty"));
                freed++;
            }
        }
        return freed;
    }

    /**
     * Step one of the inward door (D-046, #335): the petition, filed.
     *
     * <p>Nobody scripts Cypher. Every freed mind carries a propensity that
     * is a pure function of its NAME — the D-033/KID precedent, mirrored:
     * fate was always in the name, and the rng stream never hears about it
     * (D-010: this method draws nothing). Real-world hardship presses on it.
     * The griefs are the ones the real side already emits and already means:
     * a citizen's brain going dark, whatever killed it — a hull sunk with
     * crew aboard, a wire cut on a slow exit, a trace completed. Each death
     * spikes the account of every living freed mind by
     * {@link Config#PETITION_GRIEF_SPIKE}. The petition is CAUSED, not
     * sampled.
     *
     * <p>Eligibility is D-046's open point (a), and this unit answers only
     * the half the gate settled: the CENSUS lane. The root offers the city's
     * ashore roster — alive, no wire, no berth — and nobody else may file.
     * A runner in-world and a crew mid-mission keep their accounts (grief
     * reaches everyone) and simply cannot walk to the door yet; that lane
     * waits for its own verdict rather than being decided by an accident of
     * implementation.
     *
     * <p>Filing is a one-time act per mind and speaks one line. What the
     * petition is WORTH is not decided here and cannot be: the answer lives
     * on the far side of the bridge (A1), and only a name crosses.
     *
     * @param ashore the census's own gate — identity membership, never equals
     */
    public void doorTick(List<Human> ashore) {
        int griefs = 0;
        for (Petition p : petitions) {
            if (p.living && !p.mind.alive()) {
                p.living = false;
                griefs++;
            }
        }
        if (griefs > 0) {
            long spike = griefs * Config.PETITION_GRIEF_SPIKE;
            for (Petition p : petitions) {
                if (p.living) {
                    p.account += spike;
                }
            }
        }
        for (Petition p : petitions) {
            if (p.filed || !p.living
                    || p.account < petitionThreshold(p.mind.name)
                    || !holds(ashore, p.mind)) {
                continue;
            }
            p.filed = true;
            petitionCount++;
            pendingPetitions.add(p.mind.name);
            world.log(Severity.FATE, "DOOR petition: " + p.mind.name + " asks to go back");
        }
    }

    /**
     * The breaking point of the inward door, derived from the name alone —
     * "some minds simply price steak above truth", and which minds those are
     * was decided at the pod, not at the desert.
     *
     * <p>The salt and the mix are not decoration. {@link AcceptanceLoop#threshold}
     * reads the same {@code String.hashCode} for the OUTWARD door; taking
     * this one straight off it would make the two fates one die read twice —
     * every mind fated to disbelieve early would be fated to recant early,
     * and the correlation would be an artifact of arithmetic rather than a
     * claim anyone made. The murmur3 finalizer (the #96 avalanche fix, same
     * reason) decorrelates them. {@code String.hashCode} is fixed by the
     * JLS, so the fate is the same on every JVM — D-010 tier one, pinned
     * rather than trusted by {@code probes/SealHygiene}, which checks both
     * doors over the same six names so a JLS deviation names which one moved.
     */
    public static long petitionThreshold(String name) {
        int h = name.hashCode() ^ 0x0D00_0046;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return Config.PETITION_BASE + Math.floorMod(h, Config.PETITION_JITTER);
    }

    /** Identity membership, not equals — names are not unique, and the roster holds the very objects the census walks. */
    private static boolean holds(List<Human> roster, Human mind) {
        for (Human h : roster) {
            if (h == mind) {
                return true;
            }
        }
        return false;
    }

    /**
     * The petitions filed since the last drain, in filing order — names, and
     * nothing but names. This is the whole bridge: {@code machine} decides
     * WHETHER on a scalar it can read and a type it has never seen (A1), and
     * the door's two halves stay strangers. Empty drains allocate nothing.
     */
    public List<String> drainPetitions() {
        if (pendingPetitions.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(pendingPetitions);
        pendingPetitions.clear();
        return out;
    }

    /** Monotone count of petitions ever filed — the door's inward pressure, for the instruments. */
    public long petitionCount() {
        return petitionCount;
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

    /**
     * The One's birth, both halves (#847): the avatar the world holds, and
     * the mind it was grown for. The caller needs both and they are two
     * objects — the world spawns the avatar, and the birth record states the
     * mind, whose rack unit and growth ordinal are inputs the die keys to.
     *
     * <p>Handing back only the avatar and letting the recorder find the mind
     * afterwards would mean finding it by name, and a name is a search key in
     * this class and never a binding ({@link #findLink}): 196 minds wear 154
     * names at seed 42, and the record that says who came to exist must not
     * be resolved through a string two of them could answer to.
     */
    public record OneBorn(matrix.entities.TheOne avatar, Human pilot) {}

    /** The One is grown, not converted: a real pod, a fated name, a hardline. */
    public OneBorn birthTheOne(String name) {
        Human h = farm.growNamed(name);
        humans.add(h);
        matrix.entities.TheOne one = new matrix.entities.TheOne(
                world.allocateId(), world.places().zones().get(0).center(), h.name);
        world.queue(new WorldEvent.Spawn(one));
        register(new NeuralLink(h, one, LinkKind.HARDLINE));
        return new OneBorn(one, h);
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
