import matrix.Simulation;
import matrix.core.SystemState;

/**
 * Probe: the rng stream, measured at last.
 *
 * The digest counts draws (an unused draw is still a state change —
 * D-010), but the stream's SHAPE was never measured. This meter takes
 * per-tick deltas of the world's draw counter and reports them by
 * phase window: boot cost, steady city, infection cascade, the
 * negotiation freeze (the stream's quietest moment: frozen world,
 * honest instruments), and peace.
 *
 * Two consumers: D-033's gate (a grow-time threshold draw is a
 * declared digest break — the verdict should know the size of the
 * stream it shifts), and the determinism bench (a tick drawing wildly
 * off its phase's band is the earliest smell of unaccounted
 * randomness).
 *
 * Usage: java -cp out:probes/out DrawMeter [ticks] [seed]
 */
public final class DrawMeter {

    public static void main(String[] args) throws Exception {
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        long boot = world.rng().draws();
        System.out.println("DRAWS phase=boot total=" + boot);

        long prev = boot;
        long[] winSum = new long[4];
        long[] winTicks = new long[4];
        long[] winMax = new long[4];
        long freezeTicks = 0, freezeDraws = 0;
        for (long t = 0; t < ticks; t++) {
            SystemState before = world.state();
            sim.tickOnce();
            long now = world.rng().draws();
            long delta = now - prev;
            prev = now;
            if (before == SystemState.NEGOTIATION) {
                freezeTicks++;
                freezeDraws += delta;
                continue;
            }
            int w = window(world.tick());
            winSum[w] += delta;
            winTicks[w]++;
            winMax[w] = Math.max(winMax[w], delta);
        }
        String[] names = {"steady_pre_fork", "war_build", "cascade_peak", "after_treaty"};
        for (int w = 0; w < 4; w++) {
            if (winTicks[w] == 0) {
                continue;
            }
            System.out.println("DRAWS phase=" + names[w]
                    + " ticks=" + winTicks[w]
                    + " mean=" + winSum[w] / winTicks[w]
                    + " max=" + winMax[w]);
        }
        System.out.println("DRAWS phase=negotiation_freeze ticks=" + freezeTicks
                + " total=" + freezeDraws
                + (freezeTicks > 0 && freezeDraws == 0 ? " (the world holds its breath — zero draws)" : ""));
        System.out.println("DRAWMETER seed=" + seed + " ticks=" + ticks
                + " final_draws=" + prev);
    }

    /** Coarse arc windows for seed-42-class universes; honest labels, not exact bounds. */
    private static int window(long tick) {
        if (tick <= 1_500) return 0;
        if (tick <= 3_200) return 1;
        if (tick <= 4_300) return 2;
        return 3;
    }

    private DrawMeter() {}
}
