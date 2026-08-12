import matrix.Simulation;
import matrix.core.Config;
import matrix.core.SystemState;

/**
 * Probe: the rng stream, measured at last — in the windows the run declares.
 *
 * The digest counts draws (an unused draw is still a state change —
 * D-010), but the stream's SHAPE was never measured. This meter takes
 * per-tick deltas of the world's draw counter and reports them by
 * phase window: boot cost, steady city, the war build, the infection
 * cascade, the negotiation freeze (the stream's quietest moment: frozen
 * world, honest instruments), and peace.
 *
 * <p>The windows were four tick literals — {@code <=1500}, {@code <=3200},
 * {@code <=4300}, else — honest for seed 42 and wrong for the multiverse:
 * overflow lands anywhere from ~2,900 to ~5,700, and in a QUIET universe
 * (seeds 1 and 5 of the first score) it never lands at all, so the old
 * build cheerfully reported a cascade that never happened and filed a
 * thousand post-treaty ticks under it. Numbers attributed to the wrong
 * phase are worse than no numbers: a measurement with a story the run
 * never told.
 *
 * <p>Every boundary now comes from a transition the run itself declares:
 * SmithPrime alive (the fork), the infection reaching half the Director's
 * own {@link Config#OVERFLOW_FRACTION} line (the cascade's approach) and
 * then the line itself (the overflow), the world's version bump (the
 * reboot — treaty or the old playbook, both are reboots), and
 * {@link SystemState#NEGOTIATION} for the freeze, which was always read
 * from the world and is the reason this probe was trustworthy in exactly
 * one window. A window is printed only when the run opened it: no
 * overflow, no cascade line. The tick literal survives as one labelled
 * fallback — {@link Config#SMITH_FORK_TICK}, the Director's collection
 * order — for a run that passes the order without a fork ever landing;
 * every window says {@code basis=state} or {@code basis=fallback}, so a
 * reader can never mistake a guess for an observation.
 *
 * <p>The BOUNDS line publishes the derivation itself: the four transition
 * ticks, {@code -1} where the run never declared one.
 *
 * Two consumers: D-033's gate (a grow-time threshold draw is a
 * declared digest break — the verdict should know the size of the
 * stream it shifts), and the determinism bench (a tick drawing wildly
 * off its phase's band is the earliest smell of unaccounted
 * randomness). Two more waiting: era runs and scaled runs, whose arcs
 * are not the film's and whose first draw tables would otherwise be
 * attributed to windows they never entered.
 *
 * Usage: java -cp out:probes/out DrawMeter [ticks] [seed]
 */
public final class DrawMeter {

    private static final int STEADY = 0;
    private static final int WAR = 1;
    private static final int CASCADE = 2;
    private static final int TREATY = 3;
    /** Ticks past the collection order with no fork yet: steady if one lands, war if none ever does. */
    private static final int PENDING = 4;

    private static final String[] NAMES =
            {"steady_pre_fork", "war_build", "cascade_peak", "after_treaty"};

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        int version = world.version();
        long boot = world.rng().draws();
        System.out.println("DRAWS phase=boot total=" + boot);

        long prev = boot;
        long[] winSum = new long[5];
        long[] winTicks = new long[5];
        long[] winMax = new long[5];
        long freezeTicks = 0, freezeDraws = 0;
        long forkTick = -1, cascadeTick = -1, overflowTick = -1, rebootTick = -1;
        long lastTick = 0;
        for (long t = 0; t < ticks; t++) {
            SystemState before = world.state();
            sim.tickOnce();
            long now = world.rng().draws();
            long delta = now - prev;
            prev = now;
            lastTick = world.tick();

            if (forkTick < 0 && forked(world)) {
                forkTick = lastTick;
            }
            if (cascadeTick < 0 || overflowTick < 0) {
                double infected = world.countAlive() == 0
                        ? 0.0 : (double) world.countInfected() / world.countAlive();
                if (cascadeTick < 0 && infected >= Config.OVERFLOW_FRACTION / 2) {
                    cascadeTick = lastTick;
                }
                // The Director's own compare, plus its answer: the treaty path
                // leaves NORMAL on the crossing tick, the old playbook does not.
                if (overflowTick < 0 && (infected >= Config.OVERFLOW_FRACTION
                        || world.state() != SystemState.NORMAL)) {
                    overflowTick = lastTick;
                }
            }
            if (rebootTick < 0 && world.version() != version) {
                rebootTick = lastTick;
            }

            if (before == SystemState.NEGOTIATION) {
                freezeTicks++;
                freezeDraws += delta;
                continue;
            }
            int w = window(lastTick, forkTick, cascadeTick, rebootTick);
            winSum[w] += delta;
            winTicks[w]++;
            winMax[w] = Math.max(winMax[w], delta);
        }

