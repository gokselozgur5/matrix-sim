import matrix.Simulation;
import matrix.core.Config;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: how big is one déjà vu, and what does the tick it lands on cost?
 *
 * Parking (D-024 P2) buys a smaller ticked population by folding an
 * unwatched region's catalog residents into stored id lists. The bill
 * arrives all at once: when attention returns, {@code RegionMap.materialize}
 * re-places every stored id in a single flush and the whole crowd re-enters
 * the walk on one tick. Averages cannot see it — a 6,000-tick mean over a
 * city that unparked twice is the mean of a city that never unparked. This
 * probe measures the extremes instead: the largest single-tick
 * re-materialisation of the run, the tick it landed on, and that tick's
 * wall cost against the quiet ticks around it.
 *
 * <p>Two numbers, two different kinds of fact, and the probe keeps them
 * apart. The mind count is a function of the seed: same seed, same storm,
 * to the resident. The wall cost is weather — it moves with the JIT, the
 * box and whatever else is running on it — so it is REPORTED beside its own
 * noise floor (the quiet ticks' median, p99 and max) and never judged.
 * AllocMeter's #916 note is the reason: a threshold set inside an
 * instrument's own spread fires on the weather instead of on the world.
 * The judged figure is the count.
 *
 * <p>What it found on its first run is that the storm is the cheap half.
 * Across eleven runs at seeds 42 and 7, no unpark tick reached the quiet
 * p99: seed 42's worst re-materialises 834 minds on tick 4103 at 0.61-3.75x
 * the quiet median, against a p99 of 4.2-14.2x. Every run's first FOLD
 * cleared p99 and five of the eleven cleared the quiet maximum — tick 4102
 * folds the same 834 minds at 15.3-42.8x. So the probe prints a TICKCOST
 * line for every tick that folded OR materialised anything: watching only
 * the déjà vu would have priced the wrong half of the transaction.
 *
 * <p>The bound is stated at x11 and nowhere else. The bestiary multiplier
 * IS the parked population, so a storm figure carries the scale it was
 * measured at or it means nothing; at any other scale this probe reports
 * and declines to verdict, the way AllocMeter declines a byte budget for a
 * city no record ever priced (#826).
 *
 * <p>The count comes off the world's own FATE line rather than out of the
 * RegionMap, because the aggregate the flush materialises is not the
 * aggregate that stood there when the tick began — the ECO-cadence coarse
 * tick can add or forget a resident in between. The world reports what it
 * actually re-placed. The event count is cross-checked against
 * {@code World.unparks()} so a missed line reads as a verdict rather than
 * as a calm city.
 *
 * Usage: java -cp out:probes/out UnparkStorm [ticks] [seed] [scale]
 *        java -cp out:probes/out UnparkStorm --selfcheck
 *
 * Wall cost: a 6,000-tick x11 run walks ~5,260 entities every tick and
 * takes minutes, not seconds. This is a laboratory instrument, not a lane
 * one; it has no row in probes/bench.sh.
 */
public final class UnparkStorm {

    /**
     * The scale the bound is stated at — the D-027 retargeted row's city
     * (#136), and the only scale at which parking has been observed to fire.
     */
    static final int JUDGED_SCALE = 11;

    /**
     * S6's stated bound: no single tick re-materialises more than 1,000
     * minds at x11.
     *
     * <p>The number is the partition's, not a round one. At x11 the catalog
     * eco population is 5,060 across six regions, so one region's fold is
     * ~843 residents and the measured worsts sit just under it — 834 at seed
     * 42, 758 at seed 7. The bound is set above any single region and well
     * below two, because the failure it exists to catch is a change of KIND:
     * two regions sweeping back on the same tick, which is exactly what the
     * coverage target of #521 makes likely. A storm of 1,600 is a different
     * event from a storm of 850, and only the first one is news. Creeping
     * along behind one region's population would make this a thermometer
     * instead of an alarm.
     */
    static final int BOUND_MINDS_PER_TICK = 1_000;

    /** Ticks dropped from the quiet baseline: JIT and boot settle out of the sample. */
    private static final int WARMUP_TICKS = 1_000;

    private static final Pattern UNPARK =
            Pattern.compile("déjà vu in (.+) — (\\d+) residents re-materialize");
    private static final Pattern PARK =
            Pattern.compile("LOD: (.+) parks — (\\d+) residents fold into statistics");

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && "--selfcheck".equals(args[0])) {
            selfcheck();
            return;
        }
        int ticks = args.length > 0 ? Integer.parseInt(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : JUDGED_SCALE;

        String refusal = Config.scaleRefusal(scale);
        if (refusal != null) {
            System.err.println(refusal);
            System.exit(Probes.Outcome.REFUSED.code());
        }
        Config.setEcoScale(scale); // the dial's only door, written once (#882)

        Drain drain = new Drain();
        Simulation sim = new Simulation(seed, drain, null);
        var world = Probes.world(sim);

        long[] wall = new long[ticks];
        int[] materialised = new int[ticks];
        int[] folded = new int[ticks];
        List<String> events = new ArrayList<>();
        long lines = 0;

        for (int i = 0; i < ticks; i++) {
            drain.clear();
            long t0 = System.nanoTime();
            sim.tickOnce();
            wall[i] = System.nanoTime() - t0;
            long t = world.tick();
            for (String line : drain.take()) {
                Matcher up = UNPARK.matcher(line);
                if (up.find()) {
                    int minds = Integer.parseInt(up.group(2));
                    materialised[i] += minds;
                    lines++;
                    events.add("UNPARK t=" + t + " zone=\"" + up.group(1) + "\" minds=" + minds);
                    continue;
                }
                Matcher pk = PARK.matcher(line);
                if (pk.find()) {
                    int minds = Integer.parseInt(pk.group(2));
                    folded[i] += minds;
                    events.add("PARK t=" + t + " zone=\"" + pk.group(1) + "\" minds=" + minds);
                }
            }
        }

        // The quiet baseline: post-warmup ticks that neither folded nor
        // re-materialised anything. The storm is measured against the city it
        // interrupted, not against a mean it is itself inside of.
        long[] quiet = new long[ticks];
        int q = 0;
        for (int i = WARMUP_TICKS; i < ticks; i++) {
            if (materialised[i] == 0 && folded[i] == 0) {
                quiet[q++] = wall[i];
            }
        }
        long[] sorted = Arrays.copyOf(quiet, q);
        Arrays.sort(sorted);
        long median = q == 0 ? 0 : sorted[q / 2];
        long p99 = q == 0 ? 0 : sorted[Math.min(q - 1, (int) ((long) q * 99 / 100))];
        long max = q == 0 ? 0 : sorted[q - 1];
        long sum = 0;
        for (int i = 0; i < q; i++) {
            sum += sorted[i];
        }
        long mean = q == 0 ? 0 : sum / q;

        int worst = 0;
        int worstAt = -1;
        for (int i = 0; i < ticks; i++) {
            if (materialised[i] > worst) {
                worst = materialised[i];
                worstAt = i;
            }
        }

        for (String e : events) {
            System.out.println(e);
        }
        System.out.println("QUIET ticks=" + q + " median_ns=" + median + " mean_ns=" + mean
                + " p99_ns=" + p99 + " max_ns=" + max + " warmup=" + WARMUP_TICKS);
        for (int i = 0; i < ticks; i++) {
            if (materialised[i] > 0 || folded[i] > 0) {
                System.out.println("TICKCOST t=" + (i + 1)
                        + " folded=" + folded[i] + " materialised=" + materialised[i]
                        + " wall_ns=" + wall[i] + " x_quiet_median=" + ratio(wall[i], median));
            }
        }
        System.out.println("STORM scale=" + scale + " seed=" + seed + " ticks=" + ticks
                + " worst_minds=" + worst
                + " at_tick=" + (worstAt < 0 ? 0 : worstAt + 1)
                + " wall_ns=" + (worstAt < 0 ? 0 : wall[worstAt])
                + " x_quiet_median=" + (worstAt < 0 ? "0.00" : ratio(wall[worstAt], median))
                + " entities_end=" + sim.aliveEntities());

        // A parse that lost a line would print a calm city. The world counts
        // its own unpark events; if the two disagree, nothing above is worth
        // reading and the run says so instead of verdicting.
        System.out.println("CHECK unpark_events=" + world.unparks() + " unpark_lines=" + lines);
        System.out.println(verdict(worst, scale, world.unparks(), lines));
    }

    /**
     * The whole judgement, in one pure function of four integers, so that all
     * four of its answers are reachable without a universe. Two of them are
     * otherwise unreachable at any seed this repository has run: the city
     * would have to grow past the bound, or the log grammar would have to
     * change under the parser. AllocMeter took the same route for the same
     * reason (#906) — an unexercised guard is a promise, not a lock.
     */
    static String verdict(int worst, int scale, long events, long lines) {
        if (events != lines) {
            return "VERDICT UNPARK_STORM_UNREAD events=" + events + " lines=" + lines;
        }
        if (scale != JUDGED_SCALE) {
            return "VERDICT UNPARK_STORM_UNJUDGED scale=" + scale
                    + " reason=bound_is_stated_at_x" + JUDGED_SCALE;
        }
        return (worst <= BOUND_MINDS_PER_TICK ? "VERDICT UNPARK_STORM_BOUNDED worst="
                : "VERDICT UNPARK_STORM_UNBOUNDED worst=") + worst
                + " bound=" + BOUND_MINDS_PER_TICK;
    }

    /** The four verdicts, driven directly, with no ticks and no seed. */
    private static void selfcheck() {
        boolean ok = true;
        ok &= expect("measured_worst", verdict(834, 11, 2, 2),
                "VERDICT UNPARK_STORM_BOUNDED worst=834 bound=1000");
        ok &= expect("on_the_bound", verdict(1_000, 11, 2, 2),
                "VERDICT UNPARK_STORM_BOUNDED worst=1000 bound=1000");
        ok &= expect("one_over", verdict(1_001, 11, 2, 2),
                "VERDICT UNPARK_STORM_UNBOUNDED worst=1001 bound=1000");
        ok &= expect("wrong_scale", verdict(834, 1, 2, 2),
                "VERDICT UNPARK_STORM_UNJUDGED scale=1 reason=bound_is_stated_at_x11");
        ok &= expect("lost_a_line", verdict(834, 11, 2, 1),
                "VERDICT UNPARK_STORM_UNREAD events=2 lines=1");
        System.out.println("SELFCHECK bound=" + BOUND_MINDS_PER_TICK + " scale=" + JUDGED_SCALE);
        System.out.println(ok ? "SELFCHECK VERDICT GUARDS_FIRE" : "SELFCHECK VERDICT GUARD_DEAD");
    }

    private static boolean expect(String name, String got, String want) {
        boolean ok = want.equals(got);
        System.out.println("CASE " + name + " " + (ok ? "OK" : "BAD got=\"" + got + "\"")
                + " want=\"" + want + "\"");
        return ok;
    }

    private static String ratio(long value, long base) {
        return base == 0 ? "0.00" : String.format(Locale.ROOT, "%.2f", (double) value / base);
    }

    /**
     * The narrative, held in memory instead of on a terminal. The daemon
     * emits through the root's sink; a probe that wants the FATE line has to
     * be the thing on the other end of it. Read-only in the sense the probe
     * contract means: it queues nothing, it answers nothing the world asks,
     * and the world cannot tell it is there.
     */
    private static final class Drain extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 16);

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
        }

        void clear() {
            buffer.reset();
        }

        /** Whole lines only: the root ends every line with an explicit \n. */
        List<String> take() {
            String text = buffer.toString(StandardCharsets.UTF_8);
            return text.isEmpty() ? List.of() : List.of(text.split("\n"));
        }
    }

    private UnparkStorm() {}
}
