package matrix;

import matrix.core.Config;
import matrix.core.Digest;
import matrix.core.DigestCalculator;
import matrix.core.Director;
import matrix.core.EventBus;
import matrix.core.EventLog;
import matrix.core.MetricsCollector;
import matrix.core.PlaceGraph;
import matrix.core.Position;
import matrix.core.Rng;
import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.Agent;
import matrix.entities.AgentSmith;
import matrix.entities.Avatar;
import matrix.entities.ExileKind;
import matrix.entities.ExileProgram;
import matrix.entities.Oracle;
import matrix.entities.Pill;
import matrix.machine.Source;
import matrix.realworld.Human;
import matrix.realworld.LinkKind;
import matrix.realworld.NeuralLink;
import matrix.realworld.PerceptionFrame;
import matrix.realworld.RealWorld;
import matrix.zion.Zion;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The universe: the only composition root (D-012). Owns both sides and the
 * clock; ticks its SystemNodes in canonical order (D-031); speaks through
 * the three instruments of D-020. All output lines end with an explicit \n.
 */
public final class Simulation {
    private static final String[] AGENT_NAMES = {
            "Smith", "Brown", "Jones", "Johnson", "Thompson", "Jackson", "Davis", "White"};
    private static final String[] PATCH_NOTES = {
            "corridor tuning parameters updated",
            "MOVED occlusion guard enabled",
            "MotionGate host tests green — promoted to live",
            "sky shader v6.0.3 deployed",
            "cat.render hotfix — expect replay glitches",
            "spoon removed (there was none)"};

    private final Rng rng;
    private final EventBus bus = new EventBus();
    private final World world;
    private final RealWorld realWorld;
    private final Zion zion = new Zion();
    private final Source source;
    private final Director director;
    private final List<SystemNode> nodes;
    private final MetricsCollector metrics;
    private final DigestCalculator digests = new DigestCalculator();
    private final List<Digest> chain = new ArrayList<>();
    private final PrintStream out;
    private final String followName;
    private NeuralLink followed;
    private int agentsSpawned = 0;
    private int patchesDeployed = 0;
    private boolean optOutDone = false;

