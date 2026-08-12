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
 * <p>The name is the zone's own for now; Dev8's naming law — our quarters
 * wear the names our citizens wear, from the same pools, never MxO's
 * borrowed map — arrives with the generator's second life (#527).
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

    /**
     * The catalog, in zone order — the whole city, built once from the zone
     * list it binds to. The same six zones give the same six districts on
     * every machine and in every universe, seed included, because no seed
     * is ever consulted.
     */
    public static List<District> catalogOf(List<PlaceGraph.Zone> zones) {
        List<District> catalog = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) {
            String zone = zones.get(i).name();
            catalog.add(new District(i, zone, zone));
        }
        return List.copyOf(catalog);
    }
}
