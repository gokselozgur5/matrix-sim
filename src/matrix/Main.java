package matrix;

import matrix.core.Digest;
import matrix.core.Snapshot;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Daemon bootstrap: flags, wiring, run loop, exit summary. The ONLY class
 * allowed to touch the wall clock — and only for the PERF harness and
 * interactive pacing, never for domain logic (D-010's scope is the domain).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        // Before anything prints: the instrument lines are a byte contract
        // (D-020) and the JVM took their charset from the environment.
        Streams.utf8();
        long seed = 42;
        long ticks = 2_000;
        int scale = 1;
        boolean headless = false;
        boolean selftest = false;
        boolean bench = false;
        String follow = null;
        long sinkAt = -1;
        long sinkEvery = -1;
        long reloadAt = -1;
        String chronosPath = null;
        String replayPath = null;
        String expectPath = null;
        String auditPath = null;
        Long snapshotAt = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Long.parseLong(args[++i]);
                case "--scale" -> scale = Integer.parseInt(args[++i]);
                case "--headless" -> headless = true;
                case "--selftest" -> selftest = true;
                case "--bench" -> bench = true;
                case "--follow" -> follow = args[++i];
                case "--sink-at" -> sinkAt = Long.parseLong(args[++i]);
                // Judged where it is read, not in the refusal block below:
                // -1 is this flag's "off", so once the loop is over a user's
                // own -1 is indistinguishable from the default and would run
                // as a silent no-op — the shape #791 named on --reload-at.
                case "--sink-every" -> {
                    sinkEvery = Long.parseLong(args[++i]);
                    if (sinkEvery < 1) {
                        System.err.println("--sink-every " + sinkEvery
                                + " is not a cadence — the period is a count of ticks, at least 1");
                        System.exit(2);
                    }
                }
                case "--reload-at" -> reloadAt = Long.parseLong(args[++i]);
                case "--chronos" -> chronosPath = args[++i];
                case "--replay" -> replayPath = args[++i];
                case "--expect" -> expectPath = args[++i];
                case "--audit" -> auditPath = args[++i];
                case "--snapshot-at" -> snapshotAt = Long.parseLong(args[++i]);
                case "--help" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("unknown flag: " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }

        String scaleRefusal = matrix.core.Config.scaleRefusal(scale);
        if (scaleRefusal != null) {
            System.err.println(scaleRefusal);
            System.exit(2);
        }
        if (scale != 1 && (replayPath != null || chronosPath != null)) {
            // The genesis line carries seed and version, not a scale: a scaled
            // recording could not be folded back. Scale is a live-run dial.
            System.err.println("--scale rides live runs only — the chronos record knows no scale");
            System.exit(2);
        }
        matrix.core.Config.setEcoScale(scale);
        if (expectPath != null && replayPath == null) {
            System.err.println("--expect rides with --replay");
            usage();
            System.exit(2);
        }
        if (replayPath != null && chronosPath != null) {
            System.err.println("--chronos records live runs; the fold replays with the recorder off");
            System.exit(2);
        }
        if (auditPath != null && (replayPath != null || expectPath != null || chronosPath != null || headless)) {
            System.err.println("--audit walks the record alone — it boots no universe and folds nothing");
            System.exit(2);
        }
        if (snapshotAt != null && (replayPath != null || !headless)) {
            System.err.println("--snapshot-at rides with --headless — a live run, not the fold");
            usage();
            System.exit(2);
        }
        if (sinkAt >= 0 && snapshotAt != null) {
            System.err.println("--sink-at and --snapshot-at are separate scenarios — run them separately");
            usage();
            System.exit(2);
        }
        if (sinkAt >= 0 && !headless) {
            System.err.println("--sink-at is a headless scenario flag — add --headless");
            usage();
            System.exit(2);
        }
        if (sinkEvery > 0 && snapshotAt != null) {
            System.err.println("--sink-every and --snapshot-at are separate scenarios — run them separately");
            usage();
            System.exit(2);
        }
        if (sinkEvery > 0 && !headless) {
            System.err.println("--sink-every is a headless scenario flag — add --headless");
            usage();
            System.exit(2);
        }
        // Judged here rather than at the parse site, because --ticks may be
        // read after this flag. A period longer than the run files nothing:
        // that is a scenario the operator did not get, so it is refused like
        // --snapshot-at's out-of-range tick and not run as a quiet no-op.
        if (sinkEvery > ticks) {
            System.err.println("--sink-every " + sinkEvery + " never fires in a " + ticks
                    + "-tick run — the period must fall inside the run");
            System.exit(2);
        }
        if (snapshotAt != null && (snapshotAt < 0 || snapshotAt > ticks)) {
            System.err.println("--snapshot-at " + snapshotAt + " lies outside the run (0.." + ticks + ")");
            System.exit(2);
        }
        if (selftest) {
            System.exit(selftest(seed, ticks));
        }
        if (bench) {
            System.exit(bench(seed));
        }
        if (auditPath != null) {
            // D-023 stage 5 slice: the log answers for itself — no universe
            // booted, no wall clock, the exit code is the verdict.
            System.exit(ReplayHarness.audit(auditPath));
        }
        if (replayPath != null) {
            // D-023 stage 2: no PERF line here — the fold is judged by the
            // chain, never the wall clock; seed comes from the genesis line.
            System.exit(ReplayHarness.run(replayPath, expectPath, ticks));
        }
        if (headless) {
            runHeadless(seed, ticks, follow, chronosPath, sinkAt, sinkEvery, reloadAt, snapshotAt);
            return;
        }
        runInteractive(seed, follow, chronosPath);
    }

    /**
     * D-023 stage 1: the black box. Truncates — one file, one run, the
     * genesis line marks the start. Live runs only; selftest and bench
     * are in-process double-runs and stay quiet by canon.
     */
    private static OutputStream openChronos(String path) throws Exception {
        return path == null ? null : new FileOutputStream(path);
    }

    /**
     * The kernel of CI without a build tool (accepted D-009 spark): two runs,
     * one verdict. Honors --ticks so the gate can cover the full v3 arc
     * (skeptic finding: 2,000 hardcoded ticks left the finale untested);
     * the default stays 2,000 for the fast pre-push check.
     *
     * <p>Honors --scale too, and has since #136 — the dial is written
     * before this runs, so the double-run happens in whatever world it
     * built. What it did not do was say so: every verdict line was the
     * canonical one, because chain_length counts DIGEST links (one per 100
     * ticks) and not minds. A scaled proof and an unscaled proof printed the
     * same twenty characters, so the line #518 nominates as the rung's
     * determinism evidence could not be told from a run that never touched
     * the dial. {@link matrix.core.Config#scaleTag} appends the world; at
     * scale 1 it appends nothing, so the canonical verdict — the one
     * locks.yml, tools/release.sh and every past PR body quote — keeps its
     * bytes.
     */
    private static int selftest(long seed, long ticks) {
        // The economy's ordering is a lock, not a comment (#382): the
        // miracle must outrank every other disbelief item, and a retune
        // that demotes it fails here instead of in a season's worth of runs.
        System.out.println(matrix.realworld.Bond.retailOrderLine());
        // The ring hunt's displacement law is a lock too (#825): the gait
        // maxima are compile-time data, so this needs no world and no tick,
        // and a gait that outgrew the bound fails here rather than arriving
        // as a slow arc somebody blames on the box.
        System.out.println(matrix.core.Config.huntBoundLine());
        // The homecoming dial's gate is a lock too (#882): the field is
        // private, and the setter still refuses what --scale refuses and
        // refuses any write once the world has read it.
        System.out.println(matrix.core.Config.dialLockLine());
        Simulation first = new Simulation(seed, null, null);
        List<Digest> a = first.run(ticks);
        List<Digest> b = new Simulation(seed, null, null).run(ticks);
        // The census of the run that produced chain a. A divergence is
        // reported in the world it happened in, so the tag rides the FAIL
        // lines too — a red line that cannot name its scale is the exact
        // thing this method was fixed for, and red is when it matters most.
        String world = matrix.core.Config.scaleTag(first.aliveEntities());
        if (a.size() != b.size()) {
            System.out.println("SELFTEST FAIL chain_length " + a.size() + " vs " + b.size() + world);
            return 1;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                System.out.println("SELFTEST FAIL first_divergence_at_tick "
                        + a.get(i).tick() + world);
                return 1;
            }
        }
        System.out.println("SELFTEST OK seed=" + seed + " ticks=" + ticks
                + " chain_length=" + a.size() + world);
        return 0;
    }

    /**
     * D-027's Confirmation, executable at last: measure the budget table on
     * this box and print a verdict per row. Budgets per the ADR erratas —
     * steady state >= 100 ticks/s at ecosystem scale, and since #384 the
     * full v3 arc (6,000 ticks, birth to reboot to second birth) is judged
     * by that SAME rate over the arc, not by an absolute 30 s deadline.
     * A deadline measures the box as much as the code: it falls off a
     * cliff under external load while a rate degrades proportionally, so
     * pristine main used to fail its own arc row on a loaded box (57.53 s
     * measured here; 53.49 s in #205's referee table; 32.1-49.4 s in
     * #209) in the same run where the steady row passed comfortably — and
     * tools/release.sh, which refuses on any red, could not cut a release
     * from a healthy main. One floor, two rows, same units: the box's
     * slowness now shows up identically in both. The 30 s figure is not
     * lost, it is demoted to what it always was — {@code ref_box_s}, the
     * reference-box expectation D-027 recorded (~200 ticks/s canonical),
     * printed for the record while the rate carries the verdict. Under
     * {@code --scale} (#136) the steady floor stays 100 — that IS the
     * retargeted 5,000-entity row — while the scaled arc is still judged
     * by completion: nobody has measured a scaled arc rate, and a floor
     * nobody calibrated is not a budget.
     */
    private static int bench(long seed) {
        boolean scaled = matrix.core.Config.ecoScale() != 1;
        long t0 = System.nanoTime();
        Simulation steady = new Simulation(seed, null, null);
        steady.run(2_000);
        double steadyS = (System.nanoTime() - t0) / 1e9;
        long tps = Math.round(2_000 / Math.max(steadyS, 1e-9));
        boolean steadyOk = tps >= 100;
        System.out.print(String.format(Locale.ROOT,
                "BENCH steady seed=%d ticks=2000 entities=%d wall_s=%.2f ticks_per_s=%d floor=100 %s\n",
                seed, steady.aliveEntities(), steadyS, tps, steadyOk ? "PASS" : "FAIL"));

        long t1 = System.nanoTime();
        Simulation arc = new Simulation(seed, null, null);
        arc.run(6_000);
        double arcS = (System.nanoTime() - t1) / 1e9;
        long arcTps = Math.round(6_000 / Math.max(arcS, 1e-9));
        boolean arcOk = scaled || arcTps >= 100; // reaching here IS the scaled row's completion
        System.out.print(String.format(Locale.ROOT,
                "BENCH full_arc seed=%d ticks=6000 entities=%d wall_s=%.2f %s %s\n",
                seed, arc.aliveEntities(), arcS,
                scaled ? "judged=completion (the rate floor is canonical-scale)"
                        : "ticks_per_s=" + arcTps + " floor=100 ref_box_s=30",
                arcOk ? "PASS" : "FAIL"));

        // The ring hunt's linear term, measured rather than assumed (#825).
        // The bound row reads compile-time data — it is the same arithmetic
        // --selftest throws on, printed here because this is the budget table
        // and that is where a reader looks for it. The ledger row is the
        // backstop the table cannot provide: it catches a mover that reaches
        // the ledger through a door no gait declares.
        matrix.core.Config.GaitReach widest = matrix.core.Config.huntGaitReaches().get(0);
        int headroom = matrix.core.Config.HUNT_DISP_BOUND_CM - widest.maxDisplacementCm();
        boolean boundOk = headroom >= 0;
        System.out.print(String.format(Locale.ROOT,
                "BENCH hunt_bound bound_cm=%d widest=%s reach_cm=%d headroom_cm=%d floor=0 %s\n",
                matrix.core.Config.HUNT_DISP_BOUND_CM, widest.mover(), widest.maxDisplacementCm(),
                headroom, boundOk ? "PASS" : "FAIL"));

        int ceiling = matrix.core.Config.huntLedgerCeiling();
        int peak = arc.farMoverPeak();
        boolean ledgerOk = peak <= ceiling;
        System.out.print(String.format(Locale.ROOT,
                "BENCH far_movers seed=%d ticks=6000 peak=%d ceiling=%d %s\n",
                seed, peak, ceiling, ledgerOk ? "PASS" : "FAIL"));

        boolean pass = steadyOk && arcOk && boundOk && ledgerOk;
        System.out.print("BENCH VERDICT " + (pass ? "PASS" : "FAIL")
                + " (budgets: D-027 + erratas; digests untouched — bench runs quiet)\n");
        return pass ? 0 : 1;
    }

    /**
     * Headless is the scenario runner: {@code --sink-at T} files the #119
     * loss right before tick T, {@code --sink-every N} files one every N
     * ticks (#905); {@code --chronos} records the run (D-023 stage 1);
     * {@code --snapshot-at T} pauses after tick T, retains the
     * walk, and lets the emitted DIGEST judge its own preimage (stage 3;
     * the split changes nothing — run(a) + run(b) is run(a + b)). Snapshot
     * mode and sink mode are separate scenarios by design.
     *
     * <p>A cadence, not a second single shot, because the thing #806 fixed
     * only exists past the third laydown and the third laydown only exists
     * past the second loss. One tick number cannot state that scenario, so
     * for two units it was stated in prose and folded from a hand-forged
     * chronos record instead of run.
     */
    private static void runHeadless(long seed, long ticks, String follow, String chronosPath,
            long sinkAt, long sinkEvery, long reloadAt, Long snapshotAt) throws Exception {
        try (OutputStream chronosSink = openChronos(chronosPath)) {
            Simulation sim = new Simulation(seed, System.out, follow, chronosSink);
            long start = System.nanoTime();
            if (snapshotAt != null) {
                List<Digest> chain = sim.run(snapshotAt);
                Snapshot snap = sim.snapshotNow();
                System.out.print(snap.format() + "\n");
                for (Digest d : chain) {
                    if (d.tick() == snap.tick()) {
                        System.out.print("SNAPSHOT_MATCHES_DIGEST="
                                + d.sha256().equals(snap.sha256Hex()) + "\n");
                    }
                }
                sim.run(ticks - snapshotAt);
            } else {
                for (long t = 1; t <= ticks; t++) {
                    // One order per tick even when both doors point at the
                    // same tick: Zion.orderSink is a latch, so a second file
                    // in the same gap sinks nothing extra, and recording it
                    // would put two losses in the chronos record where the
                    // world executed one.
                    if (t == sinkAt || (sinkEvery > 0 && t % sinkEvery == 0)) {
                        sim.recordCommand("sink");
                        sim.commandSink();
                    }
                    if (t == reloadAt) {
                        sim.recordCommand("reload");
                        sim.commandReload();
                    }
                    sim.tickOnce();
                }
            }
            long elapsedNs = System.nanoTime() - start;
            long perTickNs = Math.max(1, elapsedNs / Math.max(1, ticks));
            long ticksPerSecond = 1_000_000_000L / perTickNs;
            // far_max/far_ceiling append (#825, D-020 additive evolution): the
            // ring hunt's one remaining linear term, and the ceiling it is
            // judged against. Both are deterministic — the rate beside them
            // is the only thing on this line that measures the box.
            System.out.print(String.format(Locale.ROOT,
                    "PERF ticks_per_s=%d entities=%d ticks=%d far_max=%d far_ceiling=%d\n",
                    ticksPerSecond, sim.aliveEntities(), ticks,
                    sim.farMoverPeak(), matrix.core.Config.huntLedgerCeiling()));
        }
    }

    private static void runInteractive(long seed, String follow, String chronosPath) throws Exception {
        try (OutputStream chronosSink = openChronos(chronosPath)) {
            runConsole(seed, follow, chronosSink);
        }
    }

    private static void runConsole(long seed, String follow, OutputStream chronosSink) throws Exception {
        Simulation sim = new Simulation(seed, System.out, follow, chronosSink);
        ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    commands.add(line.trim().toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();

        boolean paused = false;
        int speed = 1;
        while (true) {
            String cmd;
            while ((cmd = commands.poll()) != null) {
                String[] parts = cmd.split("\\s+");
                switch (parts[0]) {
                    // The six commands that touch the universe enter the chronos
                    // record before dispatch, refusals included (D-023 stage 1).
                    // Pacing (pause, speed) steers the console, not the universe.
                    case "red" -> { sim.recordCommand(cmd); sim.commandRed(); }
                    case "agent" -> { sim.recordCommand(cmd); sim.commandAgent(); }
                    case "smith" -> { sim.recordCommand(cmd); sim.commandSmith(); }
                    case "deja" -> { sim.recordCommand(cmd); sim.commandDeja(); }
                    case "reload" -> { sim.recordCommand(cmd); sim.commandReload(); }
                    case "sink" -> { sim.recordCommand(cmd); sim.commandSink(); }
                    case "pause" -> paused = !paused;
                    case "speed" -> {
                        try {
                            speed = parts.length > 1 ? Math.max(1, Math.min(50, Integer.parseInt(parts[1]))) : 1;
                        } catch (NumberFormatException e) {
                            System.out.print("speed wants a number, 1-50 — the universe does not crash on typos\n");
                        }
                    }
                    case "quit", "q" -> {
                        System.out.print("hardline exit at tick " + sim.tick() + "\n");
                        return;
                    }
                    case "help" -> System.out.print("commands: red | agent | smith | deja | reload | sink | pause | speed N | quit\n");
                    default -> System.out.print("unknown command (try: help)\n");
                }
            }
            if (!paused) {
                for (int i = 0; i < speed; i++) {
                    sim.tickOnce();
                }
            }
            Thread.sleep(100);
        }
    }

    private static void usage() {
        System.out.print("""
                matrix-sim daemon (v3.0)
                  --headless          run without the ops console, then print PERF
                  --ticks N           tick budget for headless and selftest runs (default 2000)
                  --seed N            the fate of the universe (default 42)
                  --scale N           homecoming dial (#136): multiply every Bestiary population
                                      (x11 ~ 5,269 entities — the D-027 retargeted row's scale);
                                      live runs only, refused with --chronos/--replay (default 1)
                  --follow NAME       stream one pilot's dream as JSONL every 100 ticks
                  --sink-at T         scuttle the active ship in tick T's zion slot (headless scenario, #119)
                  --sink-every N      file a sink order every N ticks (headless scenario, #905): the only
                                      way to reach the fourth laydown, where the roster runs out of names
                                      and the generation mark takes over; refusals are logged, not fatal
                  --reload-at T       fire the Architect's reload right before tick T (headless scenario,
                                      #128); with --chronos the epoch seals onto the record first:
                                      snapshot marker + boundary, written BEFORE the purge
                  --chronos PATH      record genesis + inputs as JSONL (D-023 stage 1; live runs only)
                  --snapshot-at T     with --headless: after tick T, retain the digest walk and print
                                      SNAPSHOT tick/sha/bytes (D-023 stage 3); when T is a digest tick,
                                      also verify SNAPSHOT_MATCHES_DIGEST against that tick's DIGEST line
                  --replay PATH       fold a chronos recording (D-023 stage 2): re-run from its genesis
                                      with recorded commands at their ticks, print the DIGEST chain
                                      in ChainDump format (seed from the recording; honors --ticks);
                                      epoch seals are re-taken at their boundaries and verified (#128)
                  --expect PATH       with --replay: verify against a ChainDump-format digest file;
                                      run length = the dump's last tick; prints REPLAY OK/FAIL and
                                      exits 0 match / 1 divergence / 2 refused
                  --audit PATH        verdict a chronos recording's internal consistency without
                                      booting a universe (D-023 stage 5 slice, #129): genesis,
                                      monotone ticks, seals paired with boundaries in
                                      write-before-purge order, epoch arithmetic, config
                                      fingerprint vs this build (drift named, not failed);
                                      exits 0 consistent / 1 inconsistent / 2 unreadable
                  --selftest          in-process digest double-run; exit 0 iff chains match.
                                      Honors --scale, and since #518 says so: the verdict
                                      carries scale= and entities= at any dial but 1, so a
                                      scaled proof cannot be read as a canonical one
                  --bench             measure the D-027 budget table; exit 0 iff all rows pass
                interactive commands: red | agent | smith | deja | reload | sink | pause | speed N | quit
                """);
    }
}
