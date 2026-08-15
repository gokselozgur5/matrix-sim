import matrix.character.Contest;
import matrix.character.Family;
import matrix.character.Sheet;
import matrix.character.Sheets;
import matrix.core.NamePool;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Probe: what the character kernel derives, and whether its mixer is worth
 * believing. The kernel is imported by nothing in the domain — adoption is
 * residency, not coupling — so this bench is the only place its numbers
 * exist. It derives the named cast, plays five famous cross-family contests
 * as pure arithmetic, and, on demand, measures the mixer that produced them.
 *
 * Identity in, fate out: no Simulation, no seed, no draw. Deterministic
 * output — run it twice, diff nothing.
 *
 * Usage:
 *   java -cp out:probes/out SheetBench                the cast and five scenes
 *   java -cp out:probes/out SheetBench --vocab        the four vocabularies, order as canon
 *   java -cp out:probes/out SheetBench --hunt-axis    which word carries the hunt
 *   java -cp out:probes/out SheetBench --discipline   a human asked for replication
 *   java -cp out:probes/out SheetBench --avalanche    the mixer measured on the farm's own name pool
 *   java -cp out:probes/out SheetBench --bands        where the derived population lands in the five bands
 */
public final class SheetBench {

    /**
     * Cross-axis correlation bound. The pool is 400 names, so under
     * independence the sampling error of a Pearson r is about
     * 1/sqrt(400) = 0.05; three of those is the line between "noise" and
     * "these two axes are the same axis". The Kid's monoculture is why this
     * is measured rather than assumed — that defect was found by
     * enumeration and by nothing else.
     */
    private static final double CORR_BOUND = 0.15;

    /** A strict-avalanche mixer flips half the output bits; this is the tolerance around it. */
    private static final double BITFLIP_TOLERANCE = 0.01;

    /**
     * How many values an axis bands into — {@link Sheet}'s own contract, 1..10,
     * and the number every band expectation below is a function of. Named once
     * so the band measurement cannot go on quoting 10 after the range moves.
     */
    private static final int STAT_VALUES = 10;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        String mode = args.length > 0 ? args[0] : "";
        switch (mode) {
            case "--vocab" -> vocab();
            case "--hunt-axis" -> huntAxis();
            case "--discipline" -> discipline();
            case "--avalanche" -> System.exit(avalanche());
            case "--bands" -> System.exit(bands());
            case "" -> cast();
            default -> {
                System.err.println("unknown mode: " + mode
                        + " (try --vocab, --hunt-axis, --discipline, --avalanche, --bands)");
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }
    }

    /**
     * The named cast, derived — the ten the gate argued over, in the order
     * this bench has always printed them. It is a method rather than ten
     * locals because it has a second reader now: {@code SheetDump --cast}
     * prints the same ten as part of the census (#535), and two literal
     * lists in two probes drift the day a name is added to one of them.
     * #479 moves this table out of source entirely; until it lands, the
     * cast lives in exactly one place and this is the place.
     */
    static List<Sheet> namedCast() {
        return List.of(
                Sheets.derive("Trinity", Family.HUMAN),
                Sheets.derive("Morpheus", Family.HUMAN),
                Sheets.derive("Niobe", Family.HUMAN),
                Sheets.derive("Cypher", Family.HUMAN),
                Sheets.derive("Thomas A. Anderson", Family.HUMAN),
                Sheets.derive("the Architect", Family.SYSTEM),
                Sheets.derive("the Oracle", Family.PROGRAM),
                Sheets.derive("Otto Aydin", Family.MACHINE),
                Sheets.derive("Agent Smith", Family.PROGRAM),
                Sheets.derive("Agent Jones", Family.PROGRAM));
    }

    /**
     * One member of the cast by name. The scenes below name their operands
     * the way a reader does — "Trinity", not index 0 — so an inserted cast
     * member cannot silently re-cast the rooftop, and a missing one is a
     * refusal at the line that asked rather than a wrong sheet.
     */
    private static Sheet member(List<Sheet> cast, String name) {
        for (Sheet sheet : cast) {
            if (sheet.name().equals(name)) {
                return sheet;
            }
        }
        throw new IllegalStateException("no '" + name + "' in the named cast");
    }

