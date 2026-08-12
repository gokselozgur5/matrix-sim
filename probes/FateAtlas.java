import matrix.Simulation;
import matrix.core.Config;
import matrix.core.NamePool;
import matrix.realworld.AcceptanceLoop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Probe: who is even allowed to walk out?
 *
 * #200's verification round asked whether anyone could cross the Kid band
 * at all, wrote a sweeper to answer it, and let the sweeper die with its
 * scratchpad — the exact waste Ag9 names. Rebuilding it as a sampler would
 * rebuild the wrong instrument, because the mechanic it measured no longer
 * exists: main ships a DICELESS derivation (the rng-drawn variant flipped
 * canonical seed 42 to QUIET, so the open point (c) ruling made fate a pure
 * function of the NAME) and {@code KID_BASE=144}, not the 120 and the
 * chance draw #200's PR body describes. Fate was always in the name, and a
 * pure function is not sampled. It is ENUMERATED.
 *
 * <p>So this is the enumerator. It walks every name the farm can grow —
 * {@code NamePool} first x family, 400 of them — against that name's own
 * threshold over a run's accrual windows, using the daemon's own
 * derivation (never a copy: {@link AcceptanceLoop#threshold} directly and
 * its package-private {@code spikes} by reflection, so a change in either
 * shows up here as a changed table rather than as agreement between two
 * copies of the same arithmetic).
 *
 * <p>What it proves at {@code KID_BASE=144 · KID_JITTER=48 · KID_SPIKE=24 ·
 * KID_SPIKE_DENOM=512} over 600 windows: the band is a MONOCULTURE. Every
 * name needs 6, 7 or 8 spikes; no name in the catalogue ever lands more
 * than 7 in 600 windows; exactly one name clears its own bar. The seed
 * decides WHETHER that person is grown, never WHO may walk out — which is
 * why the seed sweep here is a boot census (who does this universe grow?)
 * and not a tick budget.
 *
 * <p><b>But 600 is an argument, not a property</b> (#843). The single
 * admitted name is the answer at one budget, and the enumerator answers a
 * different question at every other: {@code personalResidue} is monotone
 * with no decay, no reset and no cap, so a name's spike count grows
 * linearly in windows while its bar stands still. Quoting {@code admitted}
 * without the {@code windows} that produced it states a property of the
 * mechanic that the mechanic does not have. {@code --sweep} exists so the
 * budget can never be dropped: it reports admitted-per-budget across a list
 * of budgets and verdicts whether eligibility is FLAT or DRIFTS. A mechanic
 * whose fate keys to something other than elapsed windows (#764) makes that
 * line read FLAT; the shipped one drifts 1 -> 400.
 *
 * <p>This is the instrument every future {@code KID_*} tuning quotes:
 * before and after, how many names the new bar admits. #348 (the Kid family
 * absorbed as HUMAN's fourth axis) is a "byte for byte" claim that this
 * table can falsify.
 *
 * <pre>
 * java -cp out:probes/out FateAtlas [windows]          enumerate (600 = a 6,000-tick run)
 * java -cp out:probes/out FateAtlas --seeds from to    which universes grow an admitted name
 * java -cp out:probes/out FateAtlas --sweep [w...]     admitted per budget, flat or drifting
 * </pre>
 *
 * The probe reads no world in its default form: no universe, no seed, no
 * draw — the derivation is the subject. The seed census boots universes
 * and reads their farms, and ticks nothing.
 *
 * <p>Every mode here is an enumeration, and an enumeration is a CEILING:
 * it grants each name an unbroken BLUE link for the whole budget. A live
 * link is closed by death, by the hardline and by the walk-out itself, so
 * the names that actually cross in a run are a subset of the admitted —
 * measured at 88 of the 374 admitted, seed 1 over 60,000 ticks. The sweep
 * answers what the bar permits, never what a universe spends.
 */
public final class FateAtlas {

    /** One name's fate, decided before the universe starts. */
    private record Fate(String name, long threshold, int needs, int spikes, int window) {}

    /**
     * The budgets the sweep reports when none are named: the canonical run,
     * {@code ArcBeats}' own budget, and three multiples that carry the curve
     * out to saturation. Named here rather than baked into the loop so the
     * ruling can ask a different question by typing different numbers.
     */
    private static final int[] DEFAULT_BUDGETS = {600, 1200, 2400, 6000, 20000};

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--seeds")) {
            long from = args.length > 1 ? Long.parseLong(args[1]) : 1;
            long to = args.length > 2 ? Long.parseLong(args[2]) : 20;
            seedCensus(from, to);
            return;
        }
        if (args.length > 0 && args[0].equals("--sweep")) {
            int[] budgets = DEFAULT_BUDGETS;
            if (args.length > 1) {
                budgets = new int[args.length - 1];
                for (int i = 1; i < args.length; i++) {
                    budgets[i - 1] = Integer.parseInt(args[i]);
                }
            }
            sweep(budgets);
            return;
        }
        int windows = args.length > 0 ? Integer.parseInt(args[0]) : 600;
        enumerate(windows);
    }

    /**
     * The eligible fraction as a function of the tick budget — #843's
     * measurement, as an instrument rather than a table someone typed.
     *
     * <p>The verdict is FLAT or DRIFTS, and flatness means the admitted
     * COUNT is the same integer at every budget. "More than one name" is
     * deliberately not the test: widening {@code KID_JITTER} moves the
     * count at 600 windows without touching the slope, so a bar that admits
     * forty names at 600 and four hundred at 6,000 would pass a
     * greater-than-one test while still being the mechanic #843 describes.
     * Only equality across budgets says eligibility stopped being a
     * function of how long anyone happened to run.
     *
     * <p>The refusals guard the FLAT line, because FLAT is the line #764
     * will grep for and a false one is worth more than no line at all.
     * Three inputs produce a flat verdict without measuring anything, and
     * all three are refused rather than answered: a single budget (flat by
     * arity), a repeated budget (flat by construction — {@code --sweep 600
     * 600} compares a column against itself), and a budget below one window
     * (the enumeration loop never runs, so every name reports admitted=0
     * and the strongest available verdict comes out of nonsense). Counting
     * arguments was not enough; the values have to be distinct and real.
     * A refusal prints no verdict line at all for a caller's
     * {@code grep -qxF} to find.
     */
    private static void sweep(int[] budgets) throws Exception {
        System.out.println("SWEEP base=" + Config.KID_BASE
                + " jitter=" + Config.KID_JITTER
                + " spike=" + Config.KID_SPIKE
                + " denom=" + Config.KID_SPIKE_DENOM
                + " accrue=" + Config.ACCRUE_EVERY_TICKS
                + " budgets=" + budgets.length);

        List<Integer> distinct = new ArrayList<>();
        for (int windows : budgets) {
            if (windows < 1) {
                System.out.println("FATESWEEP REFUSED windows=" + windows
                        + " — a budget below one window enumerates nothing");
                return;
            }
            if (!distinct.contains(windows)) {
                distinct.add(windows);
            }
        }
        if (distinct.size() < 2) {
            System.out.println("FATESWEEP REFUSED distinct=" + distinct.size()
                    + " — a flatness claim needs at least two distinct budgets");
            return;
        }

        int names = 0, lo = Integer.MAX_VALUE, hi = -1;
        for (int windows : budgets) {
            List<Fate> fates = fates(windows);
            names = fates.size();
            int admitted = 0, ceiling = 0;
            for (Fate f : fates) {
                if (f.window() > 0) {
                    admitted++;
                }
                ceiling = Math.max(ceiling, f.spikes());
            }
            lo = Math.min(lo, admitted);
            hi = Math.max(hi, admitted);
            System.out.println(String.format(Locale.ROOT,
                    "BUDGET windows=%d ticks=%d names=%d admitted=%d fraction=%.4f ceiling=%d",
                    windows, (long) windows * Config.ACCRUE_EVERY_TICKS,
                    names, admitted, (double) admitted / names, ceiling));
        }

        System.out.println("FATESWEEP budgets=" + budgets.length + " names=" + names
                + " admitted_min=" + lo + " admitted_max=" + hi);
        System.out.println(lo == hi
                ? "VERDICT ELIGIBILITY_FLAT admitted=" + lo + " of " + names
                : "VERDICT ELIGIBILITY_DRIFTS " + lo + ".." + hi + " of " + names);
    }

    private static void enumerate(int windows) throws Exception {
        List<Fate> fates = fates(windows);

        System.out.println("BAND base=" + Config.KID_BASE
                + " jitter=" + Config.KID_JITTER
                + " spike=" + Config.KID_SPIKE
                + " denom=" + Config.KID_SPIKE_DENOM
                + " windows=" + windows
                + " ticks=" + (long) windows * Config.ACCRUE_EVERY_TICKS
                + " names=" + fates.size());

        // The bar, as a histogram: how many spikes a name must land to cross.
        // Printed in ascending need, so the table diffs cleanly after a tuning.
        int minNeed = Integer.MAX_VALUE, maxNeed = 0, ceiling = 0;
        for (Fate f : fates) {
            minNeed = Math.min(minNeed, f.needs());
            maxNeed = Math.max(maxNeed, f.needs());
            ceiling = Math.max(ceiling, f.spikes());
        }
        for (int need = minNeed; need <= maxNeed; need++) {
            int names = 0, reached = 0;
            for (Fate f : fates) {
                if (f.needs() == need) {
                    names++;
                    if (f.spikes() >= need) {
                        reached++;
                    }
                }
            }
            System.out.println("NEED spikes=" + need + " names=" + names + " reached=" + reached);
        }

        int atCeiling = 0;
        for (Fate f : fates) {
            if (f.spikes() == ceiling) {
                atCeiling++;
            }
        }
        System.out.println("REACH ceiling=" + ceiling + " names=" + atCeiling);

        int admitted = 0;
        for (Fate f : fates) {
            if (f.window() > 0) {
                admitted++;
                System.out.println("WALKER name=\"" + f.name() + "\""
                        + " threshold=" + f.threshold()
                        + " needs=" + f.needs()
                        + " spikes=" + f.spikes()
                        + " window=" + f.window()
                        + " tick=" + (long) f.window() * Config.ACCRUE_EVERY_TICKS);
            }
        }
        System.out.println("FATEATLAS windows=" + windows + " names=" + fates.size()
                + " admitted=" + admitted + " ceiling=" + ceiling);
        System.out.println(admitted == 0 ? "VERDICT BAND_SEALED"
                : admitted == 1 ? "VERDICT MONOCULTURE"
                : "VERDICT BAND_OPEN admitted=" + admitted);
    }

    /**
     * Which universes grow a name the band admits. The derivation is
     * diceless, so the seed's only say is the roster: one boot per seed,
     * no ticks, the farm read as it was grown. A grown name is necessary,
     * never sufficient — the link must also stay blue and open long enough
     * to reach its window, which is a run, not a roster.
     */
    private static void seedCensus(long from, long to) throws Exception {
        List<Fate> fates = fates(600);
        List<String> admitted = new ArrayList<>();
        for (Fate f : fates) {
            if (f.window() > 0) {
                admitted.add(f.name());
            }
        }
        int universes = 0, carriers = 0;
        for (long seed = from; seed <= to; seed++) {
            universes++;
            Simulation sim = new Simulation(seed, null, null);
            List<String> grown = new ArrayList<>();
            for (var link : Probes.links(Probes.realWorld(sim))) {
                if (admitted.contains(link.human.name) && !grown.contains(link.human.name)) {
                    grown.add(link.human.name);
                }
            }
            if (!grown.isEmpty()) {
                carriers++;
                for (String name : grown) {
                    System.out.println("CARRIER seed=" + seed + " name=\"" + name + "\"");
                }
            }
        }
        System.out.println("FATECENSUS seeds=" + from + ".." + to
                + " universes=" + universes
                + " admitted_names=" + admitted.size()
                + " carriers=" + carriers);
    }

    /**
     * Every name the farm can grow, with the fate the derivation already
     * decided for it: the threshold, the spikes it must land, the spikes it
     * does land inside the budget, and the window it crosses (0 = never).
     * Order is first x family as {@link NamePool} declares them — a list,
     * never a set, so the table is byte-stable across runs and boxes.
     */
    private static List<Fate> fates(int windows) throws Exception {
        Method spikes = AcceptanceLoop.class.getDeclaredMethod("spikes", String.class, long.class);
        spikes.setAccessible(true);
        List<Fate> out = new ArrayList<>();
        for (String first : NamePool.firstNames()) {
            for (String last : NamePool.familyNames()) {
                String name = first + " " + last;
                long threshold = AcceptanceLoop.threshold(name);
                long residue = 0;
                int landed = 0, crossed = 0;
                for (int w = 1; w <= windows; w++) {
                    if ((boolean) spikes.invoke(null, name, (long) w)) {
                        residue += Config.KID_SPIKE;
                        landed++;
                        if (crossed == 0 && residue >= threshold) {
                            crossed = w;
                        }
                    }
                }
                int needs = (int) ((threshold + Config.KID_SPIKE - 1) / Config.KID_SPIKE);
                out.add(new Fate(name, threshold, needs, landed, crossed));
            }
        }
        return out;
    }

    private FateAtlas() {}
}
