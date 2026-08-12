import matrix.Simulation;
import matrix.core.Config;
import matrix.core.District;
import matrix.core.PlaceGraph;
import matrix.core.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * Probe: naming a city must not cost the world a single die roll.
 *
 * The district catalog (D-048, #289) is derived from zone names by our own
 * mixer. The claim that it is STREAM-NEUTRAL — zero draws off the one
 * seeded stream, so every position, every pill and every fate lands exactly
 * where it landed before the city had names — is the kind of claim that is
 * true the day it is written and false three units later, when someone
 * makes a quarter's character depend on who lives in it.
 *
 * <p>Three legs, because each alone is refutable:
 *
 * <ol>
 * <li><b>Seed independence.</b> The catalog is read out of four live
 * universes with four different seeds. If naming ever consulted the
 * stream, two universes would name their quarters differently — the
 * loudest possible symptom, and the one a digest alone would never
 * name.</li>
 * <li><b>Construction neutrality.</b> A stream's draw counter is read
 * across a hundred {@link PlaceGraph} constructions. Any draw, direct or
 * transitive, moves the counter.</li>
 * <li><b>Order independence.</b> A catalog built after the stream has
 * been burned down is the same catalog. A mixer that had quietly grown a
 * dependency on stream POSITION rather than on the seed would pass leg
 * two and fail here.</li>
 * </ol>
 *
 * <p>Deliberately NOT a pinned boot-draw count. The boot total moves the
 * day any unit anywhere adds a seeded decision at birth — the commuter
 * address book (#290) does exactly that, by declaration — and a probe that
 * fails on someone else's lawful move teaches its readers to ignore it.
 * What is pinned here is the district catalog's own relationship to the
 * stream: none.
 *
 * Usage: java -cp out:probes/out DistrictNeutral [ticks-ignored]
 */
public final class DistrictNeutral {

    private static final long[] SEEDS = {42, 7, 1, 55};
    private static final int CONSTRUCTIONS = 100;
    private static final int BURN = 1_000;

    public static void main(String[] args) throws Exception {
        List<String> faults = new ArrayList<>();

        // Leg 1: four universes, one city.
        List<String> reference = null;
        for (long seed : SEEDS) {
            Simulation sim = new Simulation(seed, null, null);
            List<String> rows = rows(Probes.world(sim).places());
            if (reference == null) {
                reference = rows;
                for (String row : rows) {
                    System.out.println("CATALOG " + row);
                }
            } else if (!reference.equals(rows)) {
                faults.add("seed " + seed + " named the city differently");
            }
            System.out.println("SEED " + seed + " districts=" + rows.size()
                    + " matches_reference=" + reference.equals(rows));
        }

        // Leg 2: a hundred constructions cost the stream nothing.
        Rng rng = new Rng(42);
        long before = rng.draws();
        for (int i = 0; i < CONSTRUCTIONS; i++) {
            new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM);
        }
        long spent = rng.draws() - before;
        System.out.println("DRAWS constructions=" + CONSTRUCTIONS + " spent=" + spent);
        if (spent != 0) {
            faults.add("building the catalog spent " + spent + " draws");
        }

        // Leg 3: the same city, built out of a burned-down stream.
        for (int i = 0; i < BURN; i++) {
            rng.nextInt(1_000);
        }
        List<String> afterBurn = rows(new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM));
        System.out.println("BURNED draws=" + rng.draws()
                + " matches_reference=" + afterBurn.equals(reference));
        if (!afterBurn.equals(reference)) {
            faults.add("a catalog built after " + BURN + " draws is a different city");
        }

        for (String fault : faults) {
            System.out.println("FAULT " + fault);
        }
        System.out.println(faults.isEmpty()
                ? "VERDICT DISTRICTS_DRAW_NOTHING"
                : "VERDICT DISTRICTS_TOUCHED_THE_STREAM faults=" + faults.size());
    }

    /** One row per quarter, everything the catalog claims about it — the whole comparable surface. */
    private static List<String> rows(PlaceGraph places) {
        List<String> rows = new ArrayList<>();
        for (District d : places.districts()) {
            rows.add(d.row());
        }
        return rows;
    }

    private DistrictNeutral() {}
}
