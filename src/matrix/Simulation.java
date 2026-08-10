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
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.realworld.Human;
import matrix.realworld.LinkKind;
import matrix.realworld.NeuralLink;
import matrix.realworld.PerceptionFrame;
import matrix.realworld.RealWorld;

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

    private final Rng rng;
    private final EventBus bus = new EventBus();
    private final World world;
    private final RealWorld realWorld;
    private final List<SystemNode> nodes;
    private final MetricsCollector metrics;
    private final DigestCalculator digests = new DigestCalculator();
    private final List<Digest> chain = new ArrayList<>();
    private final PrintStream out;
    private final String followName;
    private NeuralLink followed;
    private int agentsSpawned = 0;

    public Simulation(long seed, OutputStream sink, String followName) {
        this.rng = new Rng(seed);
        this.out = sink == null ? null : new PrintStream(sink, true, StandardCharsets.UTF_8);
        this.followName = followName;
        PlaceGraph places = new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM);
        this.world = new World(rng, bus, places);
        this.realWorld = new RealWorld(world);
        this.metrics = new MetricsCollector(world);
        this.nodes = List.of(
                new MachineSystem(world, new Director(world)),
                new RealWorldSystem(realWorld));
        if (this.out != null) {
            EventLog log = new EventLog(this.out);
            bus.subscribe(log::onEvent);
        }
        boot(seed);
    }

    private void boot(long seed) {
        for (int i = 0; i < Config.BLUE_START; i++) {
            jackIn(Pill.BLUE);
        }
        for (int i = 0; i < Config.RED_START; i++) {
            jackIn(Pill.RED);
        }
        for (int i = 0; i < Config.AGENT_START; i++) {
            spawnAgent();
        }
        world.flush();
        if (followName != null) {
            followed = realWorld.findLink(followName);
        }
        world.log(Severity.FATE, "MATRIX v6.0 boot — seed " + seed + ", "
                + realWorld.farm().occupiedCount() + " nodes plugged, city "
                + (Config.WORLD_W_CM / 100_000.0) + " km x " + (Config.WORLD_H_CM / 100_000.0) + " km");
        world.log(Severity.SYS, "exit nodes online: " + world.places().exits().size()
                + " phone booths across " + world.places().zones().size() + " zones");
        world.log(Severity.SYS, "compute model: PROCESSOR — the inmates render their own cells");
        if (followName != null) {
            world.log(Severity.SYS, followed == null
                    ? "follow: no pilot matches '" + followName + "'"
                    : "follow: streaming the dream of " + followed.human.name + " as JSONL");
        }
    }

    private void jackIn(Pill pill) {
        Human h = realWorld.grow();
        Position pos = new Position(rng.nextInt(Config.WORLD_W_CM + 1), rng.nextInt(Config.WORLD_H_CM + 1));
        Avatar avatar = new Avatar(world.allocateId(), pos, h.name, pill);
        world.queue(new WorldEvent.Spawn(avatar));
        realWorld.register(new NeuralLink(h, avatar, LinkKind.HARDLINE));
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

    private void spawnAgent() {
        String codename = AGENT_NAMES[agentsSpawned % AGENT_NAMES.length];
        agentsSpawned++;
        Position pos = new Position(rng.nextInt(Config.WORLD_W_CM + 1), rng.nextInt(Config.WORLD_H_CM + 1));
        world.queue(new WorldEvent.Spawn(new Agent(world.allocateId(), pos, codename)));
        world.log(Severity.SYS, "IDS daemon deployed: agent " + codename);
    }

    public void tickOnce() {
        for (SystemNode node : nodes) {
            node.tick(world.tick() + 1);
        }
        long t = world.tick();
        if (t % Config.METRIC_EVERY_TICKS == 0) {
            emit(metrics.sample(t).format());
        }
        if (t % Config.DIGEST_EVERY_TICKS == 0) {
            world.digestInto(digests);
            Digest d = new Digest(t, digests.finishHex());
            chain.add(d);
            emit(d.format());
        }
        if (followed != null && t % Config.FOLLOW_EVERY_TICKS == 0 && followed.avatar.alive) {
            emit(PerceptionFrame.jsonl(t, followed.avatar, world));
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
