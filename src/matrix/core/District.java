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
 * never MxO's borrowed map, and never a die roll. The same pools literally:
 * {@link NamePool}, the one home both banks index (#842). The catalog holds
 * no copy to keep in step.
 */
public record District(int index, String zoneName, String name,
        int density, int wealth, int glitch) {

    /**
     * The character axes are PERMILLE, and this is the declared range every
     * column lives in — bounds included, stated before anything reads them.
     * Permille rather than a fraction because the domain does integer
     * arithmetic (D-004's fixed-point law): a district's character must
     * never be a double that rounds differently on someone else's machine.
     *
     * <p>What the columns mean, so a later reader does not invent it:
     * <b>density</b> is how crowded the quarter is, <b>wealth</b> is what
     * it costs to stand there, <b>glitch</b> is how badly the render
     * behaves — the susceptibility a glitch pocket (#296) will read when
     * pockets start choosing where to be born. Today all three are lore:
     * nothing mechanical consults them, and the unit that first does will
     * be a declared move.
     */
    public static final int AXIS_MIN = 0;
    public static final int AXIS_MAX = 1_000;

    /** Salts: one per column, so a zone's first name and its family name are independent reads of one key. */
    private static final int SALT_FIRST = 1;
    private static final int SALT_LAST = 2;
    private static final int SALT_DENSITY = 11;
    private static final int SALT_WEALTH = 12;
    private static final int SALT_GLITCH = 13;

    /** A row outside its own declared range is a broken catalog, and it says so at boot or never. */
    public District {
        requireAxis(density, "density", name);
        requireAxis(wealth, "wealth", name);
        requireAxis(glitch, "glitch", name);
    }

    private static void requireAxis(int value, String column, String name) {
        if (value < AXIS_MIN || value > AXIS_MAX) {
            throw new IllegalArgumentException("district " + name + ": " + column + "=" + value
                    + " outside the declared range " + AXIS_MIN + ".." + AXIS_MAX);
        }
    }

    /**
     * The catalog, in zone order — the whole city, built once from the zone
     * list it binds to. The same six zones give the same six districts on
     * every machine and in every universe, seed included, because no seed
     * is ever consulted.
     *
     * <p>It refuses a zone list it cannot name, and it refuses it before it
     * names anything (#1015). Two quarters may not wear one name, so the
     * grid — every first name against every family name — is a hard ceiling
     * on how many quarters this city can have, and a caller one zone past it
     * is not asking for a slow catalog but for an impossible one. Before this
     * refusal existed the de-collision loop simply never returned, and
     * {@code PlaceGraph} builds the catalog in its constructor, so the 401st
     * zone was a boot hang with no message at all. {@code Config.scaleRefusal}
     * is the shape borrowed: a sentence naming both numbers, in finite time.
     *
     * @throws IllegalArgumentException when there are more zones than names
     */
    public static List<District> catalogOf(List<PlaceGraph.Zone> zones) {
        int grid = NamePool.firstNames().size() * NamePool.familyNames().size();
        if (zones.size() > grid) {
            throw new IllegalArgumentException("the name grid holds " + grid
                    + " names; " + zones.size() + " zones cannot all be named");
        }
        List<District> catalog = new ArrayList<>(zones.size());
        List<String> taken = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) {
            String zone = zones.get(i).name();
            String name = nameFor(zone, taken);
            taken.add(name);
            catalog.add(new District(i, zone, name,
                    axis(zone, SALT_DENSITY), axis(zone, SALT_WEALTH), axis(zone, SALT_GLITCH)));
        }
        return List.copyOf(catalog);
    }

    /**
     * One column's value for one zone, inside the declared range and
     * inclusive of both bounds. Same key as the name, a different salt:
     * the character of a quarter and the name of a quarter are independent
     * reads of the same word, which is why the mixer avalanches.
     */
    private static int axis(String zone, int salt) {
        return AXIS_MIN + Math.floorMod(mix(zone, salt), AXIS_MAX - AXIS_MIN + 1);
    }

    /**
     * One quarter's name, keyed by the zone it binds: a first name and a
     * family name out of the citizens' pools. Namesakes BETWEEN a district
     * and a citizen are expected and are the census's business — that is
     * the law working, not failing — but two quarters may not wear one
     * name, or every instrument line naming a district would be ambiguous.
     *
     * <p>The mixer chooses a SEAT in the name grid — first names down,
     * family names across — and a taken seat walks to the next one, the
     * walk wrapping through the whole grid and stopping when it returns to
     * where it started. That bound is the point (#1015). The de-collision
     * this replaces re-mixed with a shifted salt and had no ceiling: it was
     * a coupon collector whose only exit was a free seat, so it took some
     * four thousand re-mixes to reach the last name of four hundred and
     * never returned at all for a caller who wanted four hundred and one.
     * A walk visits each of the grid's seats exactly once, so it finds a
     * free one whenever there is a free one, in at most grid steps.
     *
     * <p>Step zero is the mixer's own seat, untouched, which is why the six
     * quarters are the six quarters they have always been: no run in this
     * repository has ever taken step one.
     */
    private static String nameFor(String zone, List<String> taken) {
        List<String> first = NamePool.firstNames();
        List<String> family = NamePool.familyNames();
        int seats = first.size() * family.size();
        int seat = Math.floorMod(mix(zone, SALT_FIRST), first.size()) * family.size()
                + Math.floorMod(mix(zone, SALT_LAST), family.size());
        for (int step = 0; step < seats; step++) {
            int s = (seat + step) % seats;
            String name = first.get(s / family.size()) + " " + family.get(s % family.size());
            if (!taken.contains(name)) {
                return name;
            }
        }
        // Unreachable through catalogOf, which refuses more zones than seats
        // before it names anything. Kept because a helper that cannot answer
        // must not answer quietly, and because the bound above is what makes
        // the hang impossible rather than merely guarded against.
        throw new IllegalStateException("every one of the " + seats
                + " names is taken; " + zone + " cannot be named");
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

    /**
     * The character line: the three columns said out loud, in thirds of
     * the declared range. Prose derived from the numbers, never authored
     * beside them — a row whose words and whose figures could disagree is
     * two rows.
     */
    public String character() {
        return band(density, "empty pavements", "steady traffic", "packed pavements")
                + ", " + band(wealth, "poor", "getting by", "moneyed")
                + ", " + band(glitch, "a clean render", "the odd stutter", "a bad memory");
    }

    private static String band(int value, String low, String middle, String high) {
        if (value < AXIS_MAX / 3) {
            return low;
        }
        return value < (2 * AXIS_MAX) / 3 ? middle : high;
    }

    /** The catalog row as an instrument speaks it: one line, one quarter, name first. */
    public String row() {
        return "DISTRICT " + name + " · " + zoneName + " · " + character()
                + " · density=" + density + " wealth=" + wealth + " glitch=" + glitch;
    }
}
