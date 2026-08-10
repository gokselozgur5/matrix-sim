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
        if (t % Config.EXILE_COLLECT_EVERY_TICKS == 0) {
            collectRandomExile();
        }
        if (t % Config.MYTH_EVERY_TICKS == 0) {
            mythReport();
        }
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