    public Simulation(long seed, OutputStream sink, String followName) {
        this.rng = new Rng(seed);
        this.out = sink == null ? null : new PrintStream(sink, true, StandardCharsets.UTF_8);
        this.followName = followName;
        PlaceGraph places = new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM);
        this.world = new World(rng, bus, places);
        this.realWorld = new RealWorld(world);
        this.source = new Source(world);
        this.metrics = new MetricsCollector(world);
        if (this.out != null) {
            EventLog log = new EventLog(this.out);
            bus.subscribe(log::onEvent);
        }
        AgentSmith smith = seedPopulation();
        this.director = new Director(world, source, smith);
        // Canonical node order (D-031, crown #122): machine, realworld, zion —
        // zion LAST, so liberations queued this tick are absorbed this tick.
        // The third node is the fence event: nodes.add, addition not refactor.
        this.nodes = List.of(
                new MachineSystem(world, director, source),
                new RealWorldSystem(realWorld),
                new ZionSystem(zion));
        world.flush();
        if (followName != null) {
            followed = realWorld.findLink(followName);
        }
        bootBanner(seed);
    }

    /**
     * Boot order is canon: citizens, then the Oracle (she must subscribe
     * BEFORE the first publish — the bus seals, by law), then the named
     * agent, the daemons, the exiles.
     */
    private AgentSmith seedPopulation() {
        for (int i = 0; i < Config.BLUE_START; i++) {
            jackIn(Pill.BLUE);
        }
        for (int i = 0; i < Config.RED_START; i++) {
            jackIn(Pill.RED);
        }
        world.queue(new WorldEvent.Spawn(
                new Oracle(world.allocateId(), world.places().zones().get(0).center(), bus)));
        AgentSmith smith = new AgentSmith(world.allocateId(), randomPosition());
        agentsSpawned = 1;
        world.queue(new WorldEvent.Spawn(smith));
        for (int i = 1; i < Config.AGENT_START; i++) {
            spawnAgent();
        }
        ExileKind[] kinds = ExileKind.values();
        for (int i = 0; i < Config.EXILE_COUNT; i++) {
            world.queue(new WorldEvent.Spawn(
                    new ExileProgram(world.allocateId(), randomPosition(), kinds[i % kinds.length])));
        }
        for (matrix.entities.eco.Species species : matrix.entities.eco.Bestiary.ALL) {
            for (int i = 0; i < species.populationCap(); i++) {
                world.queue(new WorldEvent.Spawn(new matrix.entities.eco.EnvironmentProgram(
                        world.allocateId(), randomPosition(), species)));
            }
        }
        return smith;
    }

    private void bootBanner(long seed) {
        world.log(Severity.FATE, "MATRIX v6.0 boot — seed " + seed + ", "
                + realWorld.farm().occupiedCount() + " nodes plugged, city "
                + (Config.WORLD_W_CM / 100_000.0) + " km x " + (Config.WORLD_H_CM / 100_000.0) + " km");
        world.log(Severity.SYS, "exit nodes online: " + world.places().exits().size()
                + " phone booths across " + world.places().zones().size() + " zones");
        world.log(Severity.SYS, "compute model: PROCESSOR — the inmates render their own cells");
        world.log(Severity.SYS, "program society online: the Oracle and "
                + Config.EXILE_COUNT + " exiles walk among the sleepers");
        world.log(Severity.SYS, "ecosystem online: " + matrix.entities.eco.Bestiary.ALL.size()
                + " species rendered — a healthy program is invisible");
        if (followName != null) {
            world.log(Severity.SYS, followed == null
                    ? "follow: no pilot matches '" + followName + "'"
                    : "follow: streaming the dream of " + followed.human.name + " as JSONL");
        }
    }

    private void jackIn(Pill pill) {
        Human h = realWorld.grow();
        Avatar avatar = new Avatar(world.allocateId(), randomPosition(), h.name, pill);
        world.queue(new WorldEvent.Spawn(avatar));
        realWorld.register(new NeuralLink(h, avatar, LinkKind.HARDLINE));
    }

    private Position randomPosition() {
        return new Position(rng.nextInt(Config.WORLD_W_CM + 1), rng.nextInt(Config.WORLD_H_CM + 1));
    }

    /** Ops console: force one awakening. Even overrides respect the cap (skeptic finding). */
    public void commandRed() {
        if (world.count(Pill.RED) >= Config.RED_CAP) {
            world.log(Severity.SYS, "manual override refused: the city cannot hold more awakened (cap "
                    + Config.RED_CAP + ")");
            return;
        }
        List<Avatar> blues = world.aliveAvatars(Pill.BLUE);
        if (blues.isEmpty()) {
            world.log(Severity.SYS, "manual override failed: nobody left to wake");
            return;
        }
        Avatar chosen = blues.get(rng.nextInt(blues.size()));
        chosen.pill = Pill.RED;
        world.log(Severity.OK, "manual override: red pill administered to " + chosen.pilotName);
    }

    /** Ops console: deploy one more IDS daemon. */
    public void commandAgent() {
        spawnAgent();
        world.flush();
    }

    /** Ops console: deprecate Smith ahead of schedule. Handle with care. */
    public void commandSmith() {
        director.orderSmithCollection("manual override");
    }

    /** Ops console: the Architect's old answer, on demand. */
    public void commandReload() {
        matrix.machine.Architect.INSTANCE.reload(world, false);
        world.ledger().reset();
    }

    private boolean oneExists() {
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.TheOne) {
                return true;
            }
        }
        return false;
    }

    /** Ops console: hot patch — the users call it déjà vu, and the ledger notices (D-022). */
    public void commandDeja() {
        String note = PATCH_NOTES[patchesDeployed % PATCH_NOTES.length];
        patchesDeployed++;
        world.ledger().accrue(Config.DEJA_RESIDUE_SPIKE);
        world.log(Severity.FATE, "déjà vu — hot patch deployed: " + note);
        world.log(Severity.SYS, "a black cat walks by twice; nobody screams — but "
                + Config.DEJA_RESIDUE_SPIKE + " residue lands on the ledger");
    }

    private void spawnAgent() {
        String codename = AGENT_NAMES[agentsSpawned % AGENT_NAMES.length];
        agentsSpawned++;
        world.queue(new WorldEvent.Spawn(new Agent(world.allocateId(), randomPosition(), codename)));
        world.log(Severity.SYS, "IDS daemon deployed: agent " + codename);
    }

    public void tickOnce() {
        for (SystemNode node : nodes) {
            node.tick(world.tick() + 1);
        }
        long t = world.tick();
        if (world.state() == matrix.core.SystemState.PEACE && !optOutDone) {
            optOutDone = true;
            int freed = realWorld.optOut(Config.OPTOUT_COUNT);
            world.flush();
            world.log(Severity.OK, "open door tally: " + freed + " walked out; the census keeps them");
        }
        // The handoff (crown #84): only the root holds both banks (D-012), so
        // only the root carries freed Humans across — every tick, in link
        // registration order. Today every pending liberation is the treaty's.
        for (Human freed : realWorld.drainLiberations()) {
            zion.absorb(freed, "treaty");
        }
        if (world.state() == matrix.core.SystemState.NORMAL
                && world.ledger().overflowed() && !oneExists()) {
            matrix.entities.TheOne one = realWorld.birthTheOne("Thomas A. Anderson");
            world.flush();
            world.log(Severity.FATE, "The One is born — " + one.pilotName
                    + ", grown for a debt of " + world.ledger().balance()
                    + " (the ledger does not forgive; it balances)");
        }
        if (t % Config.METRIC_EVERY_TICKS == 0) {
            emit(metrics.sample(t).format());
        }
        if (t % Config.ECO_EVERY_TICKS == 0) {
            emit(metrics.ecoLine(t));
        }
        if (t % Config.ZION_EVERY_TICKS == 0) {
            emit(zion.zionLine(t));
        }
        if (t % Config.DIGEST_EVERY_TICKS == 0) {
            world.digestInto(digests);
            Digest d = new Digest(t, digests.finishHex());
            chain.add(d);
            emit(d.format());
        }
        if (followName != null && t % Config.FOLLOW_EVERY_TICKS == 0) {
            // One rule, no special cases: a dark stream re-taps any LIVE link matching
            // the name — a reborn Thomas resumes, a walked-out or eaten pilot does not.
            if (followed == null) {
                followed = realWorld.findLink(followName);
            }
            if (followed == null) {
                // still dark; nothing to say
            } else if (followed.avatar.alive && world.isPresent(followed.avatar)) {
                emit(PerceptionFrame.jsonl(t, followed.avatar, world));
            } else {
                // The LINK tells liberation from loss: only closeClean leaves it closed with
                // a living avatar. A hijacked mind is "lost" — the dream is Smith's now.
                emit("{\"tick\":" + t + ",\"who\":\"" + followed.human.name
                        + (followed.closed() && followed.avatar.alive
                                ? "\",\"signal\":\"ended — they walked out the open door\"}"
                                : "\",\"signal\":\"lost — the dream is no longer theirs\"}"));
                followed = null;
            }
        }
    }

    public List<Digest> run(long ticks) {
        for (long i = 0; i < ticks; i++) {
            tickOnce();
        }
        return chain;
    }

    public long tick() {
        return world.tick();
    }

    public int aliveEntities() {
        return world.countAlive();
    }

    private void emit(String line) {
        if (out != null) {
            out.print(line + "\n");
        }
    }
}
