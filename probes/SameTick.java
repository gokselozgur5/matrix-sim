import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Probe: the keeper the root door's same-tick guarantee never had (#830).
 *
 * The door promises that a liberation queued in tick T is in Zion's census
 * before tick T ends. For six hundred commits nothing checked it. The
 * `ZION` line prints every 100 ticks, so a one-tick slip is invisible to
 * it; the census is outside the digest chain (the #187 precedent), so the
 * leash cannot see it either. A guarantee with no keeper is a wish.
 *
 * The falsifiable form of the promise, and the one this probe judges:
 * `RealWorld.pendingLiberations` is EMPTY at every tick boundary. Nothing
 * queued in tick T survives into T+1 — and since the drain is the census's
 * only feeder, "drained inside T" is "absorbed inside T". The check has one
 * stated blind spot: it looks between ticks, so it cannot see the order of
 * events INSIDE a tick, only that the tick closed with the queue empty.
 *
 * What it is armed against, concretely: moving the drain out of
 * `Simulation.tickOnce` and into `Zion.tick` — the refactor the door used
 * to invite. Zion's slot ticks BEFORE the treaty's `optOut` fires, so a
 * slot-side drain leaves the treaty's liberations queued at the end of
 * their own tick, and this probe says so with the tick number.
 *
 * A vacuous pass is also a failure: a run where nobody is ever freed proves
 * nothing, so the census must grow at least once or the verdict is
 * `NO_LIBERATIONS`. Seed 42 at 6,000 ticks frees six at the treaty.
 *
 * Judged in {@code probes/bench.sh} — one row, exact-line grep on
 * `VERDICT SAME_TICK_ABSORB`, which is what makes this a lock rather than a
 * program somebody remembers to run.
 *
 * One command:
 *   java -cp out:probes/out SameTick 6000
 *
 * Usage: java -cp out:probes/out SameTick [ticks] [seed]
 */
public final class SameTick {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        TickBuf buf = new TickBuf();
        Simulation sim = new Simulation(seed, buf, null);
        var realWorld = Probes.realWorld(sim);
        var zion = Probes.zion(sim);
        buf.drain(); // the boot banner belongs to no tick

        int census = zion.census().size();
        int lateTicks = 0;
        long firstLate = -1;
        int lateMost = 0;
        StringBuilder growth = new StringBuilder();
        int grewTimes = 0;

        for (long t = 1; t <= ticks; t++) {
            sim.tickOnce();
            String slice = buf.drain();
            int pending = Probes.pendingLiberations(realWorld).size();
            if (pending > 0) {
                lateTicks++;
                lateMost = Math.max(lateMost, pending);
                if (firstLate < 0) {
                    firstLate = t;
                }
            }
            int now = zion.census().size();
            if (now > census) {
                grewTimes++;
                if (growth.length() > 0) {
                    growth.append(',');
                }
                // The tick the census grew in, and the door it grew through —
                // the log slice of that same tick names it, which is the whole
                // point: growth and cause are witnessed in one tick's window.
                growth.append(t).append("+").append(now - census).append(door(slice));
                census = now;
            }
        }

        System.out.println("SAMETICK seed=" + seed + " ticks=" + ticks
                + " census=" + census + " growth_ticks=" + grewTimes
                + " late_ticks=" + lateTicks
                + " first_late=" + firstLate
                + " max_stranded=" + lateMost);
        System.out.println("SAMETICK_GROWTH " + (growth.length() == 0 ? "none" : growth));
        if (census == 0) {
            // Not a pass and not a failure: no mind was freed in this run, so the
            // absorb rule was never asked. Exit 2 (#1138).
            Probes.leave("VERDICT NO_LIBERATIONS", false, false);
        }
        Probes.leave(lateTicks == 0
                ? "VERDICT SAME_TICK_ABSORB"
                : "VERDICT LATE_ABSORB", lateTicks == 0, true);
    }

    /** The door named by this tick's own lines, or nothing — context, never the verdict. */
    private static String door(String slice) {
        boolean treaty = slice.contains("open door tally:");
        boolean selfsub = slice.contains("self-substantiation:");
        if (treaty && selfsub) {
            return "(treaty+selfsub)";
        }
        if (treaty) {
            return "(treaty)";
        }
        return selfsub ? "(selfsub)" : "";
    }

    /** One tick's worth of emitted lines, then empty again — the buffer never grows without bound. */
    private static final class TickBuf extends ByteArrayOutputStream {
        String drain() {
            String s = new String(buf, 0, count, StandardCharsets.UTF_8);
            reset();
            return s;
        }
    }

    private SameTick() {}
}