    /** The exhibit the gate argued over: ten sheets, five scenes, one count line. */
    private static void cast() {
        List<Sheet> cast = namedCast();
        Sheet trinity = member(cast, "Trinity");
        Sheet morpheus = member(cast, "Morpheus");
        Sheet niobe = member(cast, "Niobe");
        Sheet thomas = member(cast, "Thomas A. Anderson");
        Sheet architect = member(cast, "the Architect");
        Sheet otto = member(cast, "Otto Aydin");
        Sheet smith = member(cast, "Agent Smith");
        Sheet jones = member(cast, "Agent Jones");

        for (Sheet sheet : cast) {
            System.out.println("SHEET " + sheet.line());
        }

        // Five cross-family scenes, as arithmetic. Every family appears at
        // least once; the rooftop now asks for the hunt by its own name
        // instead of borrowing privilege, which is what the append bought.
        contest("rooftop", trinity, "evasion", jones, Family.HUNT_AXIS);
        contest("the-room", architect, "authority", thomas, "will");
        contest("the-overflow", smith, "replication", architect, "tolerance");
        contest("mechanical-line", niobe, "evasion", otto, "precision");
        contest("interrogation", morpheus, "will", smith, "purposeIntegrity");

        System.out.println("BENCH cast=" + cast.size() + " contests=5");
    }

    private static void contest(String scene, Sheet a, String axisA, Sheet b, String axisB) {
        System.out.println(String.format(Locale.ROOT,
                "CONTEST %s %s.%s=%d vs %s.%s=%d margin=%+d %s",
                scene, a.name(), axisA, a.stat(axisA), b.name(), axisB, b.stat(axisB),
                Contest.margin(a, axisA, b, axisB), Contest.resolve(a, axisA, b, axisB)));
    }

    /**
     * The four vocabularies, left to right in canonical order — per family
     * first (a reader greps one family), then all four on one line (a
     * reviewer diffs the whole grammar).
     */
    private static void vocab() {
        StringBuilder all = new StringBuilder("VOCAB");
        for (Family f : Family.values()) {
            String words = String.join(" ", f.axes());
            System.out.println("VOCAB " + f + " " + words);
            all.append(all.length() > 5 ? " |" : "").append(' ').append(f).append(' ').append(words);
        }
        System.out.println(all);
    }

    /** Which word carries the hunt, and on whose authority. */
    private static void huntAxis() {
        System.out.println("HUNT AXIS program." + Family.HUNT_AXIS
                + " — appended by the D-042 verdict");
    }

    /**
     * Vocabulary discipline, proven rather than promised: a human asked for
     * replication must throw, and a human asked for its own word must not.
     * The repo carries no test framework by choice (D-009) — the bench is
     * where an assertion lives.
     */
    private static void discipline() {
        Sheet human = Sheets.derive("Thomas A. Anderson", Family.HUMAN);
        String refusal;
        try {
            human.stat("replication");
            refusal = null;
        } catch (IllegalArgumentException e) {
            refusal = e.getMessage();
        }
        boolean ownWordAnswers = human.stat("disbelief") >= 1;
        System.out.println("DISCIPLINE ask=HUMAN.replication threw=" + (refusal != null)
                + " ask=HUMAN.disbelief answered=" + ownWordAnswers);
        if (refusal != null) {
            System.out.println("DISCIPLINE message=\"" + refusal + "\"");
        }
        System.out.println("DISCIPLINE VERDICT "
                + (refusal != null && ownWordAnswers ? "PASS" : "FAIL"));
    }

