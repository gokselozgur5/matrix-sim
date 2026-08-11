package matrix;

import matrix.core.ChronosLog;
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
import matrix.core.Snapshot;
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
    private final Zion zion;
    private final Source source;
    private final matrix.machine.SubstrateBudget substrate;
    private final Director director;
    private final List<SystemNode> nodes;
    private final MetricsCollector metrics;
    private final DigestCalculator digests = new DigestCalculator();
    private final List<Digest> chain = new ArrayList<>();
    private final PrintStream out;
    private final ChronosLog chronos;
    private final String followName;
    private NeuralLink followed;
    private int chronosVersionSeen;
    private int agentsSpawned = 0;
    private int patchesDeployed = 0;
    private boolean optOutDone = false;

    public Simulation(long seed, OutputStream sink, String followName) {
        this(seed, sink, followName, null);
    }

    /** The four-arg root: a non-null chronosSink turns the D-023 stage-1 recorder on. */
    public Simulation(long seed, OutputStream sink, String followName, OutputStream chronosSink) {
        this.rng = new Rng(seed);
        this.out = sink == null ? null : new PrintStream(sink, true, StandardCharsets.UTF_8);
        this.followName = followName;
        PlaceGraph places = new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM);
        this.world = new World(rng, bus, places);
        this.realWorld = new RealWorld(world);
        this.zion = new Zion(world);
        this.source = new Source(world);
        this.metrics = new MetricsCollector(world);
        if (this.out != null) {
            EventLog log = new EventLog(this.out);
            bus.subscribe(log::onEvent);
        }
        this.chronos = chronosSink == null ? null : new ChronosLog(chronosSink);
        this.chronosVersionSeen = world.version();
        if (chronos != null) {
            // genesis first — before the boot flush, before tick 1 (crown #177)
            chronos.genesis(seed, world.version());
            world.installChronosTap(chronos);
        }
        AgentSmith smith = seedPopulation();
        this.director = new Director(world, source, smith);
        // D-008 (crowns #32/#124): under PROCESSOR the machine wing gets a
        // budget, fed through a NAMED port — one scalar, wired here because
        // only the root holds both banks (D-012); under BATTERY it is absent
        // and the whole substrate costs nothing.
        this.substrate = Config.COMPUTE_MODEL.coupled()
                ? new matrix.machine.SubstrateBudget(places.zones().size())
                : null;
        java.util.function.IntSupplier pluggedPods = realWorld.farm()::occupiedCount;
        // Canonical node order (D-031, crown #122): machine, realworld, zion —
        // zion LAST, so liberations queued this tick are absorbed this tick.
        // The third node is the fence event: nodes.add, addition not refactor.
        this.nodes = List.of(
                new MachineSystem(world, director, source, substrate, pluggedPods),
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
            // The homecoming dial (#136): caps multiply, spawn order does not
            // change — at scale 1 this loop is bit-for-bit the canonical one.
            for (int i = 0; i < species.populationCap() * Config.ECO_SCALE; i++) {
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
        world.log(Severity.SYS, "compute model: " + Config.COMPUTE_MODEL.name()
                + " — " + Config.COMPUTE_MODEL.desc());
        world.log(Severity.SYS, "program society online: the Oracle and "
                + Config.EXILE_COUNT + " exiles walk among the sleepers");
        world.log(Severity.SYS, "ecosystem online: " + matrix.entities.eco.Bestiary.ALL.size()
                + " species rendered — a healthy program is invisible"
                + (Config.ECO_SCALE == 1 ? ""
                        : " (homecoming x" + Config.ECO_SCALE + ": every population multiplied)"));
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

    /**
     * Ops console: force one awakening. Even overrides respect the cap —
     * and count it the way the Director does since the v3 fix: latent
     * (wrapped) reds included, or wrapped minds convert into grantable
     * slots and the treaty restores the surplus (#206, H2).
     */
    public void commandRed() {
        if (world.countRedIncludingWrapped() >= Config.RED_CAP) {
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

    /**
     * Ops console: the Architect's old answer, on demand — replay-shaped
     * since stage 4 (#128). The epoch closes on the record BEFORE the
     * purge touches the world: seal first (the boundary Snapshot, written
     * as an epoch marker — the crown's {@code ChronosLog o-- Snapshot}
     * edge), then the boundary line, then the Architect's surgery.
     *
     * Stage-5 invariant (#129), held by construction, not discipline:
     * the seal is {@link #snapshotNow()} taken at the dispatch point —
     * between ticks, exactly where the fold stands when it re-applies
     * the recorded command — so recorder and fold walk the SAME state
     * through the SAME sink grammar, and the post-purge digest is the
     * first link of the new epoch. The record leads, the world follows;
     * agreement is structural, and divergence is a chain verdict.
     */
    public void commandReload() {
        if (chronos != null) {
            chronos.snapshot(snapshotNow());
            chronos.boundary(world.tick(), "reload");
        }
        matrix.machine.Architect.INSTANCE.reload(world, false);
        world.ledger().reset();
        director.abortPeace();
        // the boundary is already on the record — sync the version so the
        // mid-tick detector stays quiet; it still owns emergency and treaty
        chronosVersionSeen = world.version();
    }

    /** Chronos: an operator command enters the record at the tick it lands on. */
    public void recordCommand(String cmd) {
        if (chronos != null) {
            chronos.command(world.tick(), cmd);
        }
    }

    /**
     * Chronos boundary detection for MID-TICK crossings: the emergency
     * reload (the Director's overflow playbook) and the treaty bump the
     * version inside a node's tick, where only this post-tick sweep can
     * see them — a version crossing plus the resulting state names the
     * boundary without instrumenting the machine package. The console
     * reload no longer reaches here: it seals and writes its own boundary
     * BEFORE the purge (stage 4, #128) and syncs the version seen.
     * Mid-tick boundaries stand alone on the record — unsealed by design:
     * the root cannot stand inside a tick, and in the coarse+seeded model
     * re-execution regenerates them; the chain referees. Reads only; with
     * chronos off it is a no-op.
     */
    private void chronosBoundary() {
        if (chronos == null) {
            return;
        }
        int v = world.version();
        if (v != chronosVersionSeen) {
            chronosVersionSeen = v;
            chronos.boundary(world.tick(),
                    world.state() == matrix.core.SystemState.PEACE ? "treaty" : "reload");
        }
    }

    /**
     * Ops console: scuttle the active ship (#119). Operator-driven and
     * deterministic exactly like reload — except the loss executes in the
     * NEXT zion tick's canonical slot, so the cascade lands in tick order,
     * never between batches.
     */
    public void commandSink() {
        zion.orderSink();
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
        chronosBoundary();
        long t = world.tick();
        if (world.state() == matrix.core.SystemState.PEACE && !optOutDone) {
            optOutDone = true;
            int freed = realWorld.optOut(Config.OPTOUT_COUNT);
            world.flush();
            world.log(Severity.OK, "open door tally: " + freed + " walked out; the census keeps them");
        }
        // The handoff (crown #84): only the root holds both banks (D-012), so
        // only the root carries freed Humans across — every tick, in link
        // registration order. Each liberation carries its own door: the
        // treaty's, or the Kid's selfsub tag (#121, D-033).
        for (RealWorld.Liberation freed : realWorld.drainLiberations()) {
            zion.absorb(freed.human(), freed.origin());
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
            emit(metrics.sample(t, realWorld.selfsubCount()).format());
        }
        if (t % Config.ECO_EVERY_TICKS == 0) {
            emit(metrics.ecoLine(t));
        }
        if (t % Config.ATTN_EVERY_TICKS == 0) {
            emit(metrics.attnLine(t));
        }
        if (substrate != null && t % Config.METRIC_EVERY_TICKS == 0) {
            // D-008 (#134): the machine wing's own instrument, right after
            // the attention census it rations. The budget formats, the
            // map's glitch count rides along, only the root emits (D-020).
            emit(substrate.line(world.regions().capGlitches()));
        }
        if (t % Config.ZION_EVERY_TICKS == 0) {
            // #118: the root hands zion's open links to the collector (D-012) and
            // the trace suffix rides the ZION line — present exactly when links>0.
            emit(zion.zionLine(t) + metrics.traceSuffix(zion.openPirateAvatars()));
        }
        if (t % Config.DIGEST_EVERY_TICKS == 0) {
            world.digestInto(digests);
            // The D-033 addendum's framed segment rides AFTER the entity walk:
            // only the root holds both banks (D-012), so only the root can
            // feed both worlds to one referee.
            realWorld.digestInto(digests);
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

    /**
     * D-023 stage 3: the retained walk of the state as it stands — the
     * same digestInto walk the chain hashes, bytes kept (crown #179).
     * Reads only; taking a snapshot moves nothing and draws nothing.
     */
    public Snapshot snapshotNow() {
        return Snapshot.of(world, realWorld::digestInto);
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
