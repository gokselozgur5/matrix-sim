import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
 *   seeds       comma-separated (default 42,7)
 *   ticks       tick budget per universe (default 6000)
 *   --band N    max tolerated |delta| per beat, in ticks (default 200)
 *   --baseline  a previous BEATDRIFT row, verbatim; matched to its seed
 *   --sha S     stamp the row with S instead of HEAD — the pinned-tree case,
 *               where `git archive <sha>` left an exported tree with no .git
 *
 * Verdicts: DRIFT_WITHIN_BAND · DRIFT_FLAGGED (one FLAG line per offending beat)
 * · NO_BASELINE (rows recorded, nothing to compare — the first row of a table).
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
                case "--sha" -> shaOverride = args[++i];
                default -> positional.add(args[i]);
            }
        }
        String seedSpec = positional.size() > 0 ? positional.get(0) : "42,7";
        long ticks = positional.size() > 1 ? Long.parseLong(positional.get(1)) : 6_000;

        String sha = shaOverride != null ? shaOverride : shortSha();
        boolean flagged = false;
        boolean compared = false;
        long worst = 0;

        for (String token : seedSpec.split(",")) {
            long seed = Long.parseLong(token.trim());
            Map<String, Long> now = beatsOf(seed, ticks);
            Map<String, Long> before = baselineFor(baselines, seed);

            StringBuilder row = new StringBuilder("BEATDRIFT sha=").append(sha)
                    .append(" seed=").append(seed).append(" ticks=").append(ticks);
            for (Beat beat : BEATS) {
                row.append(' ').append(beat.name()).append('=').append(now.get(beat.name()));
            }

            List<String> flags = new ArrayList<>();
            long maxDelta = 0;
            if (before == null) {
                row.append(" max_delta=n/a");
            } else {
                compared = true;
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
                    maxDelta = Math.max(maxDelta, Math.abs(delta));
                    if (Math.abs(delta) > band) {
                        flags.add("FLAG seed=" + seed + " beat=" + beat.name()
                                + " delta=" + (delta > 0 ? "+" : "") + delta + " band=" + band);
                    }
                }
                row.append(" max_delta=").append(maxDelta);
                worst = Math.max(worst, maxDelta);
            }
            System.out.println(row);
            flags.forEach(System.out::println);
            flagged |= !flags.isEmpty();
        }

        System.out.println("BAND ticks=" + band + " seeds=" + seedSpec);
        if (!compared) {
            System.out.println("VERDICT NO_BASELINE");
        } else if (flagged) {
            System.out.println("VERDICT DRIFT_FLAGGED");
        } else {
            System.out.println("MAX_DELTA " + worst);
            System.out.println("VERDICT DRIFT_WITHIN_BAND");
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
