package matrix;

import matrix.core.ChronosLog;
import matrix.core.Config;
import matrix.core.Digest;
import matrix.core.DigestCalculator;
import matrix.core.Director;
import matrix.core.District;
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
import matrix.causal.CausalPhase;
import matrix.causal.TruthSnapshot;
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
    // Read-only: the roster the world draws agent names from, never written after the
    // class loads (#1148).
    private static final String[] AGENT_NAMES = {
            "Smith", "Brown", "Jones", "Johnson", "Thompson", "Jackson", "Davis", "White"};
    // Read-only, same as the roster above it (#1148).
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
    /**
     * The heart's book (D-045): mind-to-mind edges, real-side by law. Held
     * beside the biological bank rather than inside it — {@code RealWorld}
     * is another crew's file this wave — and handed to the realworld node,
     * which is the only thing that drives it.
     */
    private final matrix.realworld.Bond.Registry bonds;
    private final Zion zion;
    private final Source source;
    private final matrix.machine.SubstrateBudget substrate;
    /**
     * The inward door's far bank (D-046 step two): WHETHER a filed petition
     * is granted. Held by the root because the root is the only thing allowed
     * to hold both banks (D-012) — and because the two halves of the door must
     * never see each other's types (A1), the root is also the only thing that
     * can carry a name one way and a grant the other.
     */
    private final matrix.machine.DoorPolicy doorPolicy;
    private final Director director;
    private final List<SystemNode> nodes;
    private final MetricsCollector metrics;
    private final DigestCalculator digests = new DigestCalculator();
    private final List<Digest> chain = new ArrayList<>();
    /**
     * Transient cursor for the root-owned Human causal walk. It is reset at
     * tick start and never hashed or read by either world. A cursor avoids a
     * nine-element allocation on every tick while still refusing the first
     * missing, repeated, or reordered hand-over.
     */
    private int causalPhaseCursor = 0;
    private boolean causalTickCompleted = false;
    /**
     * Phase-one truth for the tick currently walking the causal spine. Null
     * means the phase has not produced a value; an eligible-but-empty result
     * is represented by {@link TruthSnapshot#empty}, never by null.
     */
    private TruthSnapshot tickTruth;
    /** The exact immutable phase-one object accepted by phase two. */
    private TruthSnapshot deliveryTruth;
    /** Reused primitive staging; published snapshots own separate compact arrays. */
    private final TruthSnapshot.Builder truthBuilder = new TruthSnapshot.Builder();
    private final PrintStream out;
    private final ChronosLog chronos;
    /**
     * Every birth this run has seen, in the order the world decided them —
     * kept whether the recorder is on or off, because the FOLD runs with the
     * recorder off and still has to prove it re-executed the same births.
     * Observation only: no draw, no digest field, no branch the world can
     * take because this list exists.
     */
    private final List<ChronosLog.Birth> births = new ArrayList<>();
    private final String followName;
    private NeuralLink followed;
    /** The mind the name resolved to, kept for good (#375): the tap binds to a person, not a string. */
    private Human followedMind;
    /** ...unless the subject is The One, who is a role the world re-fills — #107's re-arm, kept. */
    private boolean followingTheOne;
    private int chronosVersionSeen;
    private int agentsSpawned = 0;
    private int patchesDeployed = 0;
    private boolean optOutDone = false;
    /**
     * Whether anybody stood in the census lane on the previous tick — the
     * whole state behind #994's line, and observation only: no draw, no
     * digest field, no branch the world can take because this boolean exists.
     */
    private boolean laneHeldSomebody = false;

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
        this.bonds = new matrix.realworld.Bond.Registry(realWorld, world);
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
        // D-046 step two (#338/#440): the inward door's far bank gets the
        // SECOND named port into the farm — spare rack units, a scalar, wired
        // here because only the root holds both banks (D-012). The rack is
        // PODS_REFERENCE units wide; what death and liberation vacated is what
        // a returning mind may have. DoorPolicy never learns whose.
        this.doorPolicy = new matrix.machine.DoorPolicy(world,
                () -> Config.PODS_REFERENCE - realWorld.farm().occupiedCount(), substrate);
        // Canonical node order (D-031, crown #122): machine, realworld, zion.
        // What the order buys is DRAW order, and only that (#830): same-tick
        // absorption comes from the drain's position in tickOnce, below —
        // not from zion sitting last. And it buys draw order only against
        // MACHINE: realworld and zion commute, so swapping those two is
        // byte-identical over 6,000 ticks and the root door carries the
        // table. The third node is the fence event: nodes.add, addition
        // not refactor.
        this.nodes = List.of(
                new MachineSystem(world, director, source, substrate, pluggedPods),
                new RealWorldSystem(realWorld, bonds),
                new ZionSystem(zion));
        world.flush();
        if (followName != null) {
            followed = reTap();
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
        for (matrix.entities.eco.Species species : matrix.entities.eco.Bestiary.CATALOG) {
            // The homecoming dial (#136): caps multiply, spawn order does not
            // change — at scale 1 this loop is bit-for-bit the canonical one.
            int population = species.populationCap() * Config.ecoScale();
            for (int i = 0; i < population; i++) {
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
        // The catalog, said out loud once, over the zones the line above just
        // counted (#539, D-048). Each row is District.row() verbatim — the
        // print quotes the catalog, it never re-formats the same facts beside
        // it, so a row cannot disagree with the object it describes.
        //
        // NARRATIVE, not a ninth instrument family. D-020's ECO/ATTN precedent
        // is additive-grammar for key=value sample lines, and this row is prose
        // with three figures at the end of it: emitted bare, LineGrammar's own
        // rule reads DISTRICT as an instrument prefix and LineLint verdicts six
        // correct lines GRAMMAR_BROKEN. The event log is D-020's first
        // instrument and the home every other boot fact already uses.
        //
        // Costs the stream nothing: the catalog was built by PlaceGraph at
        // construction from zone names alone, and this loop reads it. Guarded
        // by probes/DistrictNeutral leg 4, not by this comment.
        for (District district : world.places().districts()) {
            world.log(Severity.SYS, district.row());
        }
        world.log(Severity.SYS, "compute model: " + Config.COMPUTE_MODEL.name()
                + " — " + Config.COMPUTE_MODEL.desc());
        world.log(Severity.SYS, bonds.line() + " — nobody has mattered to anybody yet");
        world.log(Severity.SYS, "program society online: the Oracle and "
                + Config.EXILE_COUNT + " exiles walk among the sleepers");
        world.log(Severity.SYS, "ecosystem online: " + matrix.entities.eco.Bestiary.CATALOG.size()
                + " species rendered — a healthy program is invisible"
                + (Config.ecoScale() == 1 ? ""
                        : " (homecoming x" + Config.ecoScale() + ": every population multiplied)"));
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
        if (world.state() == matrix.core.SystemState.NEGOTIATION) {
            world.log(Severity.SYS, "reload refused: the world is at the table — the negotiation must land first");
            return;
        }
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
     * Chronos: a birth enters the record at the tick the world decided it.
     * Called AFTER the world already holds the newborn — the record states
     * what happened, it never causes it. Two sinks, one call: the run's own
     * birth list (kept always, the fold's evidence) and the recorder (kept
     * when one is attached). Draws nothing; the birth-seed law wants the
     * event, and the event is already a fact by the time this runs.
     */
    private void recordBirth(Human who, String family) {
        // The rack unit and the growth ordinal come off the MIND, which is
        // the only object that holds both (#847). A free-born has no slot,
        // and the empty string is what the derivation reads for them — the
        // record writes the input, not a hole where an input would be.
        ChronosLog.Birth birth = new ChronosLog.Birth(world.tick(), who.name, family,
                who.pod == null ? "" : who.pod.rackUnit, who.id);
        births.add(birth);
        if (chronos != null) {
            chronos.birth(birth);
            // The record's own echo, in the run's own stream (#553): where a
            // recording is being written, the operator watching stdout sees
            // exactly what the chronos file is receiving, at the same tick,
            // in the record's own field order. The gate is the recorder for
            // one reason — with no record there is nothing to echo, and a
            // recorder-free run stays byte-identical to a pre-BIRTH main.
            // When the lane flag lands (#526/#543) the gate widens to the
            // enabled lane and the registry catches up (#833); the line's
            // shape does not move, because field order is the contract.
            emit("BIRTH tick=" + birth.tick() + " name=\"" + birth.name()
                    + "\" family=" + birth.family()
                    + " rack=\"" + birth.rack() + "\" id=" + birth.id());
        }
    }

    /**
     * The births this run has seen, in world order — the fold's evidence
     * that a recorded birth was re-EXECUTED and not merely re-read (#550).
     * Read-only by construction: a caller that mutates this would be
     * rewriting history, which is exactly what the record exists to prevent.
     */
    public List<ChronosLog.Birth> births() {
        return List.copyOf(births);
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

    /**
     * The follow tap's only resolver (#375). Before the first match the name is
     * a search key; the instant it lands, the tap is BOUND and the name is never
     * consulted again — afterwards this returns that same mind's current link,
     * or the current One when the subject was The One. Null means the subject is
     * dark right now, never "here is somebody else". Pure read: it resolves
     * links the observer may print and touches no state the digest can see.
     */
    private NeuralLink reTap() {
        if (followedMind == null) {
            NeuralLink first = realWorld.findLink(followName);
            if (first != null) {
                followedMind = first.human;
                followingTheOne = first.avatar instanceof matrix.entities.TheOne;
            }
            return first;
        }
        return followingTheOne ? realWorld.theOneLink() : realWorld.linkOf(followedMind);
    }

    /**
     * The census lane's closing line (#994). Runs on the falling edge only,
     * so the walk over the registry is paid once per closing and never on a
     * quiet tick; the fleet's seats are the CEILING the knobs declare
     * ({@code RIG_CAPACITY * FLEET_MAX}), not the boards afloat right now,
     * because the ceiling is the number the registry is being weighed
     * against. The living count rides along because the census counts its
     * dead on purpose (#202) and a lane can be empty for two very different
     * reasons — everybody crewed, or nobody left breathing.
     */
    private void sayNobodyIsAshore() {
        List<Human> census = zion.census();
        int living = 0;
        for (Human h : census) {
            if (h.alive()) {
                living++;
            }
        }
        int berths = Config.RIG_CAPACITY * Config.FLEET_MAX;
        world.log(Severity.SYS, "nobody is ashore: the census lane is empty — "
                + census.size() + " on the registry, " + living + " of them alive,"
                + " and the fleet's boards seat " + berths
                + "; the inward door reads this lane and no other");
    }

    public void tickOnce() {
        beginCausalTick();
        snapshotTruth();
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
        // The door's inward direction, step one (D-046, #335). Only the root
        // holds both banks (D-012), so only the root can offer the city's
        // ashore roster to the door path — the census lane, and no other.
        // After the drain on purpose: a mind freed THIS tick joins the census
        // first and can be offered the door no earlier than the next one.
        List<Human> lane = zion.ashore();
        realWorld.doorTick(lane);
        // #994, the STATED branch: the tick the lane closes is said out loud.
        // The roster the door was just offered is the one read here — one walk,
        // not a second one — and the falling edge is the event: everybody who
        // could have petitioned took a berth, a wire or a grave, so from this
        // tick the door has nobody to ask. Silence used to mean both "nobody
        // ashore" and "no door", which is how 6 freed against 6 berths shipped
        // three times unnoticed.
        if (laneHeldSomebody && lane.isEmpty()) {
            sayNobodyIsAshore();
        }
        laneHeldSomebody = !lane.isEmpty();
        // Step two (#338): the names cross the bridge. A String goes out, a
        // boolean comes back, and neither half of the door has learned a type
        // belonging to the other (A1). The grant has no consumer until #340
        // lands the performer — deliberately: WHETHER ships before WHAT, so
        // that when the pod allocation arrives it can be unconditional.
        for (String petitioner : realWorld.drainPetitions()) {
            doorPolicy.decide(petitioner);
        }
        if (world.state() == matrix.core.SystemState.NORMAL
                && world.ledger().overflowed() && !oneExists()) {
            RealWorld.OneBorn born = realWorld.birthTheOne("Thomas A. Anderson");
            matrix.entities.TheOne one = born.avatar();
            world.flush();
            // The world's own oldest birth is the substrate's first emitter
            // (#553): the record leads nothing here — the One is already in
            // the world — but from this line on the birth-seed law has an
            // event to key to. HUMAN is D-042's family vocabulary: a mind in
            // a pod, however famous.
            recordBirth(born.pilot(), "HUMAN");
            world.log(Severity.FATE, "The One is born — " + one.pilotName
                    + ", grown for a debt of " + world.ledger().balance()
                    + " (the ledger does not forgive; it balances)");
        }
        deliverPercepts();
        reduceMinds();
        proposeIntents();
        validateAndCommit();
        applyEffects();
        settleConsequences();
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
            // #118: the root hands zion's pirate BOARD to the collector (D-012)
            // and the trace suffix rides the ZION line — present exactly when the
            // metric is MEASURABLE, which is not the same thing as links>0 (#374).
            // The suffix follows BODIES; links= on the same line follows WIRES,
            // counted straight off the rigs. They are two facts, and they disagree
            // at both ends of a session by design. At the open, a session opened in
            // zion's slot (the LAST node) queues its spawn behind this tick's flush,
            // so that tick prints links>0 with no suffix. At the close, a wire cut
            // in the same slot leaves its body standing until the next flush, so the
            // board still carries a pirate links= has already dropped — which is the
            // whole point of handing over the board and not the open subset (#808):
            // that body is a visitor, and the resident baseline must not absorb it.
            // The collector measures against the world it can see, so it keeps only
            // board bodies the world already holds, and it needs live agents and
            // resident reds or the mean and its baseline have no denominator. Absent
            // otherwise, the ECO line's short form. The rule is stated in full on
            // MetricsCollector.traceSuffix.
            emit(zion.zionLine(t) + metrics.traceSuffix(zion.pirateBoard()));
        }
        digestCausalState();
        if (t % Config.DIGEST_EVERY_TICKS == 0) {
            world.digestInto(digests);
            // The D-033 addendum's framed segment rides AFTER the entity walk:
            // only the root holds both banks (D-012), so only the root can
            // feed both worlds to one referee.
            realWorld.digestInto(digests);
            // ...and the heart rides immediately after it (#497): a declared
            // move, appended at the end of the real-side walk exactly where
            // it would sit if RealWorld framed it itself.
            bonds.digestInto(digests);
            Digest d = new Digest(t, digests.finishHex());
            chain.add(d);
            emit(d.format());
        }
        observeCausalState();
        if (followName != null && t % Config.FOLLOW_EVERY_TICKS == 0) {
            // One rule, bound to a MIND and not to a string (#375): the name is a
            // search key exactly once, and from the first match the stream belongs
            // to that person. Names are not unique — 196 humans wear 154 of them at
            // seed 42 — so re-tapping by name let a namesake walk in mid-stream
            // under the same heading. A dark stream still re-arms, but only onto
            // its own subject: the same mind on a new wire resumes (walked out,
            // then riding a pirate signal — the whole point of the tap), and The
            // One resumes because The One is a role the world keeps unique, not a
            // name that happens to match. #107's reborn Thomas is untouched.
            if (followed == null) {
                followed = reTap();
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
        finishCausalTick();
    }

    /** Start one root-owned phase walk before either world advances. */
    private void beginCausalTick() {
        causalPhaseCursor = 0;
        causalTickCompleted = false;
        tickTruth = null;
        deliveryTruth = null;
    }

    /** Phase 1: freeze eligible truth before either world advances. */
    private void snapshotTruth() {
        enterCausalPhase(CausalPhase.SNAPSHOT_TRUTH);
        tickTruth = freezeTruth(Math.addExact(world.tick(), 1));
    }

    /**
     * Phase 2 currently accepts only the frozen phase-one input. #1691 will
     * turn entries into audited attempts; it must extend this method without
     * consulting either live world.
     */
    private void deliverPercepts() {
        enterCausalPhase(CausalPhase.DELIVER_PERCEPTS);
        if (tickTruth == null) {
            throw new IllegalStateException("delivery has no tick-start truth snapshot");
        }
        deliveryTruth = tickTruth;
    }

    /**
     * Build the complete V1 fact group for each streamable resident.
     *
     * <p>Eligibility is one exact conjunction at this boundary: the Human's
     * current link is open, brain and avatar are alive, and that avatar is
     * still present in the Matrix registry. The real-side registry is only a
     * candidate source: the reusable builder inserts scalar candidates by
     * immutable Human ordinal and publishes that compact ascending prefix,
     * so list iteration order cannot become percept order. No ledger, RNG,
     * birth key, pod, residue, other entity, or observer setting enters the
     * snapshot.
     */
    private TruthSnapshot freezeTruth(long tick) {
        truthBuilder.begin();
        for (Human human : realWorld.humans()) {
            NeuralLink link = human.link();
            if (link == null || link.closed() || !human.alive()
                    || !link.avatar.alive || !world.isPresent(link.avatar)) {
                continue;
            }
            TruthSnapshot.ResidentPill residentPill = switch (link.avatar.pill) {
                case BLUE -> TruthSnapshot.ResidentPill.BLUE;
                case RED -> TruthSnapshot.ResidentPill.RED;
            };
            truthBuilder.add(human.id, residentPill,
                    link.avatar.xCm(), link.avatar.yCm());
        }
        return truthBuilder.build(tick);
    }

    /** Phase 3 hook: future real-side reducers consume only visible receipts here. */
    private void reduceMinds() {
        enterCausalPhase(CausalPhase.REDUCE_MINDS);
    }

    /** Phase 4 hook: future minds publish immutable proposals here. */
    private void proposeIntents() {
        enterCausalPhase(CausalPhase.PROPOSE_INTENTS);
    }

    /** Phase 5 hook: the root will reject or canonically commit proposals here. */
    private void validateAndCommit() {
        enterCausalPhase(CausalPhase.VALIDATE_AND_COMMIT);
    }

    /** Phase 6 hook: canonical state will consume classified causes here. */
    private void applyEffects() {
        enterCausalPhase(CausalPhase.APPLY_EFFECTS);
    }

    /** Phase 7 hook: future-causal biography and social state settles here. */
    private void settleConsequences() {
        enterCausalPhase(CausalPhase.SETTLE_CONSEQUENCES);
    }

    /** Phase 8 hook: it precedes the existing periodic seal on every tick. */
    private void digestCausalState() {
        enterCausalPhase(CausalPhase.DIGEST);
    }

    /**
     * Phase 9 Human-causal hook: future resident-visible observation follows
     * the seal and remains causally inert. Legacy D-020 diagnostic event and
     * metric lines keep their historical byte order; they are instruments,
     * not percept receipts delivered to a mind.
     */
    private void observeCausalState() {
        enterCausalPhase(CausalPhase.OBSERVE);
    }

    /**
     * Advance the one closed order. Reordering or duplicating a hook fails at
     * the first wrong hand-over rather than allowing a malformed tick to
     * reach the next digest.
     */
    private void enterCausalPhase(CausalPhase phase) {
        List<CausalPhase> order = CausalPhase.canonicalOrder();
        if (causalPhaseCursor >= order.size() || phase != order.get(causalPhaseCursor)) {
            CausalPhase expected = causalPhaseCursor < order.size()
                    ? order.get(causalPhaseCursor) : null;
            throw new IllegalStateException("causal phase out of order: expected "
                    + expected + " but entered " + phase);
        }
        causalPhaseCursor++;
    }

    /** Seal the phase receipt only after the observer hook has returned. */
    private void finishCausalTick() {
        if (causalPhaseCursor != CausalPhase.canonicalOrder().size()) {
            throw new IllegalStateException("causal tick incomplete: phases="
                    + causalPhaseCursor);
        }
        if (tickTruth == null || deliveryTruth != tickTruth) {
            throw new IllegalStateException(
                    "causal tick did not deliver its exact frozen truth snapshot");
        }
        causalTickCompleted = true;
    }

    /**
     * Immutable observation of the last fully completed phase walk. Before
     * the first tick it is empty; a partial or failed tick never masquerades
     * as a completed one.
     */
    public List<CausalPhase> lastCausalPhases() {
        return causalTickCompleted ? CausalPhase.canonicalOrder() : List.of();
    }

    /**
     * Ticks the world forward and hands back the determinism chain as it
     * stands at the tick this call stopped on — a copy, not the field (#996).
     * The chain is D-010's sacred object and D-012 puts it in exactly one
     * pair of hands, so a caller gets a reading and not the instrument: the
     * list it holds cannot grow under it when the world ticks on, two calls
     * on one universe hand back two objects, and {@code clear()} on a return
     * value empties nothing the world is keeping. Nothing inside this class
     * reads the field back — {@code chain.add} on the digest beat is its only
     * other use — so the copy costs the world nothing it was relying on.
     */
    public List<Digest> run(long ticks) {
        for (long i = 0; i < ticks; i++) {
            tickOnce();
        }
        return List.copyOf(chain);
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
        return Snapshot.of(world, sink -> {
            realWorld.digestInto(sink);
            bonds.digestInto(sink);
        });
    }

    public int aliveEntities() {
        return world.countAlive();
    }

    /**
     * The far-mover ledger's high-water mark over this run (#825) — the
     * linear term the ring hunts still pay, reported on PERF and judged by
     * {@code --bench} against {@link Config#huntLedgerCeiling()}.
     */
    public int farMoverPeak() {
        return world.farMoverPeak();
    }

    private void emit(String line) {
        if (out != null) {
            out.print(line + "\n");
        }
    }
}
