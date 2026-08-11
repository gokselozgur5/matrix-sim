package matrix;

import matrix.core.ChronosLog;
import matrix.core.Digest;
import matrix.core.Snapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The fold (D-023 stage 2, crown #178): given a chronos recording, re-run
 * the universe and prove it. Genesis names the seed, recorded operator
 * commands are re-applied at their exact ticks, and the replayed DIGEST
 * chain is the only referee — compared byte for byte against a reference
 * chain; on divergence the verdict names the first differing tick.
 *
 * It boots universes, so it lives beside the bootstrap at the root and is
 * driven by {@code Main --replay} — never a probe (probes are read-only
 * diagnostics; the harness re-executes). It constructs {@link Simulation}s
 * and nothing lower: no new composition root. It draws nothing (the
 * replayed universe draws), touches no wall clock (that allowance stays
 * with Main's PERF harness), and issues no "close enough" verdicts.
 *
 * Since stage 4 (#128) the fold also holds the reload to its seal: a
 * recorded console reload arrives with an epoch marker — the closing
 * epoch's boundary {@link Snapshot}, sha and sizes, never a payload —
 * and the fold re-takes the same walk at the same between-ticks point
 * before re-dispatching, then compares. Recorder and reader learned the
 * marker grammar in the same breath, as the stage-3 note demanded.
 * Flush fingerprint lines are read as evidence of shape but not folded:
 * in the coarse+seeded model, re-execution regenerates the events and
 * the chain judges the outcome.
 *
 * Exit grammar: 0 the chains agree (or a chain was printed), 1 the chains
 * diverge (a broken seal included), 2 the fold refused (unreadable
 * record, foreign physics, unknown command).
 */
public final class ReplayHarness {

    /** A recorded operator command, to be re-applied when the replay reaches its tick. */
    private record Command(long tick, String cmd) {}

    /** An epoch-boundary marker (#128): the closing epoch's certificate, to be re-taken and compared. */
    private record Marker(long tick, int epoch, String sha, long bytes) {}

    /** What a recording declares before its first tick. */
    private record Recording(long seed, String configFingerprint,
            List<Command> commands, List<Marker> markers) {}

    /**
     * The whole stage-2 surface: fold {@code chronosPath}; with
     * {@code expectPath} verify against a ChainDump-format digest file
     * (run length = the dump's last tick), without it re-run
     * {@code ticksFlag} ticks and print the chain in ChainDump format —
     * so a printed fold is itself a valid {@code --expect} reference.
     * Returns the exit code; Main owns the actual exit.
     */
    public static int run(String chronosPath, String expectPath, long ticksFlag) {
        try {
            Recording rec = parse(Path.of(chronosPath));
            String physics = ChronosLog.configFingerprint();
            if (!rec.configFingerprint().equals(physics)) {
                refuse("foreign physics: recorded config " + rec.configFingerprint()
                        + " but this build is " + physics + " — the fold refuses a different universe");
            }
            if (expectPath == null) {
                return fold(rec, ticksFlag, null);
            }
            List<String> expected = readExpect(Path.of(expectPath));
            long ticks = digestTick(expected.get(expected.size() - 1), expected.size());
            return fold(rec, ticks, expected);
        } catch (IOException e) {
            System.out.print("REPLAY REFUSED unreadable: " + e.getMessage() + "\n");
            return 2;
        } catch (IllegalStateException e) {
            System.out.print("REPLAY REFUSED " + e.getMessage() + "\n");
            return 2;
        }
    }

    /**
     * Re-execution. A fresh universe from the recorded seed — silent, no
     * follow, recorder OFF (the fold reads the record; it never writes
     * one) — ticked in segments so each recorded command lands exactly
     * where the console once stood: between ticks, after {@code tick}
     * completed ticks. Commands recorded past the horizon stay unapplied;
     * they could not touch a compared link.
     *
     * Seals (#128): when the next recorded reload carries a marker at its
     * tick, the fold re-takes {@link Simulation#snapshotNow()} at the
     * dispatch point — the exact walk the recorder sealed pre-purge — and
     * compares sha, epoch and size before re-applying the command. A
     * marker no reload claims is not folded (the audit's jurisdiction,
     * not the chain's); re-taking a walk draws nothing, so seals leave
     * the replayed chain untouched.
     */
    private static int fold(Recording rec, long ticks, List<String> expected) {
        Simulation sim = new Simulation(rec.seed(), null, null);
        int next = 0;
        int applied = 0;
        int nextMarker = 0;
        int sealsVerified = 0;
        long done = 0;
        while (done < ticks) {
            while (next < rec.commands().size() && rec.commands().get(next).tick() == done) {
                Command c = rec.commands().get(next);
                if (c.cmd().split("\\s+")[0].equals("reload")
                        && nextMarker < rec.markers().size()
                        && rec.markers().get(nextMarker).tick() == done) {
                    Marker m = rec.markers().get(nextMarker);
                    Snapshot live = sim.snapshotNow();
                    if (!live.sha256Hex().equals(m.sha()) || live.version() != m.epoch()
                            || live.bytes().length != m.bytes()) {
                        System.out.print("REPLAY FAIL boundary_seal tick=" + done
                                + " recorded=" + m.sha() + " epoch=" + m.epoch()
                                + " got=" + live.sha256Hex() + " epoch=" + live.version() + "\n");
                        return 1;
                    }
                    nextMarker++;
                    sealsVerified++;
                }
                dispatch(sim, c);
                next++;
                applied++;
            }
            long until = next < rec.commands().size() && rec.commands().get(next).tick() < ticks
                    ? rec.commands().get(next).tick()
                    : ticks;
            sim.run(until - done);
            done = until;
        }
        List<Digest> chain = sim.run(0);
        int afterHorizon = rec.commands().size() - applied;

        if (expected == null) {
            StringBuilder out = new StringBuilder();
            for (Digest d : chain) {
                out.append(d.format()).append('\n');
            }
            out.append("CHAIN seed=").append(rec.seed()).append(" ticks=").append(ticks)
                    .append(" links=").append(chain.size()).append('\n');
            System.out.print(out);
            return 0;
        }
        if (chain.size() != expected.size()) {
            System.out.print("REPLAY FAIL chain_length expected=" + expected.size()
                    + " got=" + chain.size() + "\n");
            return 1;
        }
        for (int i = 0; i < chain.size(); i++) {
            if (!chain.get(i).format().equals(expected.get(i))) {
                System.out.print("REPLAY FAIL first_divergence_tick=" + chain.get(i).tick()
                        + " expected=" + expected.get(i).substring(expected.get(i).indexOf(" sha=") + 5)
                        + " got=" + chain.get(i).sha256() + "\n");
                return 1;
            }
        }
        System.out.print("REPLAY OK seed=" + rec.seed() + " ticks=" + ticks
                + " links=" + chain.size() + " commands_applied=" + applied
                + (sealsVerified > 0 ? " seals_verified=" + sealsVerified : "")
                + (afterHorizon > 0 ? " commands_after_horizon=" + afterHorizon : "") + "\n");
        return 0;
    }

    /** Exactly the console's dispatch table — and only it. A recording holding anything else is refused loudly. */
    private static void dispatch(Simulation sim, Command c) {
        switch (c.cmd().split("\\s+")[0]) {
            case "red" -> sim.commandRed();
            case "agent" -> sim.commandAgent();
            case "smith" -> sim.commandSmith();
            case "deja" -> sim.commandDeja();
            case "reload" -> sim.commandReload();
            case "sink" -> sim.commandSink();
            default -> refuse("unknown recorded command '" + c.cmd() + "' at tick " + c.tick()
                    + " — the fold re-applies only what the console dispatches");
        }
    }

    /**
     * Reads a chronos JSONL recording: genesis first (exactly one), then
     * commands, boundaries and flush fingerprints with monotone tick
     * stamps. This is a reader for our own recorder's grammar (crown
     * #177), not a general JSON parser — anything off-grammar is refused,
     * because a fold over a misread record would be a quiet lie.
     */
    private static Recording parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Long seed = null;
        String config = null;
        List<Command> commands = new ArrayList<>();
        List<Marker> markers = new ArrayList<>();
        long lastTick = 0;
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty()) {
                continue;
            }
            String kind = stringField(line, "chronos");
            if (kind == null) {
                refuse("line " + (n + 1) + " is not a chronos record");
            }
            if (seed == null && !kind.equals("genesis")) {
                refuse("no genesis before line " + (n + 1) + " — a recording opens by naming its universe");
            }
            switch (kind) {
                case "genesis" -> {
                    if (seed != null) {
                        refuse("second genesis at line " + (n + 1) + " — one universe per recording");
                    }
                    seed = longField(line, "seed", n + 1);
                    config = stringField(line, "config");
                    if (config == null) {
                        refuse("genesis without a config fingerprint at line " + (n + 1));
                    }
                }
                case "command" -> {
                    long tick = tickField(line, n + 1, lastTick);
                    lastTick = tick;
                    String cmd = stringField(line, "cmd");
                    if (cmd == null) {
                        refuse("command without a cmd at line " + (n + 1));
                    }
                    commands.add(new Command(tick, cmd));
                }
                case "snapshot" -> {
                    long tick = tickField(line, n + 1, lastTick);
                    lastTick = tick;
                    String sha = stringField(line, "sha");
                    if (sha == null || !sha.matches("[0-9a-f]{64}")) {
                        refuse("snapshot marker without a sha256 at line " + (n + 1)
                                + " — a seal that names no certificate seals nothing");
                    }
                    markers.add(new Marker(tick, (int) longField(line, "epoch", n + 1),
                            sha, longField(line, "bytes", n + 1)));
                }
                case "boundary", "flush" -> lastTick = tickField(line, n + 1, lastTick);
                default -> refuse("unknown record kind '" + kind + "' at line " + (n + 1));
            }
        }
        if (seed == null) {
            refuse("no genesis — there is nothing to replay");
        }
        return new Recording(seed, config, commands, markers);
    }

    /** The reference chain: DIGEST lines of a ChainDump-format file; its CHAIN trailer is tolerated, anything else refused. */
    private static List<String> readExpect(Path file) throws IOException {
        List<String> chain = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty() || line.startsWith("CHAIN ")) {
                continue;
            }
            if (!line.startsWith("DIGEST tick=")) {
                refuse("expect line " + (n + 1) + " is not a DIGEST line — feed a ChainDump-format file");
            }
            chain.add(line);
        }
        if (chain.isEmpty()) {
            refuse("expect file holds no DIGEST lines — an empty chain proves nothing");
        }
        return chain;
    }

    /** Tick of a {@code DIGEST tick=N sha=...} line — the last one also sets the run length. */
    private static long digestTick(String digestLine, int lineNo) {
        int start = "DIGEST tick=".length();
        int end = digestLine.indexOf(' ', start);
        try {
            return Long.parseLong(digestLine.substring(start, end < 0 ? digestLine.length() : end));
        } catch (RuntimeException e) {
            refuse("expect line " + lineNo + " names no tick");
            return -1; // unreachable
        }
    }

    private static long tickField(String line, int lineNo, long lastTick) {
        long tick = longField(line, "tick", lineNo);
        if (tick < lastTick) {
            refuse("tick stamps run backward at line " + lineNo + " (" + tick + " after " + lastTick
                    + ") — not a chronological record");
        }
        return tick;
    }

    private static long longField(String line, String key, int lineNo) {
        String needle = "\"" + key + "\":";
        int i = line.indexOf(needle);
        if (i < 0) {
            refuse("line " + lineNo + " lacks \"" + key + "\"");
        }
        int start = i + needle.length();
        int end = start;
        if (end < line.length() && line.charAt(end) == '-') {
            end++;
        }
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        if (end == start) {
            refuse("line " + lineNo + ": \"" + key + "\" is not a number");
        }
        return Long.parseLong(line.substring(start, end));
    }

    /** First string field named {@code key}, unescaped per the writer's grammar; null when absent. */
    private static String stringField(String line, String key) {
        String needle = "\"" + key + "\":\"";
        int i = line.indexOf(needle);
        if (i < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + needle.length(); j < line.length(); j++) {
            char c = line.charAt(j);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && j + 1 < line.length()) {
                char e = line.charAt(++j);
                if (e == 'u' && j + 4 < line.length()) {
                    sb.append((char) Integer.parseInt(line.substring(j + 1, j + 5), 16));
                    j += 4;
                } else {
                    sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return null; // unterminated string — treated as absent, callers refuse
    }

    private static void refuse(String why) {
        throw new IllegalStateException(why);
    }

    private ReplayHarness() {}
}
