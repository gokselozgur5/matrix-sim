import matrix.character.Contest;
import matrix.character.Family;
import matrix.character.Sheet;
import matrix.character.Sheets;
import matrix.core.NamePool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        String mode = args.length > 0 ? args[0] : "";
        switch (mode) {
            case "--vocab" -> vocab();
            case "--hunt-axis" -> huntAxis();
            case "--discipline" -> discipline();
            case "--avalanche" -> System.exit(avalanche());
            case "" -> cast();
            default -> {
                System.err.println("unknown mode: " + mode
                        + " (try --vocab, --hunt-axis, --discipline, --avalanche)");
                System.exit(2);
            }
        }
    }

    /** The exhibit the gate argued over: ten sheets, five scenes, one count line. */
    private static void cast() {
        Sheet trinity = Sheets.derive("Trinity", Family.HUMAN);
        Sheet morpheus = Sheets.derive("Morpheus", Family.HUMAN);
        Sheet niobe = Sheets.derive("Niobe", Family.HUMAN);
        Sheet cypher = Sheets.derive("Cypher", Family.HUMAN);
        Sheet thomas = Sheets.derive("Thomas A. Anderson", Family.HUMAN);
        Sheet architect = Sheets.derive("the Architect", Family.SYSTEM);
        Sheet oracle = Sheets.derive("the Oracle", Family.PROGRAM);
        Sheet otto = Sheets.derive("Otto Aydin", Family.MACHINE);
        Sheet smith = Sheets.derive("Agent Smith", Family.PROGRAM);
        Sheet jones = Sheets.derive("Agent Jones", Family.PROGRAM);

        List<Sheet> cast = List.of(trinity, morpheus, niobe, cypher, thomas,
                architect, oracle, otto, smith, jones);
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
        return avalancheOk && corrOk ? 0 : 1;
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
