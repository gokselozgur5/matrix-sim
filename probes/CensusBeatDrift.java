import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Probe: the census keeps the film's timing, merge by merge.
 *
 * `ArcBeats` gates ORDER and CI throws the ticks away — but the ticks are the
 * part that carries drift. A beat that slides four hundred ticks while staying
 * in order passes the lane in silence, and that is exactly the shape of change
 * that hides: a tuning nudge, a draw-count shift, a population tweak that moves
 * when the war starts without breaking what the war is.
 *
 * This probe extracts the same eight beats with the same sequential scan
 * `ArcBeats` uses — so the recorded ticks are the ones the gate saw — and
 * prints one appendable row per seed, stamped with the commit it was measured
 * at. Given the previous row, it prints per-beat deltas and verdicts them
 * against a band stated on the command line rather than after the fact.
 *
 * Two seeds by default (42 and 7, the repo's standard universes) so a drift
 * that is really a one-universe accident cannot masquerade as a systemic one.
 *
 * Usage:
 *   java -cp out:probes/out CensusBeatDrift [seeds] [ticks] [--band N] [--baseline "ROW"]...
 *
 *   seeds            comma-separated (default 42,7)
 *   ticks            tick budget per universe (default 6000)
 *   --band N         max tolerated |delta| per beat, in ticks (default 200)
 *   --baseline       a previous BEATDRIFT row, verbatim; matched to its seed
 *   --baseline-file  a file of such rows — `#` and blank lines ignored, every
 *                    other line must open with `BEATDRIFT `. This is how the
 *                    bench feeds it probes/beatdrift.baseline, the committed pin
 *   --sha S          stamp the row with S instead of HEAD — the pinned-tree case,
 *                    where `git archive <sha>` left an exported tree with no .git
 *
 * Verdicts: DRIFT_WITHIN_BAND · DRIFT_FLAGGED (one FLAG line per offending beat)
 * · NO_BASELINE (rows recorded, nothing to compare — the first row of a table).
 *
 * Every verdict carries the set it judged — `compared=<pairs measured>/<seeds ×
 * beats>` — and the band it judged them against. Without the denominator the
 * strongest line this probe can print is also the line it prints when it read a
 * baseline that named none of the beats: a comparison of nothing to nothing is
 * not a clean bill of health, and it no longer reads like one (#900).
 */
public final class CensusBeatDrift {

    private record Beat(String name, String needle) {}

    /** D-036's beat list, in the order the film plays it — identical to ArcBeats. */
    private static final List<Beat> BEATS = List.of(
            new Beat("birth", "The One is born"),
            new Beat("refusal", "I DIDN'T"),
            new Beat("overflow", "SMITH OVERFLOW"),
            new Beat("flatline", "Thomas A. Anderson flatlined"),
            new Beat("peace", "The One: \"Peace.\""),
            new Beat("reboot", "REBOOT v"),
            new Beat("door", "open door tally"),
            new Beat("second_birth", "The One is born"));

    public static void main(String[] args) {
        matrix.Streams.utf8();
        List<String> positional = new ArrayList<>();
        List<String> baselines = new ArrayList<>();
        long band = 200;
        String shaOverride = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--band" -> band = Long.parseLong(args[++i]);
                case "--baseline" -> baselines.add(args[++i]);
                case "--baseline-file" -> baselines.addAll(rowsOf(args[++i]));
                case "--sha" -> shaOverride = args[++i];
                default -> positional.add(args[i]);
            }
        }
        String seedSpec = positional.size() > 0 ? positional.get(0) : "42,7";
        long ticks = positional.size() > 1 ? Long.parseLong(positional.get(1)) : 6_000;

        String sha = shaOverride != null ? shaOverride : shortSha();
        String[] seeds = seedSpec.split(",");
        long pairs = (long) seeds.length * BEATS.size();
        boolean flagged = false;
        long compared = 0;
        long worst = 0;

        for (String token : seeds) {
            long seed = Long.parseLong(token.trim());
            Map<String, Long> now = beatsOf(seed, ticks);
            Map<String, Long> before = baselineFor(baselines, seed);

            StringBuilder row = new StringBuilder("BEATDRIFT sha=").append(sha)
                    .append(" seed=").append(seed).append(" ticks=").append(ticks);
            for (Beat beat : BEATS) {
                row.append(' ').append(beat.name()).append('=').append(now.get(beat.name()));
            }

            List<String> flags = new ArrayList<>();
            long rowCompared = 0;
            long maxDelta = 0;
            if (before == null) {
                row.append(" max_delta=n/a");
            } else {
                // A row measured at another budget is not a previous reading of this
                // question; comparing them would report the budget as drift.
                Long baselineTicks = before.get("ticks");
                if (baselineTicks != null && baselineTicks != ticks) {
                    throw fatal("baseline row for seed " + seed + " was measured at ticks="
                            + baselineTicks + " and this run is at ticks=" + ticks
                            + " — two budgets are two questions");
                }
                for (Beat beat : BEATS) {
                    long a = before.getOrDefault(beat.name(), Long.MIN_VALUE);
                    long b = now.get(beat.name());
                    if (a == Long.MIN_VALUE) {
                        continue; // beat absent from the baseline row: nothing to compare
                    }
                    if (a < 0 ^ b < 0) {
                        flags.add("FLAG seed=" + seed + " beat=" + beat.name()
                                + " " + (b < 0 ? "beat_lost" : "beat_gained")
                                + " was=" + a + " now=" + b);
                        continue;
                    }
                    if (a < 0) {
                        continue; // absent on both sides: agreed absence, not drift
                    }
                    long delta = b - a;
                    rowCompared++;
                    maxDelta = Math.max(maxDelta, Math.abs(delta));
                    if (Math.abs(delta) > band) {
                        flags.add("FLAG seed=" + seed + " beat=" + beat.name()
                                + " delta=" + (delta > 0 ? "+" : "") + delta + " band=" + band);
                    }
                }
                // Same rule one line up: a max_delta of 0 over nothing measured is
                // the sentence this unit exists to stop printing.
                row.append(" max_delta=").append(rowCompared == 0 ? "n/a" : maxDelta);
                compared += rowCompared;
                worst = Math.max(worst, maxDelta);
            }
            System.out.println(row);
            flags.forEach(System.out::println);
            flagged |= !flags.isEmpty();
        }

        // The set the verdict judged, appended to every arm of it: pairs actually
        // measured over seeds × beats, and the band they were measured against.
        // A flag is a fact about a comparison that was made, so it outranks an
        // empty denominator — all eight beats lost is DRIFT_FLAGGED with nothing
        // compared, not NO_BASELINE.
        String judged = " compared=" + compared + "/" + pairs + " band=" + band;
        System.out.println("BAND ticks=" + band + " seeds=" + seedSpec);
        if (flagged) {
            System.out.println("VERDICT DRIFT_FLAGGED" + judged);
        } else if (compared == 0) {
            System.out.println("VERDICT NO_BASELINE" + judged);
        } else {
            System.out.println("MAX_DELTA " + worst);
            System.out.println("VERDICT DRIFT_WITHIN_BAND" + judged);
        }
    }

    /** Run one universe and pull the eight beats with ArcBeats' sequential scan. */
    private static Map<String, Long> beatsOf(long seed, long ticks) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
        new Simulation(seed, buf, null).run(ticks);
        String[] lines = buf.toString(StandardCharsets.UTF_8).split("\n");

        Map<String, Long> found = new LinkedHashMap<>();
        int cursor = 0;
        for (Beat beat : BEATS) {
            long tick = -1;
            while (cursor < lines.length) {
                String line = lines[cursor++];
                if (line.startsWith("[") && line.contains(beat.needle())) {
                    tick = Long.parseLong(line.substring(1, line.indexOf(']')));
                    break;
                }
            }
            found.put(beat.name(), tick);
        }
        return found;
    }

    /** Parse a previous BEATDRIFT row (k=v tokens) whose seed matches. */
    private static Map<String, Long> baselineFor(List<String> rows, long seed) {
        for (String row : rows) {
            Map<String, Long> parsed = new LinkedHashMap<>();
            for (String token : row.trim().split("\\s+")) {
                int eq = token.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                try {
                    parsed.put(token.substring(0, eq), Long.parseLong(token.substring(eq + 1)));
                } catch (NumberFormatException ignored) {
                    // sha=, max_delta=n/a and friends are not beats
                }
            }
            Long rowSeed = parsed.get("seed");
            if (rowSeed != null && rowSeed == seed) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * The committed pin, read as rows. Blank lines and lines opening with `#` are
     * comments; every other line must open with `BEATDRIFT `. A line that is
     * neither is refused rather than skipped — a baseline whose rows were quietly
     * dropped judges nothing while still printing a verdict, which is the exact
     * failure this probe was made judged to prevent.
     */
    private static List<String> rowsOf(String path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw fatal("cannot read baseline file " + path + ": " + e);
        }
        List<String> rows = new ArrayList<>();
        for (int n = 1; n <= lines.size(); n++) {
            String line = lines.get(n - 1).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.startsWith("BEATDRIFT ")) {
                throw fatal(path + ":" + n + " is neither a comment nor a BEATDRIFT row: " + line);
            }
            rows.add(line);
        }
        return rows;
    }

    /**
     * Refuse, loudly, with the probe's exit code for a bad invocation. The return
     * type exists so callers can write `throw fatal(…)` and javac can see the flow
     * end there; System.exit has already left the building by then.
     */
    private static RuntimeException fatal(String message) {
        System.err.println("FATAL " + message);
        System.exit(Probes.Outcome.REFUSED.code());
        return new IllegalStateException(message);
    }

    /** The commit the row was measured at; "unknown" outside a checkout. */
    private static String shortSha() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 && !out.isEmpty() ? out : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private CensusBeatDrift() {}
}