    /**
     * The mixer, measured on the farm's own 400-name pool — the population
     * the world will actually derive from.
     *
     * <p>Two questions, because the parked kernel's review named exactly
     * two: does the finalizer avalanche (flip one input bit, half the output
     * bits move), and are the axes independent (or is a name's evasion just
     * its will wearing a hat)? The first is measured over every bit of every
     * (name, family, axis) mix word; the second is a Pearson correlation of
     * banded values across every axis pair within a family.
     */
    private static int avalanche() throws Exception {
        List<String> pool = namePool();
        Method mix = open("mix", String.class, Family.class, int.class);
        Method avalanche = open("avalanche", int.class);

        long flips = 0;
        long trials = 0;
        int axes = 0;
        double worstCorr = 0.0;
        String worstPair = "none";

        for (Family family : Family.values()) {
            axes += family.axisCount();
            int n = family.axisCount();
            double[][] values = new double[n][pool.size()];
            for (int axis = 0; axis < n; axis++) {
                for (int i = 0; i < pool.size(); i++) {
                    int word = (int) mix.invoke(null, pool.get(i), family, axis);
                    values[axis][i] = 1 + Math.floorMod(word, 10);
                    // Strict avalanche on the finalizer: one input bit in,
                    // count the output bits that moved.
                    int mixed = (int) avalanche.invoke(null, word);
                    for (int bit = 0; bit < 32; bit++) {
                        int other = (int) avalanche.invoke(null, word ^ (1 << bit));
                        flips += Integer.bitCount(mixed ^ other);
                        trials += 32;
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double r = Math.abs(pearson(values[i], values[j]));
                    if (r > worstCorr) {
                        worstCorr = r;
                        worstPair = family + "." + family.axes()[i] + "~" + family.axes()[j];
                    }
                }
            }
        }

        double meanBitflip = (double) flips / trials;
        boolean avalancheOk = Math.abs(meanBitflip - 0.5) <= BITFLIP_TOLERANCE;
        boolean corrOk = worstCorr <= CORR_BOUND;
        System.out.println(String.format(Locale.ROOT, "AVALANCHE names=%d axes=%d mean_bitflip=%.4f"
                        + " max_axis_corr=%.4f pair=%s bound=%.2f VERDICT %s",
                pool.size(), axes, meanBitflip, worstCorr, worstPair, CORR_BOUND,
                avalancheOk && corrOk ? "PASS" : "FAIL"));
        // The line above verdicts on two conjuncts and prints one of their
        // bounds, which makes the mixer's own measurement unreadable: nothing
        // in it answers |0.5001 - 0.5| <= what, and the trailing bound=0.15
        // reads as if it covered mean_bitflip, a tolerance fifteen times
        // looser than the one actually applied. Tightening BITFLIP_TOLERANCE
        // moved the last word and not one number. The legs go on their own
        // appended line rather than into that one, because bench.sh reads
        // this mode's exit code and a mid-line insertion is a break (D-020).
        System.out.println(String.format(Locale.ROOT,
                        "AVALANCHE legs bitflip=%.4f/%s %s corr=%.4f/%s %s VERDICT %s",
                meanBitflip, bound(BITFLIP_TOLERANCE), avalancheOk ? "PASS" : "FAIL",
                worstCorr, bound(CORR_BOUND), corrOk ? "PASS" : "FAIL",
                avalancheOk && corrOk ? "PASS" : "FAIL"));
        return avalancheOk && corrOk ? 0 : 1;
    }

    /**
     * A bound printed at the precision it was written with. A fixed {@code %.2f}
     * would print a 0.00001 tolerance as {@code 0.00} and hand the reader a
     * bound the probe is not applying — the failure this line exists to end.
     */
    private static String bound(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * Every name the pod farm can grow — {@link NamePool}'s first x family,
     * 400 of them. The same enumeration FateAtlas walks: a mixer is judged
     * on the population it will serve, not on a bench of ten.
     */
    private static List<String> namePool() throws Exception {
        List<String> pool = new ArrayList<>();
        for (String first : NamePool.firstNames()) {
            for (String last : NamePool.familyNames()) {
                pool.add(first + " " + last);
            }
        }
        return pool;
    }

    // ---- #835: the bands, measured on the population that will fight in them ----

    /** One vocabulary word and the family that owns it — what a contest names on each side. */
    private record Slot(Family family, String axis) {

        String label() {
            return family + "." + axis;
        }
    }

    /** A matchup the record actually stages, under the scene name it stages it in. */
    private record Staged(String scene, Slot a, Slot b) {}

    /**
     * Where the derived population lands in {@link Contest}'s five bands.
     *
     * <p>The thresholds shipped as an assertion — {@code >=+4} DECISIVE,
     * {@code +1..+3} EDGE, {@code 0} TIE — with the class itself saying no
     * one had measured them, and the failure they were afraid of named: a
     * vocabulary where most encounters tie is a vocabulary with no drama.
     * This is that measurement, and it is an enumeration rather than a
     * sample: every name the farm can grow, derived into every family, run
     * against every other name through {@link Contest#resolve} itself — no
     * reimplementation of the law, no draw, no seed.
     *
     * <p><b>What the answer is compared against.</b> The values are 1..10,
     * so if the mixer bands uniformly and independently the margin
     * distribution is exactly {@code P(d) = (10-|d|)/100} and each band's
     * share follows. That analytic table is computed here by walking all one
     * hundred (v, w) value pairs through {@link Contest#band} — it is
     * derived from the thresholds rather than transcribed beside them, so it
     * follows the law if a later verdict moves it. TIE is the band the
     * verdict hangs on, because TIE is the one whose expectation survives
     * any non-uniformity in a single axis: its share is
     * {@code sum_v p_v q_v}, which has no first-order term in either
     * histogram's error. The other four bands do, and they are printed
     * beside their expectations for the reader rather than gated.
     *
     * <p><b>Self-contests are excluded.</b> The pool is one pool used for
     * all four families, so the {@code i == j} cell is a name against its
     * own alias in another family, or a soul against itself within one —
     * neither is a matchup the world stages, and on the same-axis lane it
     * would be a guaranteed tie by construction. 400 names give 159,600
     * ordered pairs of distinct souls per matchup.
     */
    private static int bands() throws Exception {
        List<String> pool = namePool();
        Map<Family, List<Sheet>> derived = new EnumMap<>(Family.class);
        for (Family family : Family.values()) {
            List<Sheet> sheets = new ArrayList<>(pool.size());
            for (String name : pool) {
                sheets.add(Sheets.derive(name, family));
            }
            derived.put(family, sheets);
        }

        List<Slot> slots = new ArrayList<>();
        for (Family family : Family.values()) {
            for (String axis : family.axes()) {
                slots.add(new Slot(family, axis));
            }
        }

        // The mechanism under the bands: one axis's own 400 values. Everything
        // downstream is a convolution of two of these histograms, so a band
        // share can only drift as far as a histogram does.
        double worstChi = 0.0;
        String worstChiSlot = "none";
        for (Slot slot : slots) {
            int[] hist = new int[STAT_VALUES + 1];
            for (Sheet sheet : derived.get(slot.family())) {
                hist[sheet.stat(slot.axis())]++;
            }
            double expect = pool.size() / (double) STAT_VALUES;
            int min = Integer.MAX_VALUE;
            int max = 0;
            double chi = 0.0;
            long sum = 0;
            for (int v = 1; v <= STAT_VALUES; v++) {
                min = Math.min(min, hist[v]);
                max = Math.max(max, hist[v]);
                sum += (long) v * hist[v];
                chi += (hist[v] - expect) * (hist[v] - expect) / expect;
            }
            if (chi > worstChi) {
                worstChi = chi;
                worstChiSlot = slot.label();
            }
            System.out.println(String.format(Locale.ROOT,
                    "BANDSLOT %-25s n=%d min=%d max=%d mean=%.3f chisq=%.2f",
                    slot.label(), pool.size(), min, max, sum / (double) pool.size(), chi));
        }
        System.out.println(String.format(Locale.ROOT,
                "BANDSLOT WORST %s chisq=%.2f df=9 crit_p001=27.88", worstChiSlot, worstChi));

        double[] analytic = analytic();
        int tie = Contest.Outcome.TIE.ordinal();

        // The five matchups the record stages by name, each in its own
        // orientation — the rooftop is a runner's evasion against a hunt, and
        // which side is A is part of the scene.
        for (Staged staged : staged()) {
            long[] counts = tally(derived, staged.a(), staged.b());
            System.out.println(String.format(Locale.ROOT, "BANDROW %-16s %s vs %s contests=%d %s",
                    staged.scene(), staged.a().label(), staged.b().label(), total(counts), shares(counts)));
        }

        // Every distinct pair of vocabulary words, in canonical slot order, so
        // an A-side lead in the aggregate is a real fact about which axes
        // derive higher and not an artifact of how the pairs were listed.
        long[] sweep = new long[Contest.Outcome.values().length];
        int pairs = 0;
        double worstTieDelta = 0.0;
        String worstTiePair = "none";
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                long[] counts = tally(derived, slots.get(i), slots.get(j));
                pairs++;
                for (int k = 0; k < counts.length; k++) {
                    sweep[k] += counts[k];
                }
                double delta = share(counts, tie) - analytic[tie];
                if (Math.abs(delta) > Math.abs(worstTieDelta)) {
                    worstTieDelta = delta;
                    worstTiePair = slots.get(i).label() + "~" + slots.get(j).label();
                }
            }
        }

        // The lane the law permits and the sweep leaves out: two souls on the
        // SAME word — one runner outrunning another. Both sides then read one
        // histogram, so the lane has a hard floor no mixer can get under,
        // (n/k - 1)/(n - 1), reached only by a perfectly flat histogram.
        // Its EXPECTATION is still exactly 1/k once self-contests are dropped,
        // which is why the floor sits below 1/k rather than at it.
        long[] sameAxis = new long[Contest.Outcome.values().length];
        for (Slot slot : slots) {
            long[] counts = tally(derived, slot, slot);
            for (int k = 0; k < counts.length; k++) {
                sameAxis[k] += counts[k];
            }
        }

        double tolerance = tieTolerance(STAT_VALUES, pool.size());
        double measured = share(sweep, tie);
        double delta = measured - analytic[tie];
        boolean agree = Math.abs(delta) <= tolerance;

        // The law, read against itself. Everything else here measures the
        // POPULATION against Contest.band, and the analytic table is derived
        // from that same function on purpose — which means it follows an
        // illegitimate move of the thresholds as obediently as a legitimate
        // one. This line asks the other question: do the kernel's two tables
        // still say the same thing, and does band still mirror? It is an
        // exhibit and not the gate — Contest's class init refuses to load a
        // tree where they disagree, so a broken law throws before this probe
        // prints a line — and probes/bench.sh judges this row so that a lane,
        // rather than a person running the command, is the one who finds out.
        boolean symmetric = Contest.firstAsymmetry() == 0;
        boolean oneLaw = Contest.EXCHANGE_OPENS_AT == Contest.DECISIVE_EDGE && symmetric;
        System.out.println(String.format(Locale.ROOT,
                "BANDS LAW decisive_edge=%d exchange_opens_at=%d symmetric=%s VERDICT %s",
                Contest.DECISIVE_EDGE, Contest.EXCHANGE_OPENS_AT, symmetric,
                oneLaw ? "ONE_LAW" : "TWO_LAWS"));
        System.out.println("BANDS ANALYTIC " + shares(analytic)
                + " (uniform 1..10, banded by Contest.band)");
        double sameAxisFloor = (pool.size() / (double) STAT_VALUES - 1.0) / (pool.size() - 1.0);
        System.out.println(String.format(Locale.ROOT,
                "BANDS SAMEAXIS pairs=%d contests=%d TIE=%.2f%% expected=%.2f%% floor=%.2f%%",
                slots.size(), total(sameAxis), 100.0 * share(sameAxis, tie),
                100.0 * analytic[tie], 100.0 * sameAxisFloor));
        System.out.println(String.format(Locale.ROOT,
                "BANDS WORSTPAIR %s tie_delta=%+.3fpp of %d pairs at tol=%.3fpp",
                worstTiePair, 100.0 * worstTieDelta, pairs, 100.0 * tolerance));
        System.out.println(String.format(Locale.ROOT,
                "BANDS pairs=%d contests=%d %s analytic_TIE=%.2f%% delta=%+.3fpp tol=%.3fpp VERDICT %s",
                pairs, total(sweep), shares(sweep), 100.0 * analytic[tie], 100.0 * delta,
                100.0 * tolerance, agree ? "BANDS_AS_DERIVED" : "BANDS_DRIFTED"));
        return agree && oneLaw ? 0 : 1;
    }

