import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probe: is a contiguous block of seeds a random sample of the multiverse?
 *
 * <p>Every fraction the census chapter has ever quoted rests on an untested
 * assumption — that seeds are exchangeable, so 100 of them in a row are 100
 * draws from one urn. Census entry 3 tested it by accident and it did not pass:
 * at one tree, seeds 1–100 and 101–200 disagreed on {@code QUIET} (21 vs 6,
 * z = 3.10) and {@code FULL_ARC} (73 vs 92, z = −3.54). This instrument is the
 * deliberate version of that accident.
 *
 * <h2>The design, and why it is not just "more blocks"</h2>
 *
 * A third and fourth contiguous block can only tell you whether the effect
 * repeats. It cannot tell you <i>why</i>, because every contiguous block
 * confounds two things: which universes are in it, and where on the seed axis
 * they sit. So this probe takes <b>groups</b> of blocks and tests each group
 * for homogeneity separately, and a seed simulated for one group is reused by
 * every other — which makes the decisive comparison free:
 *
 * <ul>
 *   <li><b>contiguous</b> — 1-100, 101-200, 201-300, 301-400: the partition the
 *       census actually uses.</li>
 *   <li><b>interleaved</b> — 1:4:100, 2:4:100, 3:4:100, 4:4:100: the <i>same
 *       400 universes</i>, dealt out like cards instead of cut like a deck.</li>
 * </ul>
 *
 * Identical data, two partitions. If the contiguous partition is heterogeneous
 * and the interleaved one is not, the structure is <b>along the seed axis</b>
 * and no amount of extra sampling down that axis will fix a fraction. If both
 * are heterogeneous, the universes themselves are overdispersed and the seed is
 * innocent. If neither is, entry 3 caught the 1-in-500. That is a three-way
 * decision the original two-block comparison could not make at any sample size,
 * and it costs zero extra universes.
 *
 * <p>A third group, <b>span</b> (1-400 against 1001:100:100), asks the other
 * half of the question: do seeds four orders of magnitude apart behave like
 * neighbours? A seeding weakness that decorrelates slowly would show here and
 * nowhere else.
 *
 * <h2>The statistics, stated before the numbers were seen</h2>
 *
 * Per group and per fate, Pearson's homogeneity chi-square across the group's k
 * blocks (df = k−1), its overdispersion ratio {@code phi = chi2/df} — 1.0 is
 * exactly binomial, and the fate-mix is the only thing being tested — and the
 * upper-tail p. A fate that is 0 everywhere or n everywhere is <b>not testable</b>
 * and is skipped rather than counted as agreement: {@code OLD_PLAYBOOK} at 0/400
 * is not evidence of exchangeability, it is no evidence at all.
 *
 * <p>The group's verdict is {@code BLOCK_EFFECT} when the smallest tested p
 * falls under a <b>Bonferroni</b> threshold of 0.05/M over the M testable fates,
 * else {@code BLOCKS_EXCHANGEABLE}. The correction is not decoration: entry 3's
 * z = 3.10 was the first comparison anyone made, which is what made it alarming,
 * and this probe makes many — so it must pay for them. Pairwise z's are printed
 * as description, never as the verdict.
 *
 * <p>Determinism: seeds are memoised and simulated once each, in a fixed pool;
 * every {@code Simulation} is private to its call ({@link SeedAtlas#census}), so
 * {@code --threads 4} and {@code --threads 1} print byte-identical output. All
 * statistics are computed after the last universe lands, never as they arrive.
 *
 * <pre>
 * Usage: java -cp out:probes/out CensusBlocks [--threads N] [--group NAME] &lt;spec&gt;... [ticks]
 *
 *   spec   A-B        contiguous seeds A..B          (e.g. 101-200)
 *          A:S:N      N seeds from A, stride S       (e.g. 1:4:100 -&gt; 1,5,9,...,397)
 *   ticks  a bare number, last (default 6000)
 * </pre>
 */
public final class CensusBlocks {

    /** The fates, in the order the census chapter's tables print them. */
    private static final String[] FATES =
            {"FULL_ARC", "TREATY", "WAR", "QUIET", "OLD_PLAYBOOK"};

    private record Block(String group, String spec, long[] seeds) {}

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        List<Block> blocks = new ArrayList<>();
        long ticks = 6_000;
        int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
        String group = "all";

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--selfcheck")) {
                selfcheck();
                return;
            } else if (a.equals("--group")) {
                group = args[++i];
            } else if (a.equals("--threads")) {
                threads = Integer.parseInt(args[++i]);
            } else if (a.matches("\\d+")) {
                ticks = Long.parseLong(a);              // a bare number is the tick budget
            } else {
                blocks.add(new Block(group, a, parseSpec(a)));
            }
        }
        if (blocks.isEmpty()) {
            System.err.println("FATAL no blocks given; e.g. CensusBlocks 1-100 101-200 6000");
            System.exit(Probes.Outcome.REFUSED.code());
        }

        // One universe per distinct seed, however many blocks quote it. This is what
        // makes the interleaved partition free: it re-reads the contiguous group's
        // 400 universes rather than running 400 more.
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Block b : blocks) {
            for (long s : b.seeds()) {
                distinct.add(s);
            }
        }

        long t0 = System.nanoTime();
        Map<Long, SeedAtlas.Row> rows = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        final long fticks = ticks;
        for (long seed : distinct) {
            pool.submit(() -> {
                rows.put(seed, SeedAtlas.census(seed, fticks));
                int d = done.incrementAndGet();
                if (d % 10 == 0 || d == distinct.size()) {
                    System.err.printf(Locale.ROOT, "... %d/%d universes, %.0f s elapsed%n",
                            d, distinct.size(), (System.nanoTime() - t0) / 1e9);
                }
            });
        }
        pool.shutdown();
        if (!pool.awaitTermination(24, TimeUnit.HOURS)) {
            System.err.println("FATAL sweep did not finish");
            System.exit(Probes.Outcome.BROKE.code());
        }
        double wall = (System.nanoTime() - t0) / 1e9;
        if (rows.size() != distinct.size()) {
            System.err.println("FATAL a universe failed: " + rows.size() + "/" + distinct.size());
            System.exit(Probes.Outcome.BROKE.code());
        }

        System.out.printf(Locale.ROOT,
                "CENSUS_BLOCKS ticks=%d threads=%d blocks=%d distinct_seeds=%d wall_s=%.1f%n",
                ticks, threads, blocks.size(), distinct.size(), wall);

        // Group order is first-appearance order, so output follows the command line.
        Map<String, List<Block>> groups = new LinkedHashMap<>();
        for (Block b : blocks) {
            groups.computeIfAbsent(b.group(), k -> new ArrayList<>()).add(b);
        }
        for (Map.Entry<String, List<Block>> e : groups.entrySet()) {
            report(e.getKey(), e.getValue(), rows, ticks);
        }
    }

    /** One group: its blocks' rows, its per-fate homogeneity, its verdict. */
    private static void report(String group, List<Block> blocks,
                               Map<Long, SeedAtlas.Row> rows, long ticks) {
        int k = blocks.size();
        int[][] x = new int[k][FATES.length];           // x[block][fate]
        int[] n = new int[k];

        for (int i = 0; i < k; i++) {
            long[] seeds = blocks.get(i).seeds();
            n[i] = seeds.length;
            List<Long> births = new ArrayList<>();
            for (long s : seeds) {
                SeedAtlas.Row r = rows.get(s);
                for (int f = 0; f < FATES.length; f++) {
                    if (FATES[f].equals(r.verdict())) {
                        x[i][f]++;
                    }
                }
                if (r.birth() >= 0) {
                    births.add(r.birth());
                }
            }
            double mean = 0, sd = 0;
            long bmin = -1, bmax = -1;
            if (!births.isEmpty()) {
                for (long b : births) {
                    mean += b;
                }
                mean /= births.size();
                for (long b : births) {
                    sd += (b - mean) * (b - mean);
                }
                sd = births.size() > 1 ? Math.sqrt(sd / (births.size() - 1)) : 0;
                bmin = births.stream().mapToLong(Long::longValue).min().getAsLong();
                bmax = births.stream().mapToLong(Long::longValue).max().getAsLong();
            }
            System.out.printf(Locale.ROOT,
                    "BLOCK group=%s spec=%s n=%d full_arc=%d treaty=%d war=%d quiet=%d"
                            + " old_playbook=%d birth_n=%d birth_mean=%.1f birth_sd=%.1f"
                            + " birth_min=%d birth_max=%d%n",
                    group, blocks.get(i).spec(), n[i],
                    x[i][0], x[i][1], x[i][2], x[i][3], x[i][4],
                    births.size(), mean, sd, bmin, bmax);
        }

        if (k < 2) {
            System.out.printf(Locale.ROOT,
                    "BLOCKS group=%s k=%d VERDICT NOT_TESTABLE - a group of one block has no"
                            + " homogeneity to test (df=0); it is reported for description only%n",
                    group, k);
            return;
        }

        // Which fates can be tested at all: a fate seen 0 times, or every time, has no
        // variance to explain and is skipped. Skipped is not "agreed".
        List<Integer> testable = new ArrayList<>();
        int nAll = 0;
        for (int j : n) {
            nAll += j;
        }
        for (int f = 0; f < FATES.length; f++) {
            int tot = 0;
            for (int i = 0; i < k; i++) {
                tot += x[i][f];
            }
            if (tot > 0 && tot < nAll) {
                testable.add(f);
            }
        }
        double alpha = testable.isEmpty() ? Double.NaN : 0.05 / testable.size();

        double maxZ = 0, maxPhi = 0, minP = Double.NaN;
        String maxZFate = "-", maxZPair = "-", minPFate = "-";

        for (int f : testable) {
            int tot = 0;
            for (int i = 0; i < k; i++) {
                tot += x[i][f];
            }
            int[] col = new int[k];
            for (int i = 0; i < k; i++) {
                col[i] = x[i][f];
            }
            double chi2 = chi2Homog(col, n);
            int df = k - 1;
            double phi = chi2 / df;
            double p = chiSqUpper(chi2, df);
            System.out.printf(Locale.ROOT,
                    "HOMOGENEITY group=%s metric=%s k=%d chi2=%.3f df=%d p=%.5f phi=%.2f%n",
                    group, FATES[f], k, chi2, df, p, phi);
            maxPhi = Math.max(maxPhi, phi);
            if (Double.isNaN(minP) || p < minP) {
                minP = p;
                minPFate = FATES[f];
            }

            for (int a = 0; a < k; a++) {
                for (int b = a + 1; b < k; b++) {
                    double z = twoPropZ(x[a][f], n[a], x[b][f], n[b]);
                    if (Double.isNaN(z)) {
                        continue;
                    }
                    System.out.printf(Locale.ROOT,
                            "PAIR group=%s metric=%s a=%s b=%s a_x=%d/%d b_x=%d/%d z=%.2f p=%.5f%n",
                            group, FATES[f], blocks.get(a).spec(), blocks.get(b).spec(),
                            x[a][f], n[a], x[b][f], n[b], z, normalTwoTailed(z));
                    if (Math.abs(z) > Math.abs(maxZ)) {
                        maxZ = z;
                        maxZFate = FATES[f];
                        maxZPair = blocks.get(a).spec() + "/" + blocks.get(b).spec();
                    }
                }
            }
        }

        if (testable.isEmpty()) {
            System.out.printf(Locale.ROOT,
                    "BLOCKS group=%s k=%d n=%d metrics_tested=0 VERDICT NOT_TESTABLE - every fate"
                            + " is degenerate in this group (all-or-nothing), so nothing was"
                            + " compared; this is not agreement%n", group, k, nAll);
            return;
        }
        String verdict = minP < alpha ? "BLOCK_EFFECT" : "BLOCKS_EXCHANGEABLE";
        System.out.printf(Locale.ROOT,
                "BLOCKS group=%s k=%d n=%d metrics_tested=%d max_pairwise_z=%.2f max_z_metric=%s"
                        + " max_z_pair=%s overdispersion=%.2f min_p=%.5f min_p_metric=%s"
                        + " bonferroni_alpha=%.4f VERDICT %s%n",
                group, k, nAll, testable.size(), maxZ, maxZFate, maxZPair,
                maxPhi, minP, minPFate, alpha, verdict);
    }

    /** {@code A-B} contiguous, or {@code A:S:N} — N seeds from A with stride S. */
    private static long[] parseSpec(String spec) {
        if (spec.contains(":")) {
            String[] p = spec.split(":");
            if (p.length != 3) {
                throw new IllegalArgumentException("bad strided spec: " + spec + " (want A:S:N)");
            }
            long a = Long.parseLong(p[0]);
            long s = Long.parseLong(p[1]);
            int c = Integer.parseInt(p[2]);
            long[] out = new long[c];
            for (int i = 0; i < c; i++) {
                out[i] = a + (long) i * s;
            }
            return out;
        }
        int dash = spec.indexOf('-', 1);
        if (dash < 0) {
            throw new IllegalArgumentException("bad spec: " + spec + " (want A-B or A:S:N)");
        }
        long a = Long.parseLong(spec.substring(0, dash));
        long b = Long.parseLong(spec.substring(dash + 1));
        if (b < a) {
            throw new IllegalArgumentException("bad range: " + spec);
        }
        long[] out = new long[(int) (b - a + 1)];
        for (int i = 0; i < out.length; i++) {
            out[i] = a + i;
        }
        return out;
    }

    // ---- statistics -------------------------------------------------------
    // Zero-dependency by rule (D-040): the distributions are implemented here
    // rather than pulled in, and each is the textbook form with its own check.

    /** Two-tailed p for a standard normal deviate. */
    private static double normalTwoTailed(double z) {
        return erfc(Math.abs(z) / Math.sqrt(2));
    }

    /** Pooled two-proportion z. NaN when the pooled proportion is degenerate. */
    private static double twoPropZ(int xa, int na, int xb, int nb) {
        double pa = (double) xa / na;
        double pb = (double) xb / nb;
        double pp = (double) (xa + xb) / (na + nb);
        double se = Math.sqrt(pp * (1 - pp) * (1.0 / na + 1.0 / nb));
        return se == 0 ? Double.NaN : (pa - pb) / se;
    }

    /** Pearson homogeneity chi-square for k proportions, pooled under the null. */
    private static double chi2Homog(int[] x, int[] n) {
        int tot = 0;
        int nAll = 0;
        for (int i = 0; i < x.length; i++) {
            tot += x[i];
            nAll += n[i];
        }
        double pBar = (double) tot / nAll;
        double chi2 = 0;
        for (int i = 0; i < x.length; i++) {
            double exp = n[i] * pBar;
            double var = n[i] * pBar * (1 - pBar);
            chi2 += (x[i] - exp) * (x[i] - exp) / var;
        }
        return chi2;
    }

    /**
     * Check the arithmetic against the numbers that caused this probe to exist,
     * without running a single universe.
     *
     * <p>Census entry 3 published z = 3.10 for {@code QUIET} (21/100 vs 6/100) and
     * z = −3.54 for {@code FULL_ARC} (73/100 vs 92/100). If this instrument cannot
     * reproduce those two figures it is not entitled to overturn them.
     *
     * <p>The second assertion is the one that costs an implementer sleep: at df = 1
     * the homogeneity chi-square is exactly z², so {@link #chiSqUpper} and
     * {@link #normalTwoTailed} must agree — and they reach the same p by completely
     * separate routes (an incomplete-gamma continued fraction versus a Chebyshev
     * erfc). Agreement to 1e-6 is two independent implementations checking each
     * other, which is the only kind of check a zero-dependency probe can afford.
     */
    private static void selfcheck() {
        int[][] cases = {{21, 100, 6, 100}, {73, 100, 92, 100}};
        String[] names = {"QUIET", "FULL_ARC"};
        double[] published = {3.10, -3.54};
        boolean ok = true;
        for (int i = 0; i < cases.length; i++) {
            int[] c = cases[i];
            double z = twoPropZ(c[0], c[1], c[2], c[3]);
            double pz = normalTwoTailed(z);
            double chi2 = chi2Homog(new int[] {c[0], c[2]}, new int[] {c[1], c[3]});
            double pc = chiSqUpper(chi2, 1);
            boolean zOk = Math.abs(z - published[i]) < 0.005;
            boolean idOk = Math.abs(chi2 - z * z) < 1e-9 && Math.abs(pc - pz) < 1e-6;
            ok &= zOk && idOk;
            System.out.printf(Locale.ROOT,
                    "SELFCHECK entry3 metric=%s a=%d/%d b=%d/%d z=%.2f published_z=%.2f"
                            + " p_normal=%.5f chi2=%.4f z_squared=%.4f p_chi=%.5f %s%n",
                    names[i], c[0], c[1], c[2], c[3], z, published[i], pz, chi2, z * z, pc,
                    (zOk && idOk) ? "OK" : "MISMATCH");
        }
        System.out.println("SELFCHECK basis census entry 3's published z values reproduce, and the"
                + " chi-square upper tail agrees with the normal two-tailed p at df=1, where chi2"
                + " must equal z squared - two implementations checking each other");
        // The verdict is its own short line so probes/bench.sh can judge it with
        // grep -qxF, the exact-line rule every judged probe on the bench obeys.
        System.out.println(ok ? "SELFCHECK VERDICT MATH_OK" : "SELFCHECK VERDICT MATH_BROKEN");
        if (!ok) {
            System.exit(Probes.Outcome.BROKE.code());
        }
    }

    /** Chi-square upper tail: P(X > chi2) for X ~ chi-square with df degrees of freedom. */
    private static double chiSqUpper(double chi2, int df) {
        return gammaQ(df / 2.0, chi2 / 2.0);
    }

    /** Regularised upper incomplete gamma Q(a,x) = 1 − P(a,x). */
    private static double gammaQ(double a, double x) {
        if (x < 0 || a <= 0) {
            return Double.NaN;
        }
        if (x == 0) {
            return 1.0;
        }
        if (x < a + 1.0) {
            // Series for P(a,x), then complement — converges fast on this side.
            double ap = a;
            double sum = 1.0 / a;
            double del = sum;
            for (int i = 0; i < 500; i++) {
                ap++;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * 1e-15) {
                    break;
                }
            }
            return 1.0 - sum * Math.exp(-x + a * Math.log(x) - lnGamma(a));
        }
        // Continued fraction for Q(a,x) directly (Lentz's method).
        double tiny = 1e-300;
        double b = x + 1.0 - a;
        double c = 1.0 / tiny;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i <= 500; i++) {
            double an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < tiny) {
                d = tiny;
            }
            c = b + an / c;
            if (Math.abs(c) < tiny) {
                c = tiny;
            }
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < 1e-15) {
                break;
            }
        }
        return Math.exp(-x + a * Math.log(x) - lnGamma(a)) * h;
    }

    /** Lanczos log-gamma. */
    private static double lnGamma(double xx) {
        double[] cof = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                        -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double x = xx;
        double y = xx;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (int j = 0; j < 6; j++) {
            ser += cof[j] / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    /** Complementary error function, Chebyshev fit (fractional error &lt; 1.2e-7). */
    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 2.0 / (2.0 + z);
        double ty = 4.0 * t - 2.0;
        double[] cof = {-1.3026537197817094, 6.4196979235649026e-1, 1.9476473204185836e-2,
                        -9.561514786808631e-3, -9.46595344482036e-4, 3.66839497852761e-4,
                        4.2523324806907e-5, -2.0278578112534e-5, -1.624290004647e-6,
                        1.303655835580e-6, 1.5626441722e-8, -8.5238095915e-8,
                        6.529054439e-9, 5.059343495e-9, -9.91364156e-10,
                        -2.27365122e-10, 9.6467911e-11};
        double d = 0.0;
        double dd = 0.0;
        for (int j = cof.length - 1; j > 0; j--) {
            double tmp = d;
            d = ty * d - dd + cof[j];
            dd = tmp;
        }
        double ans = t * Math.exp(-z * z + 0.5 * (cof[0] + ty * d) - dd);
        return x >= 0.0 ? ans : 2.0 - ans;
    }

    private CensusBlocks() {}
}
