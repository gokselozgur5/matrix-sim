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
        long seed = 42;
        long ticks = 2_000;
        boolean headless = false;
        boolean selftest = false;
        boolean bench = false;
        String follow = null;
        long sinkAt = -1;
        long reloadAt = -1;
        String chronosPath = null;
        String replayPath = null;
        String expectPath = null;
        Long snapshotAt = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Long.parseLong(args[++i]);
                case "--headless" -> headless = true;
                case "--selftest" -> selftest = true;
                case "--bench" -> bench = true;
                case "--follow" -> follow = args[++i];
                case "--sink-at" -> sinkAt = Long.parseLong(args[++i]);
                case "--reload-at" -> reloadAt = Long.parseLong(args[++i]);
                case "--chronos" -> chronosPath = args[++i];
                case "--replay" -> replayPath = args[++i];
                case "--expect" -> expectPath = args[++i];
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

        if (expectPath != null && replayPath == null) {
            System.err.println("--expect rides with --replay");
            usage();
            System.exit(2);
        }
        if (replayPath != null && chronosPath != null) {
            System.err.println("--chronos records live runs; the fold replays with the recorder off");
            System.exit(2);
        }
        if (snapshotAt != null && (replayPath != null || !headless)) {
            System.err.println("--snapshot-at rides with --headless — a live run, not the fold");
            usage();
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
        if (replayPath != null) {
            // D-023 stage 2: no PERF line here — the fold is judged by the
            // chain, never the wall clock; seed comes from the genesis line.
            System.exit(ReplayHarness.run(replayPath, expectPath, ticks));
        }
        if (headless) {
            runHeadless(seed, ticks, follow, chronosPath, sinkAt, reloadAt, snapshotAt);
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
     */
    private static int selftest(long seed, long ticks) {
        List<Digest> a = new Simulation(seed, null, null).run(ticks);
        List<Digest> b = new Simulation(seed, null, null).run(ticks);
        if (a.size() != b.size()) {
            System.out.println("SELFTEST FAIL chain_length " + a.size() + " vs " + b.size());
            return 1;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                System.out.println("SELFTEST FAIL first_divergence_at_tick " + a.get(i).tick());
                return 1;
            }
        }
        System.out.println("SELFTEST OK seed=" + seed + " ticks=" + ticks + " chain_length=" + a.size());
        return 0;
    }

    /**
     * D-027's Confirmation, executable at last: measure the budget table on
     * this box and print a verdict per row. Budgets per the ADR erratas —
     * steady state >= 100 ticks/s at ecosystem scale; the full v3 arc
     * (6,000 ticks, birth to reboot to second birth) under 30 s.
     */
    private static int bench(long seed) {
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
        boolean arcOk = arcS < 30.0;
        System.out.print(String.format(Locale.ROOT,
                "BENCH full_arc seed=%d ticks=6000 entities=%d wall_s=%.2f bound_s=30 %s\n",
                seed, arc.aliveEntities(), arcS, arcOk ? "PASS" : "FAIL"));

        System.out.print("BENCH VERDICT " + (steadyOk && arcOk ? "PASS" : "FAIL")
                + " (budgets: D-027 + erratas; digests untouched — bench runs quiet)\n");
        return steadyOk && arcOk ? 0 : 1;
    }

    /**
     * Headless is the scenario runner: {@code --sink-at T} files the #119
     * loss right before tick T; {@code --chronos} records the run (D-023
     * stage 1); {@code --snapshot-at T} pauses after tick T, retains the
     * walk, and lets the emitted DIGEST judge its own preimage (stage 3;
     * the split changes nothing — run(a) + run(b) is run(a + b)). Snapshot
     * mode and sink mode are separate scenarios by design.
     */
    private static void runHeadless(long seed, long ticks, String follow, String chronosPath,
            long sinkAt, long reloadAt, Long snapshotAt) throws Exception {
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
                    if (t == sinkAt) {
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
            System.out.print(String.format(Locale.ROOT,
                    "PERF ticks_per_s=%d entities=%d ticks=%d\n", ticksPerSecond, sim.aliveEntities(), ticks));
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
                  --follow NAME       stream one pilot's dream as JSONL every 100 ticks
                  --sink-at T         scuttle the active ship in tick T's zion slot (headless scenario, #119)
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
                  --selftest          in-process digest double-run; exit 0 iff chains match
                  --bench             measure the D-027 budget table; exit 0 iff all rows pass
                interactive commands: red | agent | smith | deja | reload | sink | pause | speed N | quit
                """);
    }
}
