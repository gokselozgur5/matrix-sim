import matrix.Simulation;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.realworld.AcceptanceLoop;
import matrix.realworld.Brain;
import matrix.realworld.Human;
import matrix.realworld.LinkKind;
import matrix.realworld.NeuralLink;
import matrix.realworld.Pod;
import matrix.realworld.RealWorld;
import matrix.zion.BroadcastRig;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: crown #50's {@code pod} 0..1 ruling, read back out of a running
 * universe — the reader #813 said the invariant did not have.
 *
 * Every guard around {@code Human.pod} protects a branch no shipped run
 * reaches: {@code PodFarm.grow} assigns a {@code Pod} unconditionally, so the
 * podless half of the tree is dead code until #121's free-born Kid. The
 * PODCLAIM line below measures exactly that and is a REPORT, not a verdict —
 * when the Kid lands and {@code podless} stops being 0, nothing here fails.
 * What fails is a call site that forgot the guard.
 *
 * {@code PirateSever} already builds podless subjects, and #813's own finding
 * is that this is not enough: it exercises {@code NeuralLink} in a vise, so it
 * cannot see that a DIFFERENT caller dereferenced the field bare — which is
 * how D-033's line sat unguarded at {@code RealWorld:88} through a sweep that
 * had honestly passed. This probe drives the four ENDINGS instead, each
 * through the object that owns it, inside a real {@code Simulation}:
 *
 * <ol>
 * <li>the Kid's door — {@code RealWorld.selfSubstantiate}, reached by the
 *     daemon's own accrual window rather than called;
 * <li>the treaty's door — {@code RealWorld.optOut};
 * <li>the wire's death on a rig channel — {@code NeuralLink.observeDeath}
 *     through {@code BroadcastRig.watch};
 * <li>the unclean cut — {@code NeuralLink.severUnclean} through
 *     {@code BroadcastRig.destroy}.
 * </ol>
 *
 * Each ending runs TWICE, over a podless mind and a racked one, because a
 * clause stuck on either branch would pass a one-sided test. The assertions
 * are written out longhand here rather than taken from {@link Pod} — a probe
 * that asks the code under test what it should have said proves nothing. Only
 * the load-bearing half of each sentence is pinned: the racked side must NAME
 * the rack unit, the free-born side must say there is none. Wording around
 * that may move without breaking the lock.
 *
 * The last ending carries the assertion nothing else in the tree makes:
 * {@code untouchedClause} claims the cut leaves the rack as it stood, so the
 * probe reads {@code Pod.occupied()} afterwards and holds the sentence to it.
 *
 * Usage: java -cp out:probes/out PodOptional [ticks] [seed]
 */
public final class PodOptional {

    private static int scenarios = 0;
    private static int anomalies = 0;

    /**
     * The birth a mind this probe builds by hand declares for itself (#847,
     * then #764). These eight subjects stand inside a real universe that has
     * already grown its own people with ordinals of their own, so the vise
     * takes a number past the farm's range rather than one that would read as
     * a claim about somebody the world grew; the seed and the tick are zero
     * for the same reason. Since #764 the constructor mixes all three into
     * {@code birthKey}, so a hand-built mind carries a fate like any other —
     * and the door test below stands its residue at THAT mind's threshold,
     * read off its own key, instead of at a bar computed from its name.
     */
    private static final int VISE_ORDINAL = 100_000;
    private static final long VISE_SEED = 0, VISE_TICK = 0;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        census(seed, ticks);

        selfsub("Mouse", null, "no pod to open");
        selfsub("Tank", new Pod("R99/U01"), "pod R99/U01 opens");

        treaty("Dozer", null);
        treaty("Apoc", new Pod("R99/U02"));

        rigDeath("Switch", null, "no pod to flush");
        rigDeath("Cypher", new Pod("R99/U03"), "pod R99/U03 flushed");

        rigCut("Niobe", null, "no rack unit behind them");
        rigCut("Ghost", new Pod("R99/U04"), "pod R99/U04 untouched");

