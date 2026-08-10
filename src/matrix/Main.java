package matrix;

import matrix.core.Digest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
        String follow = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Long.parseLong(args[++i]);
                case "--headless" -> headless = true;
                case "--selftest" -> selftest = true;
                case "--follow" -> follow = args[++i];
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

        if (selftest) {
            System.exit(selftest(seed));
        }
        if (headless) {
            runHeadless(seed, ticks, follow);
            return;
        }
        runInteractive(seed, follow);
    }

    /** The kernel of CI without a build tool (accepted D-009 spark): two runs, one verdict. */
    private static int selftest(long seed) {
        List<Digest> a = new Simulation(seed, null, null).run(2_000);
        List<Digest> b = new Simulation(seed, null, null).run(2_000);
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
        System.out.println("SELFTEST OK seed=" + seed + " ticks=2000 chain_length=" + a.size());
        return 0;
    }

    private static void runHeadless(long seed, long ticks, String follow) {
        Simulation sim = new Simulation(seed, System.out, follow);
        long start = System.nanoTime();
        sim.run(ticks);
        long elapsedNs = System.nanoTime() - start;
        long perTickNs = Math.max(1, elapsedNs / Math.max(1, ticks));
        long ticksPerSecond = 1_000_000_000L / perTickNs;
        System.out.print(String.format(Locale.ROOT,
                "PERF ticks_per_s=%d entities=%d ticks=%d\n", ticksPerSecond, sim.aliveEntities(), ticks));
    }

    private static void runInteractive(long seed, String follow) throws Exception {
        Simulation sim = new Simulation(seed, System.out, follow);
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
                    case "red" -> sim.commandRed();
                    case "agent" -> sim.commandAgent();
                    case "smith" -> sim.commandSmith();
                    case "deja" -> sim.commandDeja();
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
                    case "help" -> System.out.print("commands: red | agent | smith | deja | pause | speed N | quit\n");
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
                matrix-sim daemon (v1.0)
                  --headless          run without the ops console, then print PERF
                  --ticks N           tick budget for headless runs (default 2000)
                  --seed N            the fate of the universe (default 42)
                  --follow NAME       stream one pilot's dream as JSONL every 100 ticks
                  --selftest          in-process digest double-run; exit 0 iff chains match
                interactive commands: red | agent | smith | deja | pause | speed N | quit
                """);
    }
}
