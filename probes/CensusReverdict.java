import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <p>ONE-OFF: run by hand, not in the bench. It answers a question once rather than
 * guarding a property on every push, so a row would cost the lane wall clock and buy
 * nothing (#1162). The rule is that the absence is DECLARED, not that it is unusual.
 *
 * Probe: what a census owes a declared digest move.
 *
 * The digest ritual catches "did the world change?". It was never designed to
 * catch "did the DISTRIBUTION change?" — and a declared move that breaks
 * nothing re-rolls every universe's war, because the cascade rides draws. This
 * probe is the missing half: two `SeedAtlas` tables measured at two pinned
 * trees, diffed row by row, classified against thresholds fixed BEFORE the
 * numbers are seen, so the classification is a computation and not an argument.
 *
 * The thresholds, stated in advance:
 *
 *   1. THE ZERO RULE — a verdict class that was exactly 0 and is now nonzero
 *      (or the reverse) is never STABLE. A branch appearing in nature is a
 *      structural fact, not a fluctuation, and no count-based noise band is
 *      valid at that cell anyway.
 *   2. THE MIX RULE — every other class is compared against the two-sample 95%
 *      noise band 1.96 * sqrt(2 * n * p * (1 - p)) counts, p pooled over both
 *      sides. Beyond it: SHIFTED.
 *   3. THE BAND RULE — the birth distribution's MEAN is compared across sides
 *      with a two-sample 95% band, and its standard deviation against a stated
 *      ratio (default 1.25x either way). Either beyond: RESHAPED. The mean and
 *      sd are used, not min/max: a range grows with the sample by construction
 *      and cannot be tested.
 *   4. THE ROSTER RULE — an entry that NAMES individual seeds is making a
 *      paired claim, not a distributional one, so no noise band governs it: a
 *      single seed changing fate falsifies the roster however STABLE the mix.
 *      Reported separately from the verdict, because it is a different kind of
 *      obligation on a different part of the entry.
 *
 * Precedence: RESHAPED > SHIFTED > STABLE. The roster rule rides alongside.
 *
 * STABLE is a statement about the DISTRIBUTION, never about the universes. The
 * paired churn line reports how many individual seeds changed fate: a declared
 * move can leave the mix untouched and still hand every seed a different life,
 * and the census says so out loud rather than implying stability it never
 * measured.
 *
 * Both tables must cover the same seeds at the same tick budget — a comparison
 * sample that is not the same sample is not a comparison, and the probe refuses
 * rather than guesses.
 *
 * Usage:
 *   java -cp out:probes/out CensusReverdict <old-table> <new-table> [--sd-ratio R]
 *
 * where each table is `SeedAtlas` stdout captured at a tree pinned by
 * `git archive <sha>` (D-030's pin-to-SHA rule — never a working tree that can
 * move mid-verification). The instrument is held constant across both sides and
 * only the world is pinned; otherwise the diff measures the probe, not the move.
 *
 * Identity falsifier: invoked with the same table on both sides it prints every
 * delta as zero and VERDICT STABLE.
 */
public final class CensusReverdict {

    private static final List<String> CLASSES =
            List.of("full_arc", "treaty", "war", "quiet", "old_playbook");

    /** One parsed SeedAtlas table. */
    private record Table(String path, long from, long to, long ticks,
                         Map<String, Integer> counts, Map<Long, String> verdicts,
                         Map<Long, Long> births, long bandMin, long bandMax) {
        int n() {
            return verdicts.size();
        }
    }

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        List<String> positional = new ArrayList<>();
        double sdRatio = 1.25;
        for (int i = 0; i < args.length; i++) {
            if ("--sd-ratio".equals(args[i])) {
                sdRatio = Double.parseDouble(args[++i]);
            } else if (args[i].startsWith("--")) {
                // An unknown long option was becoming a POSITIONAL (#1479), which is
                // worse than being ignored: `--sd-ration 2` fed `--sd-ration` and `2`
                // to the table arguments, so the probe read a flag as a filename and
                // refused for the wrong reason. Two positionals are still positional —
                // the tables are named without flags on purpose — so only `--` is
                // caught here.
                System.exit(Probes.Outcome.REFUSED.code());
            } else {
                positional.add(args[i]);
            }
        }
        if (positional.size() < 2) {
            System.out.println("usage: CensusReverdict <old-table> <new-table> [--sd-ratio R]");
            System.out.println("VERDICT REFUSED reason=missing_tables");
            return;
        }
        Table before = parse(positional.get(0));
        Table after = parse(positional.get(1));

        System.out.println("SIDE old=" + before.path() + " seeds=" + before.from() + ".." + before.to()
                + " ticks=" + before.ticks() + " rows=" + before.n());
        System.out.println("SIDE new=" + after.path() + " seeds=" + after.from() + ".." + after.to()
                + " ticks=" + after.ticks() + " rows=" + after.n());

        if (before.from() != after.from() || before.to() != after.to()
                || before.ticks() != after.ticks() || before.n() != after.n()) {
            System.out.println("VERDICT REFUSED reason=sample_mismatch");
            return;
        }
        int n = before.n();

        // --- rule 1 + 2: the verdict mix -------------------------------------
        boolean shifted = false;
        StringBuilder head = new StringBuilder("REVERDICT sample=").append(n);
        for (String cls : CLASSES) {
            int a = before.counts().getOrDefault(cls, 0);
            int b = after.counts().getOrDefault(cls, 0);
            head.append(' ').append(cls).append('=').append(a).append("->").append(b);

            String rule;
            boolean beyond;
            if ((a == 0) != (b == 0)) {
                rule = "zero";
                beyond = true;
            } else {
                double p = (a + b) / (2.0 * n);
                double noise = 1.96 * Math.sqrt(2.0 * n * p * (1 - p));
                rule = "mix noise=" + fmt(noise);
                beyond = Math.abs(b - a) > noise;
            }
            System.out.println("CLASS " + cls + " old=" + a + " new=" + b
                    + " delta=" + signed(b - a) + " rule=" + rule
                    + " " + (beyond ? "BEYOND_NOISE" : "within_noise"));
            shifted |= beyond;
        }

        // --- rule 3: the birth band ------------------------------------------
        double[] sa = stats(before.births().values());
        double[] sb = stats(after.births().values());
        boolean reshaped = false;
        String bandRule;
        if (sa[2] < 2 || sb[2] < 2) {
            bandRule = "insufficient_births";
        } else {
            double se = Math.sqrt(sa[1] * sa[1] / sa[2] + sb[1] * sb[1] / sb[2]);
            double meanBand = 1.96 * se;
            boolean meanMoved = Math.abs(sb[0] - sa[0]) > meanBand;
            double ratio = sa[1] == 0 ? (sb[1] == 0 ? 1 : Double.POSITIVE_INFINITY) : sb[1] / sa[1];
            boolean spreadMoved = ratio > sdRatio || ratio < 1 / sdRatio;
            reshaped = meanMoved || spreadMoved;
            bandRule = "mean_band=" + fmt(meanBand) + " sd_ratio=" + fmt(ratio)
                    + " sd_limit=" + fmt(sdRatio)
                    + (meanMoved ? " MEAN_MOVED" : "") + (spreadMoved ? " SPREAD_MOVED" : "");
        }
        System.out.println("BIRTH old mean=" + fmt(sa[0]) + " sd=" + fmt(sa[1]) + " n=" + (long) sa[2]
                + " range=" + before.bandMin() + "-" + before.bandMax());
        System.out.println("BIRTH new mean=" + fmt(sb[0]) + " sd=" + fmt(sb[1]) + " n=" + (long) sb[2]
                + " range=" + after.bandMin() + "-" + after.bandMax());
        System.out.println("BAND rule=" + bandRule + " " + (reshaped ? "BEYOND_NOISE" : "within_noise"));

        // --- the paired churn: same seed, same fate? ---------------------------
        int churn = 0;
        for (Map.Entry<Long, String> e : before.verdicts().entrySet()) {
            String now = after.verdicts().get(e.getKey());
            if (now != null && !now.equals(e.getValue())) {
                churn++;
            }
        }
        System.out.println("CHURN seeds_changed=" + churn + "/" + n
                + " (paired; a held distribution is not a held multiverse)");
        // Rule 4, learned by running rule 1-3 for the first time: an entry that
        // NAMES seeds is not making a distributional claim, so no noise band
        // governs it. One seed changing fate falsifies the roster outright,
        // however STABLE the mix.
        System.out.println("ROSTER named_seed_claims="
                + (churn > 0 ? "INVALIDATED" : "intact")
                + " (any entry listing individual seeds must be restated when churn>0,"
                + " whatever the distributional verdict)");

        head.append(" band=").append(before.bandMin()).append('-').append(before.bandMax())
                .append("->").append(after.bandMin()).append('-').append(after.bandMax());
        String verdict = reshaped ? "RESHAPED" : shifted ? "SHIFTED" : "STABLE";
        System.out.println(head + " VERDICT " + verdict);
    }

    /** Parse SeedAtlas stdout: the SEED rows carry the sample, the ATLAS line the totals. */
    private static Table parse(String path) throws IOException {
        Map<Long, String> verdicts = new TreeMap<>();
        Map<Long, Long> births = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        long from = -1, to = -1, ticks = -1, bandMin = -1, bandMax = -1;

        for (String line : Files.readString(Path.of(path), StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("SEED ")) {
                String[] parts = line.trim().split("\\s+");
                long seed = Long.parseLong(parts[1]);
                Map<String, String> kv = kv(parts);
                verdicts.put(seed, kv.getOrDefault("verdict", "?"));
                long birth = Long.parseLong(kv.getOrDefault("birth", "-1"));
                if (birth >= 0) {
                    births.put(seed, birth);
                }
            } else if (line.startsWith("ATLAS ")) {
                Map<String, String> kv = kv(line.trim().split("\\s+"));
                String seeds = kv.getOrDefault("seeds", "-1..-1");
                from = Long.parseLong(seeds.substring(0, seeds.indexOf("..")));
                to = Long.parseLong(seeds.substring(seeds.indexOf("..") + 2));
                ticks = Long.parseLong(kv.getOrDefault("ticks", "-1"));
                bandMin = Long.parseLong(kv.getOrDefault("birth_min", "-1"));
                bandMax = Long.parseLong(kv.getOrDefault("birth_max", "-1"));
            }
        }
        // Counts are recomputed from the rows, never trusted from the summary:
        // the summary is the thing under test after a mechanics change.
        for (String v : verdicts.values()) {
            counts.merge(v.toLowerCase(), 1, Integer::sum);
        }
        return new Table(path, from, to, ticks, counts, verdicts, births, bandMin, bandMax);
    }

    private static Map<String, String> kv(String[] tokens) {
        Map<String, String> kv = new LinkedHashMap<>();
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                kv.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }
        return kv;
    }

    /** {mean, population sd, count} */
    private static double[] stats(Iterable<Long> values) {
        double sum = 0, n = 0;
        for (long v : values) {
            sum += v;
            n++;
        }
        if (n == 0) {
            return new double[] {0, 0, 0};
        }
        double mean = sum / n, sq = 0;
        for (long v : values) {
            sq += (v - mean) * (v - mean);
        }
        return new double[] {mean, Math.sqrt(sq / n), n};
    }

    private static String signed(long d) {
        return (d > 0 ? "+" : "") + d;
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private CensusReverdict() {}
}
