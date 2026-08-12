import java.util.Locale;
import java.util.Random;

/**
 * Probe: how many seeds a claim needs, per class of claim.
 *
 * The census's cheapest mistake is to run one sweep and let every sentence in
 * the write-up borrow its sample size. A fraction, a median and a RANKING are
 * three different statistics with three different appetites, and the third is
 * ravenous: an ordering of four clauses read off twenty runs is mostly noise.
 * This probe prices all three before the sweep is booked, so a table's claims
 * are sized in advance instead of defended afterwards.
 *
 * The three laws it computes:
 *
 *   FRACTION — n = (1.96/h)^2 * p(1-p) for a 95% half-width h. Reported at the
 *   worst case p=0.5 and at any observed p supplied.
 *
 *   MEDIAN — the sample median's 95% half-width is 1.96 * 1.2533 * sd / sqrt(n)
 *   for a roughly normal shape, so n = (2.4565 * sd / h)^2. Stated in units of
 *   sd, and stated WITH its assumption: a heavy tail makes this optimistic, and
 *   a census that has only ever measured a range does not know its own shape.
 *
 *   RANKING — no closed form worth trusting at these counts, so it is measured:
 *   a Monte Carlo over multinomial draws reports how often the observed order
 *   of k categories reproduces the true order, and how often the top category
 *   is merely correct. The gap between those two columns is why rankings are
 *   the most expensive sentence a census can print.
 *
 * The Monte Carlo is seeded (D-010: reproducible or it is an anecdote).
 *
 * Usage:
 *   java -cp out:probes/out CensusSampleSize [--p P] [--sd S] [--probs a,b,c,d]
 *                                            [--trials T] [--seed S]
 */
public final class CensusSampleSize {

    private static final double Z = 1.96;
    private static final long[] SAMPLES = {20, 50, 100, 200, 500, 1000};

    public static void main(String[] args) {
        double p = 0.17;                 // the first century's QUIET fraction
        double sd = 1;                   // median law is stated in units of sd
        double[] probs = {0.40, 0.28, 0.19, 0.13};   // four clauses, plausibly spread
        int trials = 20_000;
        long seed = 42;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--p" -> p = Double.parseDouble(args[++i]);
                case "--sd" -> sd = Double.parseDouble(args[++i]);
                case "--trials" -> trials = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--probs" -> {
                    String[] parts = args[++i].split(",");
                    probs = new double[parts.length];
                    for (int j = 0; j < parts.length; j++) {
                        probs[j] = Double.parseDouble(parts[j]);
                    }
                }
                default -> { }
            }
        }

        System.out.println("LAW fraction - n = (1.96/h)^2 * p(1-p)");
        for (double h : new double[] {0.10, 0.05, 0.03, 0.015, 0.01}) {
            System.out.println("FRACTION half_width=" + pct(h)
                    + " n_worst_case_p50=" + (long) Math.ceil(sq(Z / h) * 0.25)
                    + " n_at_p=" + fmt(p) + ":" + (long) Math.ceil(sq(Z / h) * p * (1 - p)));
        }
        for (long n : SAMPLES) {
            double[] ci = wilson(p, n);
            System.out.println("INTERVAL n=" + n + " p=" + fmt(p)
                    + " wilson95=[" + pct(ci[0]) + ", " + pct(ci[1]) + "]"
                    + " half_width=" + pct((ci[1] - ci[0]) / 2));
        }
        System.out.println("ZERO_RULE - k=0 in n bounds the rate at 3/n (95%): "
                + "n=100 -> <=" + pct(3.0 / 100) + " | n=1000 -> <=" + pct(3.0 / 1000)
                + " | a dead branch is only ever bounded, never proven");

        System.out.println("LAW median - n = (2.4565 * sd / h)^2, normal-shape assumption stated");
        for (double h : new double[] {0.50, 0.25, 0.10, 0.05}) {
            System.out.println("MEDIAN half_width=" + fmt(h) + "sd"
                    + " n=" + (long) Math.ceil(sq(Z * 1.2533 * sd / (h * sd))));
        }

        System.out.println("LAW ranking - measured, not derived (trials=" + trials
                + " seed=" + seed + " k=" + probs.length + " probs=" + fmtArr(probs) + ")");
        Random rng = new Random(seed);
        for (long n : SAMPLES) {
            int exact = 0, top1 = 0;
            for (int t = 0; t < trials; t++) {
                long[] counts = multinomial(rng, n, probs);
                if (isOrdered(counts)) {
                    exact++;
                }
                if (argmax(counts) == 0) {
                    top1++;
                }
            }
            System.out.println("RANKING n=" + n
                    + " exact_order_correct=" + pct(exact / (double) trials)
                    + " top1_correct=" + pct(top1 / (double) trials));
        }
        System.out.println("VERDICT SAMPLE_LAWS_PRICED");
    }

    /** Counts are generated in the true order, so "correct" means non-increasing. */
    private static boolean isOrdered(long[] counts) {
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] >= counts[i - 1]) {
                return false;   // ties are not a correct ordering either
            }
        }
        return true;
    }

    private static int argmax(long[] counts) {
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        for (int i = 0; i < counts.length; i++) {
            if (i != best && counts[i] == counts[best]) {
                return -1;      // a tie at the top is not a correct top-1
            }
        }
        return best;
    }

    private static long[] multinomial(Random rng, long n, double[] probs) {
        long[] counts = new long[probs.length];
        for (long i = 0; i < n; i++) {
            double u = rng.nextDouble(), acc = 0;
            for (int j = 0; j < probs.length; j++) {
                acc += probs[j];
                if (u < acc || j == probs.length - 1) {
                    counts[j]++;
                    break;
                }
            }
        }
        return counts;
    }

    /** Wilson score interval — behaves at the edges where the normal one lies. */
    private static double[] wilson(double p, long n) {
        double z2 = Z * Z;
        double centre = (p + z2 / (2 * n)) / (1 + z2 / n);
        double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n);
        return new double[] {Math.max(0, centre - half), Math.min(1, centre + half)};
    }

    private static double sq(double d) {
        return d * d;
    }

    private static String pct(double d) {
        return String.format(Locale.ROOT, "%.1f%%", 100 * d);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private static String fmtArr(double[] a) {
        StringBuilder sb = new StringBuilder();
        for (double d : a) {
            sb.append(sb.length() == 0 ? "" : ",").append(fmt(d));
        }
        return sb.toString();
    }

    private CensusSampleSize() {}
}
