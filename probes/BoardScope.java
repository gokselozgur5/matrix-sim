import matrix.Simulation;
import matrix.core.World;
import matrix.entities.Avatar;
import matrix.zion.Hovercraft;
import matrix.zion.Zion;

import java.util.ArrayList;
import java.util.List;

/**
 * Probe: whose bodies are on the fleet's boards after a hull goes down?
 * (#918)
 *
 * <p>{@code Zion.pirateBoard} is the fourth walk over the fleet and the only
 * one that does not guard {@code MissionState.LOST} — launch does, recall
 * does, {@code zionLine} does before counting. The set it builds is the trace
 * metric's exclusion list, and a sunk hull's board never empties:
 * {@code BroadcastRig.beginSession} holds the only line that takes a closed
 * wire off a board, and a LOST hull never begins another session.
 *
 * <p>So the roster of every ship that has ever sunk stays in that list until
 * the process exits. This probe counts them.
 *
 * <h2>What this measures and what it does not</h2>
 *
 * It measures the SET, not the arithmetic. The stale members are inert today
 * because {@code MetricsCollector.traceSuffix} keeps only
 * {@code p.alive && world.isPresent(p)}, and every wire cut with the hull was
 * queued for removal — so a drowned pirate never reaches a number. That
 * filter is the only thing standing between a stale roster and the baseline,
 * and this probe is what makes the roster's size a fact rather than a
 * sentence in a javadoc.
 *
 * <p>The verdict therefore pins the RELATIONSHIP rather than demanding zero:
 * every board member belonging to a LOST hull must be absent from the world.
 * `lost_present=0` is the invariant the presence filter silently relies on,
 * and the day it stops holding — the SmithCopy unwind path #918 argues from
 * the code and no seed has walked — this row goes red rather than a baseline
 * moving quietly.
 *
 * <p>`stale=` is the population and rides its own census line (#1221): its
 * size is a description of how many wires this seed happened to cut, not a
 * finding, and pinning it would make the lane red for a scenario change.
 *
 * <pre>
 *   probes/BoardScope [ticks] [seed] [sink-tick]
 * </pre>
 */
public final class BoardScope {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();

        int ticks = args.length > 0 ? Integer.parseInt(args[0]) : 6000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        int sinkAt = args.length > 2 ? Integer.parseInt(args[2]) : 4600;

        Simulation sim = new Simulation(seed, null, null);
        for (int t = 0; t < ticks; t++) {
            if (t == sinkAt) {
                sim.commandSink();
            }
            sim.tickOnce();
        }

        Zion zion = Probes.zion(sim);
        World world = Probes.world(sim);

        // The board, and which hull each member came off. `pirateBoard` walks
        // the fleet in order and each rig in registration order, so walking it
        // the same way here attributes every member without the domain having
        // to expose the mapping.
        List<Avatar> board = zion.pirateBoard();
        List<Avatar> lost = new ArrayList<>();
        int cursor = 0;
        for (Hovercraft ship : Probes.fleet(zion)) {
            List<Avatar> mine = new ArrayList<>();
            ship.rig().boardAvatarsInto(mine);
            if (ship.state() == Hovercraft.MissionState.LOST) {
                lost.addAll(mine);
            }
            cursor += mine.size();
        }

        int lostPresent = 0;
        for (Avatar a : lost) {
            if (a.alive && world.isPresent(a)) {
                lostPresent++;
                System.out.println("BOARD_LIVE_ON_LOST_HULL id=" + a.id);
            }
        }

        System.out.println("BOARD_CENSUS board=" + board.size() + " walked=" + cursor
                + " stale=" + lost.size());
        Probes.leave("VERDICT BOARD_SCOPE_HONEST lost_present=" + lostPresent
                        + " stale_none=" + (lost.isEmpty() ? 1 : 0),
                lostPresent == 0 && !lost.isEmpty() ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    private BoardScope() {}
}
