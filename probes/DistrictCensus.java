import matrix.Simulation;
import matrix.core.District;
import matrix.core.NamePool;
import matrix.core.PlaceGraph;
import matrix.core.Position;
import matrix.realworld.RealWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Probe: the census counts districts like anyone else.
 *
 * D-048's naming law (#289, #530) says our quarters wear our citizens'
 * names, out of the citizens' own pools. That has a consequence nobody
 * should have to discover from a log line at three in the morning: a
 * district and a human can be namesakes, and every name-keyed feature —
 * {@code --follow}, log forensics, the dream reader's WHERE — must know
 * it. {@link NameCensus} answers the question for the grown and never
 * looks at the map; {@link DistrictNeutral} reads the map and never looks
 * at the people. This is the join: the two rosters counted against each
 * other, in one grammar.
 *
 * <p><b>What it can refuse.</b> Two quarters wearing one name is a FAULT —
 * every instrument line naming a district would be ambiguous. A name off
 * the pools is a FAULT on either bank: #842 put the forty names in one
 * home so the two banks cannot disagree, and this is the check that they
 * still both read it rather than an appeal to how the code is currently
 * written. A pool entry containing a space is a FAULT because this probe
 * splits a name on its first space, and a two-word pool entry would make
 * every SURNAME line quietly wrong instead of loudly absent.
 *
 * <p><b>What it cannot refuse, said out loud.</b> It does not know what
 * the six quarters are supposed to be called. Change {@code SALT_FIRST}
 * in {@link District} and four quarters become different people; every
 * line below changes and the verdict stays green, because a renamed city
 * is still a city whose quarters are distinct and drawn from the pools.
 * That is #944's subject and #944's pin, in #944's probe. This one counts;
 * it does not remember.
 *
 * <p>The namesake count is a property of the SEED, not of the map — seed
 * 42 has three, seed 7 has four — which is why the CENSUS line says
 * {@code seed=} before it says anything else.
 *
 * <p><b>{@code --ceiling}</b> is the other mode, and it needs no universe:
 * it walks {@link District#catalogOf} up to the name grid's edge and one
 * zone past it. The city has six quarters and the grid holds four hundred
 * names, so the de-collision loop is dead code in every run this repository
 * has ever taken — and dead code is exactly where a hang lives unnoticed
 * (#1015). This mode is the only thing that enters it.
 *
 * Usage: java -cp out:probes/out DistrictCensus [seed]
 *        java -cp out:probes/out DistrictCensus --ceiling
 */
public final class DistrictCensus {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--ceiling")) {
            ceiling();
            return;
        }
        long seed = args.length > 0 ? Probes.number(args[0], "seed") : 42;
        Simulation sim = new Simulation(seed, null, null);
        RealWorld rw = Probes.realWorld(sim);
        List<District> districts = Probes.world(sim).places().districts();
        List<String> faults = new ArrayList<>();

        // The city, counted like a population: quarters may not be namesakes
        // of each other (the catalog de-collides), but they may share a name
        // with a citizen, and that is the law working rather than failing.
        Map<String, Integer> people = new LinkedHashMap<>();
        for (var h : rw.humans()) {
            people.merge(h.name, 1, Integer::sum);
        }
        Map<String, Integer> quarters = new LinkedHashMap<>();
        for (District d : districts) {
            quarters.merge(d.name(), 1, Integer::sum);
            System.out.println("DISTRICT " + d.name() + " · " + d.zoneName());
        }

        for (var e : new TreeMap<>(quarters).entrySet()) {
            if (e.getValue() > 1) {
                System.out.println("DUP district " + e.getKey() + " x" + e.getValue());
                faults.add("two quarters wear the name " + e.getKey());
            }
        }
        int namesakes = 0;
        for (var e : new TreeMap<>(quarters).entrySet()) {
            Integer also = people.get(e.getKey());
            if (also != null) {
                namesakes++;
                System.out.println("DUP namesake " + e.getKey()
                        + " — a quarter and " + also + " citizen(s)");
            }
        }
        // Surnames are the interesting axis: the pools are small, so the
        // city and its people share family names long before they share
        // whole ones. A reader who greps a surname gets both, by design.
        Map<String, Integer> surnames = new TreeMap<>();
        for (District d : districts) {
            surnames.merge(surname(d.name()), 1, Integer::sum);
        }
        int sharedSurnames = 0;
        for (var e : surnames.entrySet()) {
            int amongPeople = 0;
            for (var p : people.entrySet()) {
                if (surname(p.getKey()).equals(e.getKey())) {
                    amongPeople += p.getValue();
                }
            }
            if (amongPeople > 0) {
                sharedSurnames++;
            }
            System.out.println("SURNAME " + e.getKey() + " quarters=" + e.getValue()
                    + " citizens=" + amongPeople);
        }

        poolShape(faults);
        offPool("quarters", quarters.keySet(), faults);
        offPool("citizen_names", people.keySet(), faults);

        System.out.println("CENSUS seed=" + seed + " humans=" + rw.humans().size()
                + " distinct=" + people.size() + " districts=" + districts.size()
                + " namesakes=" + namesakes + " shared_surnames=" + sharedSurnames);
        for (String fault : faults) {
            System.out.println("FAULT " + fault);
        }
        if (faults.isEmpty()) {
            System.out.println("VERDICT CITY_CENSUSED");
            return;
        }
        // ONE HOME FOR THE FAILING EXIT (#1218). `Probes.leave`'s javadoc has said
        // since #1093 that it exists so the contract has one place to be read and one
        // place to change — and this leg printed and exited by hand, which made that
        // sentence false in twelve files. Nothing here was LYING: the code agrees with
        // the verdict, which is why LeaveContract counts these rather than failing on
        // them. What it cost is #1204: the three-valued exit became an enum and the
        // change reached only the probes already using the helper.
        Probes.leave("VERDICT CITY_MISCOUNTED faults=" + faults.size(), Probes.Outcome.BROKE);
    }

    /**
     * The naming ceiling, reached from both sides.
     *
     * <p>Zone names are synthetic and distinct, so the mix that keys a
     * quarter's name is a different read for every one of them: this is the
     * catalog's own de-collision under the only pressure that can reach it.
     * At the grid's edge every zone must still get a name of its own — 400
     * zones, 400 distinct names — and one zone past it the pigeonhole is
     * arithmetic, so the only two honest answers are a refusal and a hang.
     * The refusal is judged by its sentence, quoted whole, because a
     * refusal that names neither number is the thing an operator gets
     * instead of a stack trace and it has to say what went wrong.
     */
    private static void ceiling() {
        int grid = NamePool.firstNames().size() * NamePool.familyNames().size();
        List<String> faults = new ArrayList<>();

        List<District> full = District.catalogOf(zones(grid));
        Map<String, Integer> distinct = new TreeMap<>();
        for (District d : full) {
            distinct.merge(d.name(), 1, Integer::sum);
        }
        System.out.println("CEILING grid=" + grid + " zones=" + grid
                + " named=" + distinct.size());
        if (distinct.size() != grid) {
            faults.add("the grid seats " + grid + " zones under only "
                    + distinct.size() + " distinct names");
        }

        String refusal = null;
        try {
            District.catalogOf(zones(grid + 1));
        } catch (IllegalArgumentException e) {
            refusal = e.getMessage();
        }
        if (refusal == null) {
            System.out.println("CEILING grid=" + grid + " zones=" + (grid + 1) + " named=?");
            faults.add((grid + 1) + " zones were catalogued out of a " + grid + "-name grid");
        } else {
            System.out.println("CEILING grid=" + grid + " zones=" + (grid + 1)
                    + " refused=\"" + refusal + "\"");
        }

        for (String fault : faults) {
            System.out.println("FAULT " + fault);
        }
        if (faults.isEmpty()) {
            System.out.println("VERDICT CATALOG_REFUSES_OVERFLOW");
            return;
        }
        Probes.leave("VERDICT CATALOG_UNBOUNDED faults=" + faults.size(), Probes.Outcome.BROKE);
    }

    /** N zones with distinct names and no map behind them — the catalog reads the name and nothing else. */
    private static List<PlaceGraph.Zone> zones(int count) {
        List<PlaceGraph.Zone> zones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            zones.add(new PlaceGraph.Zone("ceiling zone " + i, new Position(i, i)));
        }
        return zones;
    }

    /**
     * The pools' own shape, checked before anything is derived from it.
     * Every SURNAME line above is a first-space split, and a pool entry
     * with a space in it makes that split produce a wrong answer silently
     * — the worst kind, because the census keeps printing numbers.
     */
    private static void poolShape(List<String> faults) {
        for (String pool : new String[]{"FIRST", "LAST"}) {
            List<String> names = pool.equals("FIRST")
                    ? NamePool.firstNames() : NamePool.familyNames();
            int spaced = 0;
            for (String name : names) {
                if (name.indexOf(' ') >= 0) {
                    spaced++;
                }
            }
            System.out.println("POOL " + pool + " size=" + names.size() + " spaced=" + spaced);
            if (spaced > 0) {
                faults.add("pool " + pool + " holds " + spaced
                        + " entry(s) with a space; the surname split is guesswork");
            }
        }
    }

    /**
     * One bank's names against the one pool both banks index (#842). The
     * duplication that used to need a referee is gone — {@code NamePool}
     * is the single home — so what is checked here is not that two copies
     * agree, but that each bank is still drawing from the home at all. A
     * name assembled anywhere else is off-pool, and the naming law is what
     * it breaks: our quarters wear our citizens' names, out of the same
     * pools, or the sentence is decoration.
     */
    private static void offPool(String bank, Iterable<String> names, List<String> faults) {
        List<String> first = NamePool.firstNames();
        List<String> family = NamePool.familyNames();
        int counted = 0;
        List<String> off = new ArrayList<>();
        for (String name : names) {
            counted++;
            int at = name.indexOf(' ');
            String given = at < 0 ? name : name.substring(0, at);
            if (at < 0 || !first.contains(given) || !family.contains(surname(name))) {
                off.add(name);
            }
        }
        System.out.println("POOL " + bank + "=" + counted + " off_pool=" + off.size());
        for (String name : off) {
            System.out.println("OFFPOOL " + bank + " " + name);
        }
        if (!off.isEmpty()) {
            faults.add(bank + ": " + off.size() + " name(s) not drawn from NamePool (#842)");
        }
    }

    /** Everything after the first space — sound only while the pools hold single-word names, which is checked above. */
    private static String surname(String name) {
        int at = name.indexOf(' ');
        return at < 0 ? name : name.substring(at + 1);
    }

    private DistrictCensus() {}
}