    /** The five matchups {@link #cast()} stages, and the only cross-family pairs the record names. */
    private static List<Staged> staged() {
        return List.of(
                new Staged("rooftop", new Slot(Family.HUMAN, "evasion"),
                        new Slot(Family.PROGRAM, Family.HUNT_AXIS)),
                new Staged("the-room", new Slot(Family.SYSTEM, "authority"),
                        new Slot(Family.HUMAN, "will")),
                new Staged("the-overflow", new Slot(Family.PROGRAM, "replication"),
                        new Slot(Family.SYSTEM, "tolerance")),
                new Staged("mechanical-line", new Slot(Family.HUMAN, "evasion"),
                        new Slot(Family.MACHINE, "precision")),
                new Staged("interrogation", new Slot(Family.HUMAN, "will"),
                        new Slot(Family.PROGRAM, "purposeIntegrity")));
    }

    /**
     * Every ordered pair of distinct souls on one matchup, banded by the law
     * itself. {@link Contest#resolve} is called rather than the margin
     * arithmetic being repeated here: a probe that re-implements the thing it
     * measures reports on its own copy.
     */
    private static long[] tally(Map<Family, List<Sheet>> derived, Slot a, Slot b) {
        List<Sheet> left = derived.get(a.family());
        List<Sheet> right = derived.get(b.family());
        long[] counts = new long[Contest.Outcome.values().length];
        for (int i = 0; i < left.size(); i++) {
            Sheet one = left.get(i);
            for (int j = 0; j < right.size(); j++) {
                if (i != j) {
                    counts[Contest.resolve(one, a.axis(), right.get(j), b.axis()).ordinal()]++;
                }
            }
        }
        return counts;
    }

