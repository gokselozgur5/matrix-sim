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