        System.out.println("PODOPTIONAL scenarios=" + scenarios + " anomalies=" + anomalies);
        Probes.leave(anomalies == 0
                ? "VERDICT POD_OPTIONAL_HELD" : "VERDICT POD_OPTIONAL_BROKEN", anomalies == 0);
    }

    /**
     * The premise, measured rather than asserted: how many citizens the
     * canonical film grows without a rack slot. The answer is 0 and has
     * always been 0, which is why every scenario below builds its subject by
     * hand — and why the guards were able to rot unnoticed.
     */
    private static void census(long seed, long ticks) throws Exception {
        Simulation sim = new Simulation(seed, null, null);
        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
        }
        RealWorld rw = Probes.realWorld(sim);
        matrix.zion.Zion zion = Probes.zion(sim);
        List<Human> all = new ArrayList<>(rw.humans());
        for (Human h : zion.census()) {
            addOnce(all, h);
        }
        for (var ship : Probes.fleet(zion)) {
            for (NeuralLink l : Probes.rigLinks(ship.rig())) {
                addOnce(all, l.human);
            }
        }
        int podless = 0;
        for (Human h : all) {
            if (h.pod == null) {
                podless++;
            }
        }
        System.out.println("PODCLAIM seed=" + seed + " ticks=" + ticks
                + " census=" + all.size()
                + " with_pod=" + (all.size() - podless)
                + " podless=" + podless);
    }

    /**
     * Ending 1 — D-033's door. The subject is registered into the daemon's
     * own link book and its account is stood at its own breaking point; the
     * world's accrual window does the rest, so what opens the door is
     * {@code RealWorld.tick}, not this probe.
     */
    private static void selfsub(String name, Pod pod, String mustSay) throws Exception {
        Universe u = new Universe();
        Human mind = new Human(name, new Brain(name), pod, VISE_ORDINAL, VISE_SEED, VISE_TICK);
        NeuralLink wire = jackIn(u, mind, Pill.BLUE);
        u.realWorld.register(wire);
        standResidueAt(wire, AcceptanceLoop.threshold(mind.birthKey));

        boolean npe = false;
        long before = u.realWorld.selfsubCount();
        try {
            for (int t = 0; t < 40 && !wire.closed(); t++) {
                u.sim.tickOnce();
            }
        } catch (NullPointerException e) {
            npe = true;
        }
        String said = u.lineContaining("self-substantiation: " + name);
        line("SELFSUB " + subject(pod),
                fact("no_npe", !npe),
                fact("door_opened", u.realWorld.selfsubCount() == before + 1),
                fact("closed", wire.closed()),
                fact("brain_lives", mind.alive()),
                fact("says[" + mustSay + "]", said.contains(mustSay)),
                fact("rack_vacated", pod == null || !pod.occupied()));
    }

    /**
     * Ending 2 — the treaty's door, driven over the whole city so the podless
     * mind walks out in the middle of a real mass exit rather than alone. It
     * is the only ending that vacates a rack unit and prints no clause about
     * it; the flag is the only witness, so the flag is what gets read.
     */
    private static void treaty(String name, Pod pod) throws Exception {
        Universe u = new Universe();
        Human mind = new Human(name, new Brain(name), pod, VISE_ORDINAL, VISE_SEED, VISE_TICK);
        NeuralLink wire = jackIn(u, mind, Pill.BLUE);
        u.realWorld.register(wire);

        boolean npe = false;
        int freed = 0;
        try {
            freed = u.realWorld.optOut(Integer.MAX_VALUE);
            u.world.flush();
        } catch (NullPointerException e) {
            npe = true;
        }
        line("TREATY " + subject(pod),
                fact("no_npe", !npe),
                fact("city_walked", freed > 1),
                fact("closed", wire.closed()),
                fact("brain_lives", mind.alive()),
                fact("jack_cleared", mind.link() == null),
                fact("rack_vacated", pod == null || !pod.occupied()));
    }

    /**
     * Ending 3 — the avatar dies on a pirate channel and the rig speaks the
     * flush clause over the body. Zion's book, not RealWorld's: this is the
     * call site #811 caught copying the sentence without the branch.
     */
    private static void rigDeath(String name, Pod pod, String mustSay) throws Exception {
        Universe u = new Universe();
        Human mind = new Human(name, new Brain(name), pod, VISE_ORDINAL, VISE_SEED, VISE_TICK);
        BroadcastRig rig = new BroadcastRig();
        rig.beginSession(u.world, u.world.places().zones().get(0));
        NeuralLink wire = rig.open(mind, u.world);
        u.world.flush();
        wire.avatar.alive = false;

        boolean npe = false;
        try {
            rig.watch(u.world);
        } catch (NullPointerException e) {
            npe = true;
        }
        String said = u.lineContaining("the wire went dark — " + name);
        line("RIGDEATH " + subject(pod),
                fact("no_npe", !npe),
                fact("closed", wire.closed()),
                fact("brain_dead", !mind.alive()),
                fact("says[" + mustSay + "]", said.contains(mustSay)),
                fact("rack_flushed", pod == null || !pod.occupied()));
    }

    /**
     * Ending 4 — the ship dies with the crew still under. The cut takes the
     * mind and not the rack, which is a claim about state and therefore
     * checkable: the sentence says untouched, so the slot must still be
     * occupied when the line has been printed.
     */
    private static void rigCut(String name, Pod pod, String mustSay) throws Exception {
        Universe u = new Universe();
        Human mind = new Human(name, new Brain(name), pod, VISE_ORDINAL, VISE_SEED, VISE_TICK);
        BroadcastRig rig = new BroadcastRig();
        rig.beginSession(u.world, u.world.places().zones().get(0));
        NeuralLink wire = rig.open(mind, u.world);
        u.world.flush();

        boolean npe = false;
        int cut = 0;
        try {
            cut = rig.destroy(u.world);
        } catch (NullPointerException e) {
            npe = true;
        }
        String said = u.lineContaining("the rig dies with " + name);
        line("RIGCUT " + subject(pod),
                fact("no_npe", !npe),
                fact("cut", cut == 1),
                fact("closed", wire.closed()),
                fact("brain_dead", !mind.alive()),
                fact("says[" + mustSay + "]", said.contains(mustSay)),
                fact("rack_untouched", pod == null || pod.occupied()));
    }

    /** One private universe, quiet except for the log this probe reads back. */
    private static final class Universe {
        final Simulation sim;
        final World world;
        final RealWorld realWorld;
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

        Universe() throws Exception {
            sim = new Simulation(42, sink, null);
            world = Probes.world(sim);
            realWorld = Probes.realWorld(sim);
        }

        /** The first logged line carrying a needle, or "" — the probe reads the daemon's own words. */
        String lineContaining(String needle) {
            for (String l : sink.toString(StandardCharsets.UTF_8).split("\n")) {
                if (l.contains(needle)) {
                    return l;
                }
            }
            return "";
        }
    }

    /** A body in the world and a wire to it — the shape {@code Simulation.jackIn} builds, minus the farm. */
    private static NeuralLink jackIn(Universe u, Human mind, Pill pill) {
        Avatar body = new Avatar(u.world.allocateId(),
                u.world.places().zones().get(0).center(), mind.name, pill);
        u.world.queue(new WorldEvent.Spawn(body));
        u.world.flush();
        return new NeuralLink(mind, body, LinkKind.HARDLINE);
    }

    /**
     * The one WRITE on this bench, and it is not a shortcut past the daemon —
     * it is the only way in. The account that opens D-033's door is
     * spike-only and its threshold is a pure function of the name, so of 400
     * growable names exactly one ever clears its own bar and it takes 4,740
     * ticks to do it ({@code FateAtlas}). Standing the account at the mind's
     * own threshold buys the door in one window and costs the probe nothing
     * in honesty: everything after this line is the daemon's.
     */
    private static void standResidueAt(NeuralLink wire, long residue) throws Exception {
        Field f = NeuralLink.class.getDeclaredField("personalResidue");
        f.setAccessible(true);
        f.setLong(wire, residue);
    }

    private static void addOnce(List<Human> roster, Human h) {
        for (Human held : roster) {
            if (held == h) {
                return;
            }
        }
        roster.add(h);
    }

    private static String subject(Pod pod) {
        return pod == null ? "free_born" : "racked";
    }

    /** A fact holds or it counts: prints name=held, tallies the anomaly when it does not. */
    private static String fact(String name, boolean held) {
        if (!held) {
            anomalies++;
        }
        return name + "=" + held;
    }

    private static void line(String prefix, String... facts) {
        scenarios++;
        System.out.println(prefix + " " + String.join(" ", facts));
    }

    private PodOptional() {}
}
