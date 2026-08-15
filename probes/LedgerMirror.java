import matrix.Simulation;
import matrix.core.Config;
import matrix.entities.Pill;
import matrix.realworld.NeuralLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Probe: does every AnomalyLedger delta equal the residue mirror?
 *
 * The v3 verification skeptic's strongest instrument, kept per Ag9: an
 * independently computed mirror — the sum of per-pill residue over links
 * that are open with a living avatar — must account for every positive
 * ledger movement. A closed or dead link accruing ANYWHERE breaks the
 * equality; that is exactly the ghost-HARDLINE class of bug, and with
 * this probe on the bench it can never return unnoticed.
 *
 * Negative movements are resets (treaty, reload) and print as RESET
 * lines. A reset is not an excuse to look away (#978): the treaty and the
 * reload both zero the ledger from the machine node, which runs AHEAD of
 * the wheel, so whatever stands on the ledger when the tick ends accrued
 * after the reset, from zero. The post-tick balance is therefore the
 * tick's mirror with the subtraction already done — an ABSOLUTE check,
 * the only one in the run — and it is compared and counted like any
 * other. The RESET line carries both numbers so the check is visible.
 * That tick is the busiest one several universes have: at seed 40 the
 * treaty lands on an accrual window and 325 rebuilds inside it while six
 * links contribute their window and close clean, which is exactly the
 * shape #863 fixed everywhere the probe was still looking.
 *
 * Déjà-vu spikes had one door — the ops console, never a headless
 * run — until #133 gave them a second, mechanical one: every Unpark
 * accrues DEJA_RESIDUE_SPIKE (the D-022 precedent). The mirror models it
 * from the world's own unpark count, read through the public accessor
 * before and after each tick — a legitimate source accounted, so an
 * unexplained positive residual stays what it always was: a bug.
 *
 * The clean exit is the third accounted source (#863). A walk-out that
 * lands on an accrual window contributes and THEN closes, so a mirror
 * that read openness AFTER the tick was short by exactly that link's
 * rate — a self-inflicted line the javadoc used to apologise for in
 * prose while the verdict counted it as a bug. Openness is now read
 * where the wheel reads it: BEFORE the tick. The node order is what
 * makes that exact rather than approximate. Nothing closes a
 * real-world link ahead of RealWorldSystem's own accrual loop — the
 * two doors that close one clean, self-substantiation and the treaty,
 * both run at or after it — and every avatar death lands in the
 * machine node, which runs before it. So open-at-tick-start is
 * open-at-accrual, and dead-at-tick-end is dead-at-accrual.
 *
 * THE VERDICT IS NOT WRITTEN HERE, and that is the whole point of #1130.
 * What this instrument establishes is a CONTRACT — every nonzero ledger
 * delta is explained by the three accounted sources above, or it is an
 * anomaly — and the contract's current standing is printed by the run
 * that measures it: `--sweep A..B` prints one line, `bench.sh` greps
 * that line exactly, and every push regenerates it. A sentence cannot be
 * kept honest by care; a verdict line is honest by construction.
 *
 * Recorded before the verdict, kept unedited and FALSE as of 2026-08-15:
 *
 *   "Verdict: LEDGER_ANOMALIES=0 at seeds 42, 7 and 9 over the full arc
 *    (verification round, 2026-08-11; re-verified with the unpark source
 *    modeled for the P2 parking film; #863 modelled the clean exit and
 *    swept seeds 0..49 clean, 2026-08-13; #978 opened the reset tick and
 *    swept seeds 0..59 clean, 2026-08-14)."
 *
 * It was almost certainly true when it was written — and `4316525`, one
 * day later, broke seed 7 with nothing left in the repository able to
 * notice. Measured at 57bbf96: seeds 0..59 hold SEVEN breaks —
 * 4, 7, 8, 13, 34, 49 and 52 — one anomaly each. The defect is #1090 and
 * this record does not close it; what this record closes is the sentence
 * that kept it invisible.
 *
 * Usage: java -cp out:probes/out LedgerMirror [ticks] [seed]
 *        java -cp out:probes/out LedgerMirror --sweep A..B [ticks]
 */
public final class LedgerMirror {

    /** One universe's verdict: the seed, and how many ticks the mirror could not explain. */
    public record Result(long seed, long anomalies) {}

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--sweep")) {
            sweep(args);
            return;
        }
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
        // The verdict line is printed HERE rather than inside `run`, because
        // `sweep` calls `run` sixty times and a helper that exits would end the
        // sweep on its first world (#1214). `run` still prints its MIRROR line;
        // the verdict and the exit code leave together, from one place.
        Result r = run(seed, ticks, true);
        Probes.leave("LEDGER_ANOMALIES=" + r.anomalies(), r.anomalies() == 0);
    }

    /**
     * The sweep, as a line that is regenerated rather than remembered.
     *
     * <p>This exists because the claim it replaces was prose. The javadoc
     * certified seeds 0..59 clean on 2026-08-14 and four of the first twenty
     * broke the next day, one commit later — nothing recomputed the sentence,
     * so the one instrument able to see the break carried a written statement
     * that it did not exist (#1130). A verdict line cannot go stale: it is
     * printed by the run that produces it, and `bench.sh` greps it exactly, so
     * a seed that starts breaking, a seed that stops, and a seed that is
     * silently dropped from the range are all three the same red row.
     *
     * <p>The sweep runs the range in PARALLEL, and the history is why that
     * sentence is safe to write. It ran serially first, because two universes
     * in one JVM were not independent: {@code FlockMovement} and {@code
     * SwarmMovement} were singletons holding their neighbour buffer in an
     * instance field, so a parallel range threw {@code
     * ConcurrentModificationException} out of {@code FlockMovement.move} —
     * and, worse and quieter, seed 42 produced a DIFFERENT CHAIN with no
     * exception at all. #1147 moved both buffers to a {@code ThreadLocal} and
     * {@code probes/TwoWorlds} now judges the property in the sweep: each seed
     * run alone, then all of them concurrently, chains equal link for link.
     *
     * <p>So the parallelism here rides a guarded claim rather than an assumed
     * one. Results are collected by seed and reported in seed order, so the
     * LINE is identical to a serial sweep's; {@code --serial} runs the same
     * range one at a time, which is how a reader checks that for themselves
     * rather than believing this paragraph.
     */
    private static void sweep(String[] args) {
        String[] bounds = args[1].split("\\.\\.");
        long lo = Long.parseLong(bounds[0]);
        long hi = Long.parseLong(bounds[1]);
        boolean serial = List.of(args).contains("--serial");
        long budget = 6_000;
        for (int i = 2; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                budget = Long.parseLong(args[i]);
            }
        }
        final long ticks = budget;

        java.util.stream.LongStream range = java.util.stream.LongStream.rangeClosed(lo, hi);
        List<Result> results = (serial ? range : range.parallel())
                .mapToObj(s -> run(s, ticks, false))
                .sorted(java.util.Comparator.comparingLong(Result::seed))
                .toList();

        List<String> broken = new ArrayList<>();
        for (Result r : results) {
            if (r.anomalies() > 0) {
                broken.add(Long.toString(r.seed()));
                System.out.println("SWEEP seed=" + r.seed() + " anomalies=" + r.anomalies() + " BROKEN");
            }
        }
        System.out.println("LEDGER_SWEEP seeds=" + lo + ".." + hi + " ticks=" + ticks
                + " clean=" + (results.size() - broken.size())
                + " broken=" + broken.size()
                + " at " + (broken.isEmpty() ? "-" : String.join(",", broken)));
    }

    /**
     * One universe, mirrored tick by tick. Printing is optional so a sweep stays
     * one line per break.
     *
     * <p>The reflective accessors are checked, and a sweep reads them from
     * inside a stream: an accessor that cannot be reached is a broken probe,
     * not a finding about the world, so it is wrapped rather than swallowed and
     * the run dies where it happened.
     */
    static Result run(long seed, long ticks, boolean print) {
        Simulation sim = new Simulation(seed, null, null);
        var world = reflect(() -> Probes.world(sim));
        var links = reflect(() -> Probes.links(reflect(() -> Probes.realWorld(sim))));

        long anomalies = 0;
        long prev = world.ledger().balance();
        // The wheel's own view of the register: refilled each tick, never
        // reallocated.
        List<NeuralLink> openAtAccrual = new ArrayList<>();
        for (long t = 0; t < ticks; t++) {
            // Membership AND openness are snapshotted BEFORE the tick, because
            // both are the accrual point's truth: a link born this tick — the
            // One's — joins after the wheel turned, and a link that closes
            // during the tick closes after it has already contributed.
            // Liveness is read AFTER, for the same reason with the sign
            // flipped: a mind killed this tick was killed in the machine node,
            // which runs ahead of the wheel, so it was already dead at accrual.
            openAtAccrual.clear();
            for (NeuralLink l : links) {
                if (!l.closed()) {
                    openAtAccrual.add(l);
                }
            }
            long unparksBefore = world.unparks();
            sim.tickOnce();
            long now = world.ledger().balance();
            long delta = now - prev;
            prev = now;
            if (delta == 0) {
                continue;
            }
            long mirror = 0;
            // Link residue accrues only on the wheel's window — nodes run at
            // world.tick()+1, so the window is (t+1) % ACCRUE == 0 as the
            // probe reads t post-tick. Off-window, links owe nothing, and
            // crediting them anyway would hide an off-window accrual bug —
            // the unpark spike (#133) made off-window deltas real and
            // exposed the unconditional credit.
            if ((world.tick() + 1) % Config.ACCRUE_EVERY_TICKS == 0) {
                for (NeuralLink l : openAtAccrual) {
                    if (l.avatar.alive) {
                        mirror += l.avatar.pill == Pill.RED ? Config.RESIDUE_RED : Config.RESIDUE_BLUE;
                    }
                }
            }
            // The second legitimate source (#133): each unpark this tick spiked the ledger.
            mirror += (world.unparks() - unparksBefore) * Config.DEJA_RESIDUE_SPIKE;
            if (delta < 0) {
                // The ledger was zeroed inside this tick, ahead of the wheel,
                // so what it holds now is what accrued after the reset: the
                // balance IS the mirror, and the check is absolute rather than
                // differential. Both numbers on the line, so the reader can
                // see it happened.
                if (print) {
                    System.out.println("RESET t=" + world.tick()
                            + " to=" + now + " mirror=" + mirror);
                }
                if (now != mirror) {
                    anomalies++;
                    if (print) {
                        System.out.println("ANOMALY t=" + world.tick()
                                + " after_reset=" + now + " mirror=" + mirror);
                    }
                }
                continue;
            }
            if (delta != mirror) {
                anomalies++;
                if (print) {
                    System.out.println("ANOMALY t=" + world.tick()
                            + " delta=" + delta + " mirror=" + mirror);
                }
            }
        }
        if (print) {
            System.out.println("MIRROR seed=" + seed + " ticks=" + ticks
                    + " final_balance=" + world.ledger().balance()
                    + " unparks=" + world.unparks());
        }
        return new Result(seed, anomalies);
    }

    /** A reflective accessor, called where a checked exception cannot travel. */
    private interface Reflective<T> {
        T get() throws ReflectiveOperationException;
    }

    private static <T> T reflect(Reflective<T> accessor) {
        try {
            return accessor.get();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("probe accessor unreachable", e);
        }
    }

    private LedgerMirror() {}
}
