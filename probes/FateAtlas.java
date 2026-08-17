import matrix.Simulation;
import matrix.core.Config;
import matrix.realworld.AcceptanceLoop;
import matrix.realworld.Human;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Probe: who is even allowed to walk out?
 *
 * #200's verification round asked whether anyone could cross the Kid band
 * at all, wrote a sweeper to answer it, and let the sweeper die with its
 * scratchpad — the exact waste Ag9 names. Rebuilding it as a sampler would
 * rebuild the wrong instrument, because fate is not sampled: it is a pure,
 * DICELESS derivation (the rng-drawn variant flipped canonical seed 42 to
 * QUIET, so the open point (c) ruling took the die away), and a pure
 * function is ENUMERATED.
 *
 * <p>The enumeration's DOMAIN moved once, at #764, and this probe moved with
 * it. Fate used to key to the NAME, so the domain was the farm's catalogue —
 * {@code NamePool} first x family, 400 of them, no universe needed — and
 * this file walked exactly that product. Under the #373 ruling fate keys to
 * the BIRTH EVENT (seed, tick, rack unit, growth ordinal, name), which is
 * the Architect's #212 law applied to the mechanic that shipped before it. A
 * birth is not a catalogue entry: it happens in a universe or it does not
 * happen. So the domain is now every birth a span of universes actually
 * grows, and the enumerator boots them to read it.
 *
 * <p>What did NOT change is the method, and it is the part worth keeping:
 * every fate here comes from the daemon's own derivation, never a copy —
 * {@link AcceptanceLoop#threshold} directly and its package-private
 * {@code spikes} by reflection — so a change in either shows up as a changed
 * table rather than as agreement between two copies of the same arithmetic.
 *
 * <p>What it proved at {@code KID_BASE=144} while fate was name-keyed:
 * MONOCULTURE. Every name needed 6, 7 or 8 spikes, no name in the catalogue
 * ever landed more than 7 in 600 windows, and exactly one cleared its own
 * bar — {@code Otto Aydin}, threshold 161, window 474. The seed decided
 * WHETHER he was grown, never who may walk out. That is the reading this
 * probe was written to expose and #764 was written to retire.
 *
 * <p><b>600 is an argument, not a property</b> (#843), and re-keying the die
 * did not change that. {@code personalResidue} is monotone with no decay, no
 * reset and no cap, so a birth's spike count grows linearly in windows while
 * its bar stands still, and the admitted fraction climbs to 1 at any bar the
 * family can express. {@code --sweep} exists so the budget can never be
 * dropped: it reports admitted-per-budget and verdicts whether eligibility
 * is FLAT or DRIFTS. #843's PR and D-033's third errata both expected #764
 * to make that line read FLAT; it does not, and the errata's own last
 * paragraph says why — the knob the shape turns on is the accumulator's
 * slope against the run length, and re-keying the die moves WHO is admitted,
 * never the slope. The sweep still reads DRIFTS after this unit, measured
 * and not assumed, and #373 is owed that correction.
 *
 * <p>This is the instrument every future {@code KID_*} tuning quotes: before
 * and after, how many births the new bar admits, at which budget. #348 (the
 * Kid family absorbed as HUMAN's fourth axis) is a "byte for byte" claim
 * that this table can falsify.
 *
 * <pre>
 * java -cp out:probes/out FateAtlas [windows]            enumerate seeds 1..20 (600 = a 6,000-tick run)
 * java -cp out:probes/out FateAtlas --seeds from to [w]  enumerate a chosen span of universes
 * java -cp out:probes/out FateAtlas --sweep [w...]       admitted per budget, flat or drifting
 * </pre>
 *
 * The probe boots universes and ticks NOTHING: a birth is fixed at
 * construction, so the roster at tick 0 already carries every fate the
 * universe will ever hand out. Booting happens once per span and every
 * budget is read off the same births.
 *
 * <p>Every mode here is an enumeration, and an enumeration is a CEILING: it
 * grants each birth an unbroken BLUE link for the whole budget. A live link
 * is closed by death, by the hardline and by the walk-out itself, so the
 * births that actually cross in a run are a subset of the admitted. The
 * sweep answers what the bar permits, never what a universe spends.
 *
 * <p>The ceiling is loose in a second way the duration argument does not
 * cover, and it is stated here rather than quietly carried: the domain is
 * every birth, and 4 of the 196 links a universe boots are RED — The One and
 * the pirates — whom {@code AcceptanceLoop.accrue} refuses before it ever
 * touches the personal account. Over seeds 1..20 that is 80 births of 3,920.
 * At the canonical 600-window budget none of the 80 is admitted, so the
 * headline reading is unaffected; at longer budgets they are counted, 78 of
 * them by 6,000 windows and all 80 by 20,000, and the reported fraction is
 * that much too generous. Filtering on the tick-0 pill was refused because a
 * pill is run state and this table is a statement about births: a BLUE mind
 * can be handed a red pill later, and a RED one can be handed a blue one by
 * {@code Architect.reload}, so a pill read at tick 0 would bake a run-time
 * fact into a derivation-time enumeration. It needs a decision, not a filter.
 */
public final class FateAtlas {

    /** The default span: twenty universes, the same twenty every other sweep uses. */
    private static final long DEFAULT_FROM = 1, DEFAULT_TO = 20;

    /** One birth, as the derivation reads it: the key and the facts that name it. */
    private record Birth(long seed, String name, String rackUnit, long key) {}

    /** One birth's fate at one budget, decided at construction and never again. */
    private record Fate(long seed, String name, String rackUnit,
                        long threshold, int needs, int spikes, int window) {}

    /**
     * The budgets the sweep reports when none are named: the canonical run,
     * {@code ArcBeats}' own budget, and three multiples that carry the curve
     * out to saturation. Named here rather than baked into the loop so the
     * ruling can ask a different question by typing different numbers.
     */
    private static final int[] DEFAULT_BUDGETS = {600, 1200, 2400, 6000, 20000};

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--sweep")) {
            int[] budgets = DEFAULT_BUDGETS;
            if (args.length > 1) {
                budgets = new int[args.length - 1];
                for (int i = 1; i < args.length; i++) {
                    budgets[i - 1] = Integer.parseInt(args[i]);
                }
            }
            sweep(budgets, DEFAULT_FROM, DEFAULT_TO);
            return;
        }
        long from = DEFAULT_FROM, to = DEFAULT_TO;
        int windows = 600;
        if (args.length > 0 && args[0].equals("--seeds")) {
            from = args.length > 1 ? Probes.number(args[1], "from") : DEFAULT_FROM;
            to = args.length > 2 ? Probes.number(args[2], "to") : DEFAULT_TO;
            if (args.length > 3) {
                windows = Probes.count(args[3], "windows");
            }
        } else if (args.length > 0) {
            windows = Probes.count(args[0], "windows");
        }
        enumerate(windows, from, to);
    }

    /**
     * The eligible fraction as a function of the tick budget — #843's
     * measurement, as an instrument rather than a table someone typed.
     *
     * <p>The verdict is FLAT or DRIFTS, and flatness means the admitted
     * COUNT is the same integer at every budget. "More than one" is
     * deliberately not the test: widening {@code KID_JITTER} moves the count
     * at 600 windows without touching the slope, so a bar that admits forty
     * births at 600 and all of them at 6,000 would pass a greater-than-one
     * test while still being the mechanic #843 describes. Only equality
     * across budgets says eligibility stopped being a function of how long
     * anyone happened to run.
     *
     * <p>The refusals guard the FLAT line, because a false FLAT is worth
     * less than no line at all. Three inputs produce a flat verdict without
     * measuring anything, and all three are refused rather than answered: a
     * single budget (flat by arity), a repeated budget (flat by construction
     * — {@code --sweep 600 600} compares a column against itself), and a
     * budget below one window (the enumeration loop never runs, so every
     * birth reports admitted=0 and the strongest available verdict comes out
     * of nonsense). A refusal prints no verdict line at all for a caller's
     * {@code grep -qxF} to find.
     */
    private static void sweep(int[] budgets, long from, long to) throws Exception {
        System.out.println("SWEEP base=" + Config.KID_BASE
                + " jitter=" + Config.KID_JITTER
                + " spike=" + Config.KID_SPIKE
                + " denom=" + Config.KID_SPIKE_DENOM
                + " accrue=" + Config.ACCRUE_EVERY_TICKS
                + " seeds=" + from + ".." + to
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

        // One boot per span, not one per budget: a birth's key is fixed at
        // construction, so re-growing the same universes five times would
        // spend five times the wall clock to read the same longs back.
        List<Birth> births = births(from, to);
        int total = births.size(), lo = Integer.MAX_VALUE, hi = -1;
        for (int windows : budgets) {
            int admitted = 0, ceiling = 0;
            for (Fate f : fates(births, windows)) {
                if (f.window() > 0) {
                    admitted++;
                }
                ceiling = Math.max(ceiling, f.spikes());
            }
            lo = Math.min(lo, admitted);
            hi = Math.max(hi, admitted);
            System.out.println(String.format(Locale.ROOT,
                    "BUDGET windows=%d ticks=%d births=%d admitted=%d fraction=%.4f ceiling=%d",
                    windows, (long) windows * Config.ACCRUE_EVERY_TICKS,
                    total, admitted, (double) admitted / total, ceiling));
        }

        System.out.println("FATESWEEP budgets=" + budgets.length + " births=" + total
                + " admitted_min=" + lo + " admitted_max=" + hi);
        System.out.println(lo == hi
                ? "VERDICT ELIGIBILITY_FLAT admitted=" + lo + " of " + total
                : "VERDICT ELIGIBILITY_DRIFTS " + lo + ".." + hi + " of " + total);
    }

    private static void enumerate(int windows, long from, long to) throws Exception {
        List<Fate> fates = fates(births(from, to), windows);

        System.out.println("BAND base=" + Config.KID_BASE
                + " jitter=" + Config.KID_JITTER
                + " spike=" + Config.KID_SPIKE
                + " denom=" + Config.KID_SPIKE_DENOM
                + " windows=" + windows
                + " ticks=" + (long) windows * Config.ACCRUE_EVERY_TICKS
                + " seeds=" + from + ".." + to
                + " births=" + fates.size());

        // The bar, as a histogram: how many spikes a birth must land to cross.
        // Printed in ascending need, so the table diffs cleanly after a tuning.
        int minNeed = Integer.MAX_VALUE, maxNeed = 0, ceiling = 0;
        for (Fate f : fates) {
            minNeed = Math.min(minNeed, f.needs());
            maxNeed = Math.max(maxNeed, f.needs());
            ceiling = Math.max(ceiling, f.spikes());
        }
        for (int need = minNeed; need <= maxNeed; need++) {
            int births = 0, reached = 0;
            for (Fate f : fates) {
                if (f.needs() == need) {
                    births++;
                    if (f.spikes() >= need) {
                        reached++;
                    }
                }
            }
            System.out.println("NEED spikes=" + need + " births=" + births + " reached=" + reached);
        }

        int atCeiling = 0;
        for (Fate f : fates) {
            if (f.spikes() == ceiling) {
                atCeiling++;
            }
        }
        System.out.println("REACH ceiling=" + ceiling + " births=" + atCeiling);

        // The walkers, in seed then farm order: who this span of universes
        // would let out, and where each of them was born. The tick is the
        // EARLIEST one the crossing can happen on — window counts are the
        // link's own, so a link that spends time closed, dark or RED reaches
        // its window later than the wall clock suggests, and the film shows
        // the crossing at or after this tick, never before it.
        int admitted = 0;
        Map<String, Integer> byName = new LinkedHashMap<>();
        for (Fate f : fates) {
            if (f.window() > 0) {
                admitted++;
                byName.merge(f.name(), 1, Integer::sum);
                System.out.println("WALKER seed=" + f.seed()
                        + " name=\"" + f.name() + "\""
                        + " pod=" + f.rackUnit()
                        + " threshold=" + f.threshold()
                        + " needs=" + f.needs()
                        + " spikes=" + f.spikes()
                        + " window=" + f.window()
                        + " tick=" + (long) f.window() * Config.ACCRUE_EVERY_TICKS);
            }
        }

        // The CAST: how many DISTINCT minds, and whether one name owns them
        // all. Two births that share a string are two people (NameCensus: 196
        // humans wear 154 names at seed 42), so this counts names only to
        // catch a monoculture, never to identify anybody.
        String top = "-";
        int topCount = 0;
        for (Map.Entry<String, Integer> e : byName.entrySet()) {
            if (e.getValue() > topCount) {
                topCount = e.getValue();
                top = e.getKey();
            }
        }

        System.out.println("FATEATLAS windows=" + windows + " seeds=" + from + ".." + to
                + " births=" + fates.size() + " admitted=" + admitted
                + " distinct=" + byName.size() + " top=\"" + top + "\"x" + topCount
                + " ceiling=" + ceiling);
        // MONOCULTURE needs at least two crossings before it means anything.
        // Under the name domain admitted==1 WAS the monoculture: one name of
        // four hundred, in every universe forever. Under the birth domain the
        // same integer means one birth of thousands crossed, which is RARITY
        // and has one distinct name by arithmetic. Reading that as a
        // monoculture would fire the gate on the wrong fact and teach the next
        // tuner to widen the band to silence it.
        // Two of the three outcomes are failures: a band nothing can enter, and a
        // band only one name ever enters. The exit code has to say which, because
        // #1093's whole finding is that the verdict word alone reaches only the
        // bench — and BAND_SEALED read as success to every hand-run script.
        boolean open = admitted > 0 && !(admitted >= 2 && byName.size() == 1);
        Probes.leave(admitted == 0 ? "VERDICT BAND_SEALED"
                : admitted >= 2 && byName.size() == 1 ? "VERDICT MONOCULTURE"
                : "VERDICT BAND_OPEN admitted=" + admitted, open);
    }

    /**
     * Every birth the span grows, read off the roster at tick 0. Order is
     * seed then the farm's own growth order — a list, never a set, so the
     * table is byte-stable across runs and boxes.
     */
    private static List<Birth> births(long from, long to) throws Exception {
        List<Birth> out = new ArrayList<>();
        for (long seed = from; seed <= to; seed++) {
            Simulation sim = new Simulation(seed, null, null);
            for (var link : Probes.links(Probes.realWorld(sim))) {
                Human h = link.human;
                out.add(new Birth(seed, h.name, h.pod == null ? "-" : h.pod.rackUnit, h.birthKey));
            }
        }
        return out;
    }

    /**
     * The fate the derivation already decided for each birth at one budget:
     * the threshold, the spikes it must land, the spikes it does land inside
     * the budget, and the window it crosses (0 = never).
     */
    private static List<Fate> fates(List<Birth> births, int windows) throws Exception {
        Method spikes = AcceptanceLoop.class.getDeclaredMethod("spikes", long.class, long.class);
        spikes.setAccessible(true);
        List<Fate> out = new ArrayList<>();
        for (Birth b : births) {
            long threshold = AcceptanceLoop.threshold(b.key());
            long residue = 0;
            int landed = 0, crossed = 0;
            for (int w = 1; w <= windows; w++) {
                if ((boolean) spikes.invoke(null, b.key(), (long) w)) {
                    residue += Config.KID_SPIKE;
                    landed++;
                    if (crossed == 0 && residue >= threshold) {
                        crossed = w;
                    }
                }
            }
            int needs = (int) ((threshold + Config.KID_SPIKE - 1) / Config.KID_SPIKE);
            out.add(new Fate(b.seed(), b.name(), b.rackUnit(), threshold, needs, landed, crossed));
        }
        return out;
    }

    private FateAtlas() {}
}
