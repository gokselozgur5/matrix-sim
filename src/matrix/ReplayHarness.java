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
 * Births (#548/#550) join by the same law, in the same breath: the reader
 * learns the kind in the PR that taught the recorder to write it, because
 * a record the strict reader refuses turns every recording that carries it
 * into an unfoldable artifact. They are not re-applied — a birth is not an
 * operator input — but they ARE folded: the replayed universe grows its
 * own people and the fold proves they are the same people, at the same
 * ticks, under the same names-at-birth. {@code REPLAY OK} says how many
 * were re-executed rather than merely re-read.
 *
 * Stage 5 lands as its honest slice (#129): {@code --audit} walks a
 * recording and verdicts internal consistency without booting anything
 * — the log answers for itself. The full inversion's letter — booting
 * objects FROM a snapshot — stays refused on principle: a snapshot is
 * an equality certificate, not a save-game (crown #179); in the
 * coarse+seeded model reconstruction IS re-execution from genesis under
 * recorded inputs. The log's truth-authority is delivered as the sum of
 * three facts: it is written before the purge it describes, its seals
 * are re-taken and re-verified at every fold, and it answers for itself
 * under audit — while objects remain the cache the engine regenerates.
 *
 * Exit grammar: 0 the chains agree (or a chain was printed), 1 the chains
 * diverge (a broken seal included), 2 the fold refused (unreadable
 * record, foreign physics, unknown command).
 */
public final class ReplayHarness {

    /** A recorded operator command, to be re-applied when the replay reaches its tick. */
    private record Command(long tick, String cmd, int line) {}

    /** An epoch-boundary marker (#128): the closing epoch's certificate, to be re-taken and compared. */
    private record Marker(long tick, int epoch, String sha, long bytes, int line) {}

    /** An epoch boundary as recorded — reload or treaty; the audit pairs reloads with their seals (#129). */
    private record Boundary(long tick, String kind, int line) {}

    /** A recorded birth: who came to exist, at which tick, under which name-at-birth (#548). */
    private record Birth(long tick, String name, String family, int line) {}

    /**
     * What a recording declares before its first tick — and everything the
     * audit answers for (#129). {@code lastTick} is the record's own horizon:
     * the last tick it says anything about, and therefore the last tick its
     * testimony covers.
     */
    private record Recording(long seed, int version, String configFingerprint, List<Command> commands,
            List<Marker> markers, List<Boundary> boundaries, List<Birth> births, int records, int flushes,
            long lastTick) {}

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
     * Stage 5, the honest slice (#129): the log answers for itself. The
     * audit walks one recording and verdicts INTERNAL consistency — no
     * universe is booted, nothing is folded. The laws: genesis present
     * and single, tick stamps monotone, every kind known (the strict
     * reader enforces these), every seal paired with its reload boundary
     * and its console command in write-before-purge order, epoch
     * arithmetic exact (genesis version plus boundaries crossed), and
     * the config fingerprint against this build — where drift is NAMED,
     * not failed: whether the record is coherent is the audit's
     * jurisdiction; whether this build may fold it is the fold's, which
     * would refuse. An unsealed console reload IS a fail — the stage-4
     * recorder seals before the purge, so a record that says otherwise
     * contradicts its own writer. Mid-tick boundaries (emergency reload,
     * treaty) legally stand alone: no console command, no seal.
     *
     * Exit grammar: 0 internally consistent, 1 inconsistent (the first
     * offense named), 2 unreadable.
     */
    public static int audit(String chronosPath) {
        Recording rec;
        try {
            rec = parse(Path.of(chronosPath));
        } catch (IOException e) {
            System.out.print("AUDIT REFUSED unreadable: " + e.getMessage() + "\n");
            return 2;
        } catch (IllegalStateException e) {
            System.out.print("AUDIT FAIL " + e.getMessage() + "\n");
            return 1;
        }
        boolean drift = !rec.configFingerprint().equals(ChronosLog.configFingerprint());
        StringBuilder out = new StringBuilder();
        out.append("AUDIT genesis seed=").append(rec.seed()).append(" version=").append(rec.version())
                .append(" config=").append(drift ? "drift" : "match").append('\n');
        out.append("AUDIT records=").append(rec.records()).append(" commands=").append(rec.commands().size())
                .append(" flushes=").append(rec.flushes()).append(" boundaries=").append(rec.boundaries().size())
                .append(" seals=").append(rec.markers().size())
                .append(" births=").append(rec.births().size()).append('\n');
        String offense = firstOffense(rec);
        if (offense != null) {
            System.out.print(out);
            System.out.print("AUDIT FAIL " + offense + "\n");
            return 1;
        }
        for (Marker m : rec.markers()) {
            out.append("AUDIT seal tick=").append(m.tick()).append(" epoch=").append(m.epoch())
                    .append(" sha=").append(m.sha()).append(" paired\n");
        }
        if (drift) {
            out.append("AUDIT NOTE config_drift recorded=").append(rec.configFingerprint())
                    .append(" build=").append(ChronosLog.configFingerprint())
                    .append(" — internally consistent, but the fold on this build will refuse it\n");
        }
        out.append("AUDIT OK records=").append(rec.records())
                .append(" seals_paired=").append(rec.markers().size()).append('\n');
        System.out.print(out);
        return 0;
    }

    /** The pairing and arithmetic laws, walked in record order; the first broken one is the verdict. */
    private static String firstOffense(Recording rec) {
        // every seal: its reload boundary follows at the same tick, no second
        // seal stacked before it, and its console command precedes it — the
        // write-before-purge invariant, visible in the record's own ordering
        for (int i = 0; i < rec.markers().size(); i++) {
            Marker m = rec.markers().get(i);
            Boundary b = null;
            for (Boundary cand : rec.boundaries()) {
                if (cand.line() > m.line()) {
                    b = cand;
                    break;
                }
            }
            if (b == null || b.tick() != m.tick() || !b.kind().equals("reload")) {
                return "seal_without_boundary tick=" + m.tick() + " line=" + m.line()
                        + " — a sealed epoch that never closed";
            }
            if (i + 1 < rec.markers().size() && rec.markers().get(i + 1).line() < b.line()) {
                return "stacked_seals tick=" + rec.markers().get(i + 1).tick()
                        + " line=" + rec.markers().get(i + 1).line() + " — two seals, one boundary";
            }
            boolean commanded = false;
            for (Command c : rec.commands()) {
                if (c.tick() == m.tick() && c.line() < m.line()
                        && c.cmd().split("\\s+")[0].equals("reload")) {
                    commanded = true;
                    break;
                }
            }
            if (!commanded) {
                return "seal_without_command tick=" + m.tick() + " line=" + m.line()
                        + " — only the console path can seal; mid-tick reloads cannot";
            }
            int crossed = 0;
            for (Boundary cand : rec.boundaries()) {
                if (cand.line() < m.line()) {
                    crossed++;
                }
            }
            if (m.epoch() != rec.version() + crossed) {
                return "epoch_drift tick=" + m.tick() + " recorded=" + m.epoch()
                        + " expected=" + (rec.version() + crossed) + " — the version arithmetic disagrees";
            }
        }
        // every console reload boundary: a seal stands between the command and
        // the boundary, or the record contradicts its own stage-4 writer
        for (Boundary b : rec.boundaries()) {
            if (!b.kind().equals("reload")) {
                continue;
            }
            Command cmd = null;
            for (Command c : rec.commands()) {
                if (c.tick() == b.tick() && c.line() < b.line()
                        && c.cmd().split("\\s+")[0].equals("reload")) {
                    cmd = c;
                }
            }
            if (cmd == null) {
                continue; // emergency reload: mid-tick, unsealed by design
            }
            boolean sealed = false;
            for (Marker m : rec.markers()) {
                if (m.tick() == b.tick() && m.line() > cmd.line() && m.line() < b.line()) {
                    sealed = true;
                    break;
                }
            }
            if (!sealed) {
                return "unsealed_reload tick=" + b.tick() + " line=" + b.line()
                        + " — the stage-4 recorder seals before the purge; this record did not";
            }
        }
        return null;
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
        // Two horizons, and they are not the same one. The FOLD's horizon is
        // `ticks`: a birth recorded past it was never re-executed here.
        // The RECORD's horizon is its last tick stamp: a fold run longer than
        // the recording (a short recording against a long --expect chain) will
        // grow people the record never claimed to have witnessed, and silence
        // is not testimony — those are outside the record's evidence, not
        // divergences from it.
        List<Birth> foldable = new ArrayList<>();
        int birthsAfterHorizon = 0;
        for (Birth b : rec.births()) {
            if (b.tick() <= ticks) {
                foldable.add(b);
            } else {
                birthsAfterHorizon++;
            }
        }
        List<matrix.core.ChronosLog.Birth> witnessed = new ArrayList<>();
        for (matrix.core.ChronosLog.Birth b : sim.births()) {
            if (b.tick() <= rec.lastTick()) {
                witnessed.add(b);
            }
        }
        if (!foldBirths(foldable, witnessed)) {
            return 1;
        }
        System.out.print("REPLAY OK seed=" + rec.seed() + " ticks=" + ticks
                + " links=" + chain.size() + " commands_applied=" + applied
                + " births_folded=" + foldable.size()
                + (sealsVerified > 0 ? " seals_verified=" + sealsVerified : "")
                + (afterHorizon > 0 ? " commands_after_horizon=" + afterHorizon : "")
                + (birthsAfterHorizon > 0 ? " births_after_horizon=" + birthsAfterHorizon : "") + "\n");
        return 0;
    }

    /**
     * The dispatch law for a birth record — and it is not "re-apply". A
     * birth is not an operator input: the recorded seed already grows the
     * same people in the same order, so the fold's duty is to prove it
     * RE-EXECUTED the origin story rather than merely re-read it. This walks
     * the births the replayed universe decided against the births the record
     * claims — count, then tick, name-at-birth and family in order.
     *
     * <p>It is a second referee behind the chain, not a softer one: a birth
     * that shifts by one tick under an agreeing chain means fate was spent
     * somewhere the digest frame does not reach, which is precisely the
     * failure the birth-seed law exists to catch. False on the first
     * disagreement, which is named and printed.
     */
    private static boolean foldBirths(List<Birth> recorded, List<matrix.core.ChronosLog.Birth> observed) {
        if (recorded.size() != observed.size()) {
            System.out.print("REPLAY FAIL birth_count recorded=" + recorded.size()
                    + " re_executed=" + observed.size()
                    + " — the record and the re-run disagree about how many came to exist\n");
            return false;
        }
        for (int i = 0; i < recorded.size(); i++) {
            Birth r = recorded.get(i);
            matrix.core.ChronosLog.Birth o = observed.get(i);
            if (r.tick() != o.tick() || !r.name().equals(o.name()) || !r.family().equals(o.family())) {
                System.out.print("REPLAY FAIL birth_divergence line=" + r.line()
                        + " recorded=tick=" + r.tick() + ",name=" + r.name() + ",family=" + r.family()
                        + " re_executed=tick=" + o.tick() + ",name=" + o.name() + ",family=" + o.family()
                        + "\n");
                return false;
            }
        }
        return true;
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
     * commands, epoch seals (snapshot markers, #128), boundaries, births
     * (#548) and flush fingerprints with monotone tick stamps. A birth
     * missing its name-at-birth or its family is refused like any other
     * off-grammar line: the field the die keys on cannot be optional.
     * This is a reader for
     * our own recorder's grammar (crown #177), not a general JSON parser
     * — anything off-grammar is refused, because a fold over a misread
     * record would be a quiet lie.
     */
    private static Recording parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Long seed = null;
        int version = 0;
        String config = null;
        List<Command> commands = new ArrayList<>();
        List<Marker> markers = new ArrayList<>();
        List<Boundary> boundaries = new ArrayList<>();
        List<Birth> births = new ArrayList<>();
        int records = 0;
        int flushes = 0;
        long lastTick = 0;
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty()) {
                continue;
            }
            records++;
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
                    version = (int) longField(line, "version", n + 1);
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
                    commands.add(new Command(tick, cmd, n + 1));
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
                            sha, longField(line, "bytes", n + 1), n + 1));
                }
                case "boundary" -> {
                    long tick = tickField(line, n + 1, lastTick);
                    lastTick = tick;
                    String bKind = stringField(line, "kind");
                    if (bKind == null) {
                        refuse("boundary without a kind at line " + (n + 1));
                    }
                    boundaries.add(new Boundary(tick, bKind, n + 1));
                }
                case "birth" -> {
                    long tick = tickField(line, n + 1, lastTick);
                    lastTick = tick;
                    String name = stringField(line, "name");
                    if (name == null || name.isEmpty()) {
                        refuse("birth without a name-at-birth at line " + (n + 1)
                                + " — the die keys to this field; a birth that names nobody keys nothing");
                    }
                    String family = stringField(line, "family");
                    if (family == null || family.isEmpty()) {
                        refuse("birth without a family at line " + (n + 1)
                                + " — who came to exist is half the record");
                    }
                    births.add(new Birth(tick, name, family, n + 1));
                }
                case "flush" -> {
                    lastTick = tickField(line, n + 1, lastTick);
                    flushes++;
                }
                default -> refuse("unknown record kind '" + kind + "' at line " + (n + 1));
            }
        }
        if (seed == null) {
            refuse("no genesis — there is nothing to replay");
        }
        return new Recording(seed, version, config, commands, markers, boundaries, births, records, flushes,
                lastTick);
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