    /**
     * The band shares a uniform, independent 1..10 pair would produce, read
     * out of {@link Contest#band} rather than transcribed beside it. All one
     * hundred value pairs, each worth one percent.
     */
    private static double[] analytic() {
        double[] share = new double[Contest.Outcome.values().length];
        for (int v = 1; v <= STAT_VALUES; v++) {
            for (int w = 1; w <= STAT_VALUES; w++) {
                share[Contest.band(v - w).ordinal()] += 1.0 / (STAT_VALUES * (double) STAT_VALUES);
            }
        }
        return share;
    }

    /**
     * Three sigma on the TIE share of one matchup, derived from the pool size
     * rather than picked.
     *
     * <p>TIE's share is {@code sum_v p_v q_v} over the two axes' empirical
     * histograms. Writing {@code p = u + a} and {@code q = u + b} around the
     * uniform {@code u}, the cross terms {@code u.b} and {@code a.u} vanish
     * because each error vector sums to zero, so the share is
     * {@code 1/k + a.b} — no first-order term, which is why this band and no
     * other carries the verdict. Under the null that the mixer bands
     * uniformly at random, {@code a} and {@code b} are independent
     * multinomial errors with covariance {@code C}, and
     * {@code Var(a.b) = sum C_vw^2}.
     */
    private static double tieTolerance(int bins, int names) {
        double diagonal = (1.0 / bins - 1.0 / ((double) bins * bins)) / names;
        double offDiagonal = 1.0 / ((double) bins * bins * names);
        double variance = bins * diagonal * diagonal
                + bins * (bins - 1.0) * offDiagonal * offDiagonal;
        return 3.0 * Math.sqrt(variance);
    }

