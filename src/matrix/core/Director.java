package matrix.core;

import matrix.entities.AgentSmith;
import matrix.entities.Avatar;
import matrix.entities.ExileProgram;
import matrix.entities.Pill;
import matrix.machine.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * The narrative engine. v1: the awakening cadence. v2: the deletion drama —
 * exiles get collected and hide (D-025), and at the appointed tick the
 * Source deprecates agent Smith, whose answer forks the story (D-003).
 */
public final class Director {
    private final World world;
    private final Source source;
    private final AgentSmith namedSmith;
    private boolean forkOrdered = false;
    private int negoTimer = -1;
    private int peaceTimer = -1;

    public Director(World world, Source source, AgentSmith namedSmith) {
        this.world = world;
        this.source = source;
        this.namedSmith = namedSmith;
    }

    public void tick(long t) {
        awaken(t);
        if (!forkOrdered && t >= Config.SMITH_FORK_TICK) {
            orderSmithCollection("scheduled deprecation");
        }
        if (t == Config.AGENT_DECOMMISSION_TICK) {
            decommissionOneAgent();
        }
        if (t % Config.EXILE_COLLECT_EVERY_TICKS == 0) {
            collectRandomExile();
        }
        if (t % Config.MYTH_EVERY_TICKS == 0) {
            mythReport();
        }
        routeOverflow();
        if (world.state() == SystemState.PEACE && --peaceTimer == 0) {
            world.setState(SystemState.NORMAL);
            world.log(Severity.SYS, "the peace settles into routine — agents back on patrol, the door still open");
        }
    }

    /** The finale's switchboard: overflow with a One means a table; without one, the old playbook. */
    private void routeOverflow() {
        if (world.state() != SystemState.NORMAL || world.countAlive() == 0) {
            return;
        }
        double infected = (double) world.countInfected() / world.countAlive();
        if (infected < Config.OVERFLOW_FRACTION) {
            return;
        }
        matrix.entities.TheOne one = findTheOne();
        if (one == null) {
            matrix.machine.Architect.INSTANCE.reload(world, true);
            world.ledger().reset();
            return;
        }
        world.setState(SystemState.NEGOTIATION);
        negoTimer = Config.NEGO_TICKS;
        world.queue(new matrix.core.WorldEvent.Remove(one.id));
        world.flush();
        world.log(Severity.BAD, "SMITH OVERFLOW — " + Math.round(infected * 100)
                + "% assimilated; the old playbook is impossible");
        world.log(Severity.FATE, "The One flies to Machine City — blind, broken, one card left");
    }

    /** The frozen negotiation: only these lines advance while the world holds its breath. */
    public void negotiationTick() {
        negoTimer--;
        if (negoTimer == 30) {
            world.log(Severity.BAD, "Deus Ex Machina: \"WHAT DO YOU WANT?\"");
        }
        if (negoTimer == 20) {
            world.log(Severity.FATE, "The One: \"Peace.\"");
        }
        if (negoTimer == 10) {
            world.log(Severity.SYS, "the machines accept — delete broadcast staging through the anomaly");
        }
        if (negoTimer <= 0) {
            matrix.machine.MachineCity.executeTreaty(world);
            world.ledger().reset();
            peaceTimer = Config.PEACE_TICKS;
        }
    }

    private matrix.entities.TheOne findTheOne() {
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.TheOne one) {
                return one;
            }
        }
        return null;
    }

    /** Ops console path: deprecate Smith early, by hand. */
    public void orderSmithCollection(String reason) {
        if (forkOrdered) {
            world.log(Severity.SYS, "the Source: Smith is already past collection — " + reason + " ignored");
            return;
        }
        forkOrdered = true;
        world.log(Severity.SYS, "the Source: agent Smith deprecated (" + reason + ") — collection ordered");
        source.collect(namedSmith);
    }

    private void awaken(long t) {
        if (t % Config.AWAKEN_EVERY_TICKS != 0 || world.count(Pill.RED) >= Config.RED_CAP) {
            return;
        }
        List<Avatar> blues = world.aliveAvatars(Pill.BLUE);
        if (blues.isEmpty()) {
            return;
        }
        Avatar chosen = blues.get(world.rng().nextInt(blues.size()));
        chosen.pill = Pill.RED;
        world.log(Severity.OK, "red pill: " + chosen.pilotName + " woke up and dropped off the cluster");
    }

    /**
     * The compliance leg of D-025 gets real traffic: one ordinary daemon
     * is decommissioned post-fork, accepts its SIGTERM, and the GC line
     * fires — deletion-by-consent must exist in the log, not just in the
     * contract (skeptic finding: it was dead code).
     */
    private void decommissionOneAgent() {
        for (var entity : world.entities()) {
            if (entity.alive && entity instanceof matrix.entities.Agent a
                    && !(entity instanceof AgentSmith)) {
                world.log(Severity.SYS, "post-fork audit: one security daemon marked surplus — budget follows the war");
                source.collect(a);
                return;
            }
        }
    }

    private void collectRandomExile() {
        List<ExileProgram> exiles = livingExiles();
        if (exiles.isEmpty()) {
            return;
        }
        source.collect(exiles.get(world.rng().nextInt(exiles.size())));
    }

    private void mythReport() {
        List<ExileProgram> exiles = livingExiles();
        if (exiles.isEmpty()) {
            return;
        }
        ExileProgram e = exiles.get(world.rng().nextInt(exiles.size()));
        world.log(Severity.MYTH, "user report: " + e.kind.label
                + " sighted — legacy process from an earlier version, ticket closed (by design)");
    }

    private List<ExileProgram> livingExiles() {
        List<ExileProgram> out = new ArrayList<>();
        for (var entity : world.entities()) {
            if (entity.alive && entity instanceof ExileProgram e) {
                out.add(e);
            }
        }
        return out;
    }
}
