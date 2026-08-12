package matrix.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A quarter of the city, as a catalog row (D-048). A district is a NAMED
 * BINDING over one PlaceGraph zone: district index IS zone index IS region
 * index, because the partition already exists and is immutable after boot
 * (D-024). The identity costs no machinery — the city already had zones and
 * the attention ledger already had regions; this is the row that says what
 * they MEAN.
 *
 * <p>Identity is DATA first, per the gate's leaning. Nothing in this file
 * moves an entity, draws a die or reads a clock: the catalog is a pure
 * function of the zone list, built once at boot and read afterwards.
 * Anything that lets a district touch mechanics is a declared move, made at
 * the site that moves — never here.
 *
 * <p>Dev8's naming law names it: our quarters wear the names our citizens
 * wear, out of the same pools, mixed by a zone-keyed function of our own —
 * never MxO's borrowed map, and never a die roll.
 */
public record District(int index, String zoneName, String name) {

    /**
     * The citizens' first names — PodFarm's own metal, and the whole point
     * of Dev8's law: a quarter of this city is named from the pool its
     * people are named from, so the census can count a district like
     * anyone else and a stranger can tell at a glance that this map was
     * not borrowed.
     *
     * <p>Copied, not moved. The farm still grows every Thomas from its own
     * array and not one citizen name changed hands — `realworld/` belongs
     * to another crew this phase, so lifting the pool out of `PodFarm`
     * into one shared home is filed (#842), not smuggled. Until that
     * lands, two copies are a drift risk with no guard, so
     * `DistrictCensus` (#538) reads the farm's arrays reflectively and
     * fails loudly the day the two disagree.
     */
    static final String[] FIRST = {
            "Thomas", "Trin", "Milo", "Dana", "Ezra", "Vera", "Otto", "Nadia",
            "Silas", "June", "Marcus", "Lena", "Hugo", "Iris", "Felix", "Mara",
            "Dario", "Selma", "Ivan", "Noor"};

    /** The citizens' family names — the same lift, under the same debt and the same guard. */
    static final String[] LAST = {
            "Anderson", "Vance", "Okafor", "Lindqvist", "Marek", "Osei", "Petrov",
            "Sato", "Weaver", "Kaya", "Moreau", "Iglesias", "Novak", "Reyes",
            "Berg", "Duran", "Kovacs", "Aydin", "Frost", "Adeyemi"};

    /** Salts: one per column, so a zone's first name and its family name are independent reads of one key. */
    private static final int SALT_FIRST = 1;
    private static final int SALT_LAST = 2;
    /** De-collision step: a taken name re-mixes with a shifted salt — deterministic, bounded, zero draws. */
    private static final int SALT_RETRY = 64;

    /**
     * The catalog, in zone order — the whole city, built once from the zone
     * list it binds to. The same six zones give the same six districts on
     * every machine and in every universe, seed included, because no seed
     * is ever consulted.
     */
    public static List<District> catalogOf(List<PlaceGraph.Zone> zones) {
        List<District> catalog = new ArrayList<>(zones.size());
        List<String> taken = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) {
            String zone = zones.get(i).name();
            String name = nameFor(zone, taken);
            taken.add(name);
            catalog.add(new District(i, zone, name));
        }
        return List.copyOf(catalog);
    }

    /**
     * One quarter's name, keyed by the zone it binds: a first name and a
     * family name out of the citizens' pools. Namesakes BETWEEN a district
     * and a citizen are expected and are the census's business — that is
     * the law working, not failing — but two quarters may not wear one
     * name, or every instrument line naming a district would be ambiguous.
     * A taken name re-mixes with a shifted salt until the city is
     * unambiguous; the loop terminates because each shift is a fresh read
     * of a 400-name grid and only six seats are ever filled.
     */
    private static String nameFor(String zone, List<String> taken) {
        for (int retry = 0; ; retry++) {
            int shift = retry * SALT_RETRY;
            String name = FIRST[Math.floorMod(mix(zone, SALT_FIRST + shift), FIRST.length)]
                    + " " + LAST[Math.floorMod(mix(zone, SALT_LAST + shift), LAST.length)];
            if (!taken.contains(name)) {
                return name;
            }
        }
    }

    /**
     * The house mixer: FNV-1a over the key's chars, the salt folded in by
     * the golden ratio, then murmur3's finalizer for avalanche — the
     * AcceptanceLoop precedent (#96 open point c), where an affine mix
     * turned out to be a bijection mod 2^k and killed the very tail it was
     * meant to grow. Every step is arithmetic this repository owns: no
     * {@code hashCode}, no map iteration order, nothing JVM-shaped (the
     * #212 hygiene ruling). Wraps by JLS int law, so the city is the same
     * city on every machine.
     */
    private static int mix(String key, int salt) {
        int h = 0x811C9DC5;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x01000193;
        }
        h ^= (int) (salt * 0x9E3779B9L);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }
}