        // The ticks between the collection order and the fork belong to the
        // steady city if a fork ever landed — and to the literal's war window
        // only if none ever did, which is the one place a guess is made.
        fold(winSum, winTicks, winMax, PENDING, forkTick >= 0 ? STEADY : WAR);

        // A cascade is the approach to an overflow that happened. Where the
        // world never reached its own line, the climb was just more war —
        // fold it back rather than name a phase the run never entered.
        if (overflowTick < 0) {
            fold(winSum, winTicks, winMax, CASCADE, WAR);
        }

        System.out.println("BOUNDS fork=" + forkTick + " cascade=" + cascadeTick
                + " overflow=" + overflowTick + " reboot=" + rebootTick);

        // Only the fork boundary owns a fallback, so only it can be guessed:
        // a run that passes the collection order without a fork gets the
        // literal and says so; a run that ends first guessed nothing.
        String forkBasis = forkTick >= 0 || lastTick <= Config.SMITH_FORK_TICK
                ? "state" : "fallback";
        String[] basis = {forkBasis, forkBasis, "state", "state"};
        for (int w = 0; w < 4; w++) {
            if (winTicks[w] == 0) {
                continue;
            }
            System.out.println("DRAWS phase=" + NAMES[w]
                    + " basis=" + basis[w]
                    + " ticks=" + winTicks[w]
                    + " mean=" + winSum[w] / winTicks[w]
                    + " max=" + winMax[w]);
        }
        System.out.println("DRAWS phase=negotiation_freeze basis=state ticks=" + freezeTicks
                + " total=" + freezeDraws
                + (freezeTicks > 0 && freezeDraws == 0 ? " (the world holds its breath — zero draws)" : ""));
        System.out.println("DRAWMETER seed=" + seed + " ticks=" + ticks
                + " final_draws=" + prev);
    }

    /**
     * The phase of one tick, from the transitions seen so far. The freeze is
     * carved out by the caller, so a cascade can never swallow the held
     * breath; a reboot closes the arc whichever way it arrived.
     */
    private static int window(long tick, long forkTick, long cascadeTick, long rebootTick) {
        if (rebootTick >= 0) {
            return TREATY;
        }
        if (cascadeTick >= 0) {
            return CASCADE;
        }
        if (forkTick >= 0) {
            return WAR;
        }
        return tick > Config.SMITH_FORK_TICK ? PENDING : STEADY;
    }

    /** Move one window's tally into another — the arc decided late what those ticks were. */
    private static void fold(long[] sum, long[] ticks, long[] max, int from, int into) {
        sum[into] += sum[from];
        ticks[into] += ticks[from];
        max[into] = Math.max(max[into], max[from]);
        sum[from] = 0;
        ticks[from] = 0;
        max[from] = 0;
    }

    /**
     * The story forked (D-003): the Source's collection was refused and
     * SmithPrime is walking. The entity's presence is the world's own
     * declaration of the fork — 26 ticks after the collection order, the
     * grace window the Source grants before it deletes.
     */
    private static boolean forked(matrix.core.World world) {
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.SmithPrime) {
                return true;
            }
        }
        return false;
    }

    private DrawMeter() {}
}