    private static long total(long[] counts) {
        long sum = 0;
        for (long count : counts) {
            sum += count;
        }
        return sum;
    }

    private static double share(long[] counts, int outcome) {
        return counts[outcome] / (double) total(counts);
    }

    /** The five band shares on one line, named, in the enum's own order. */
    private static String shares(long[] counts) {
        double[] fractions = new double[counts.length];
        long sum = total(counts);
        for (int i = 0; i < counts.length; i++) {
            fractions[i] = counts[i] / (double) sum;
        }
        return shares(fractions);
    }

    private static String shares(double[] fractions) {
        StringBuilder sb = new StringBuilder(96);
        for (Contest.Outcome outcome : Contest.Outcome.values()) {
            sb.append(sb.length() == 0 ? "" : " ").append(String.format(Locale.ROOT,
                    "%s=%.2f%%", outcome, 100.0 * fractions[outcome.ordinal()]));
        }
        return sb.toString();
    }

    private static Method open(String name, Class<?>... params) throws Exception {
        Method m = Sheets.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    /** Pearson correlation of two equal-length samples; 0 when either is constant. */
    private static double pearson(double[] a, double[] b) {
        int n = a.length;
        double sumA = 0, sumB = 0;
        for (int i = 0; i < n; i++) {
            sumA += a[i];
            sumB += b[i];
        }
        double meanA = sumA / n;
        double meanB = sumB / n;
        double cov = 0, varA = 0, varB = 0;
        for (int i = 0; i < n; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        return varA == 0 || varB == 0 ? 0.0 : cov / Math.sqrt(varA * varB);
    }

    private SheetBench() {}
}
