package matrix.core;

import matrix.entities.Pill;

/**
 * Samples the world into the METRIC grammar (D-020). Also emits the ECO
 * line (additive grammar, v2.5): flock cohesion as mean nearest-neighbor
 * distance among birds versus the analytic random-uniform baseline
 * (0.5/sqrt(density)) — flocking proven by numbers, no eyes needed.
 * And the ATTN line (additive grammar, D-024 P0): the RegionMap's
 * attention census — where the connected minds are, spoken in numbers.
 */
public final class MetricsCollector {
    private final World world;

    public MetricsCollector(World world) {
        this.world = world;
    }

    public String ecoLine(long tick) {
        java.util.List<matrix.entities.MatrixEntity> birds = new java.util.ArrayList<>();
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.eco.EnvironmentProgram p
                    && p.species.kingdom() == matrix.entities.eco.Kingdom.FAUNA_BIRD) {
                birds.add(e);
            }
        }
        if (birds.size() < 2) {
            return String.format(java.util.Locale.ROOT, "ECO tick=%d birds=%d", tick, birds.size());
        }
        long sum = 0;
        for (var b : birds) {
            long best = Long.MAX_VALUE;
            for (var o : birds) {
                if (o != b) {
                    best = Math.min(best, Geo.distSqCm(b.xCm(), b.yCm(), o.xCm(), o.yCm()));
                }
            }
            sum += Math.round(Math.sqrt((double) best));
        }
        long meanNn = sum / birds.size();
        double density = birds.size() / ((double) matrix.core.Config.WORLD_W_CM * matrix.core.Config.WORLD_H_CM);
        long baseline = Math.round(0.5 / Math.sqrt(density));
        return String.format(java.util.Locale.ROOT,
                "ECO tick=%d birds=%d flock_mnn_cm=%d random_baseline_cm=%d insects=%d flora=%d mammals=%d weather=%d",
                tick, birds.size(), meanNn, baseline,
                kingdomCount(matrix.entities.eco.Kingdom.FAUNA_INSECT),
                kingdomCount(matrix.entities.eco.Kingdom.FLORA),
                kingdomCount(matrix.entities.eco.Kingdom.FAUNA_MAMMAL),
                kingdomCount(matrix.entities.eco.Kingdom.WEATHER));
    }

    /**
     * The attention instrument (D-024 P0): region census plus the three
     * most-watched zones, ranked by avatar count with region-index
     * tiebreak. Region index is zone index, so the names are PlaceGraph's
     * own. Byte-stable: integers, fixed names, Locale.ROOT.
     */
    public String attnLine(long tick) {
        RegionMap regions = world.regions();
        java.util.List<PlaceGraph.Zone> zones = world.places().zones();
        int n = regions.regionCount();
        int hot = regions.hotCount();
        StringBuilder top = new StringBuilder();
        boolean[] taken = new boolean[n];
        for (int k = 0; k < 3 && k < n; k++) {
            int best = -1;
            for (int r = 0; r < n; r++) {
                if (!taken[r] && (best == -1 || regions.avatarCount(r) > regions.avatarCount(best))) {
                    best = r;
                }
            }
            taken[best] = true;
            if (k > 0) {
                top.append(',');
            }
            top.append(zones.get(best).name()).append(':').append(regions.avatarCount(best));
        }
        return String.format(java.util.Locale.ROOT,
                "ATTN tick=%d regions=%d hot=%d cold=%d top=\"%s\"", tick, n, hot, n - hot, top);
    }

    /** Per-kingdom census — the D-018 caps become checkable in the instrument stream. */
    private int kingdomCount(matrix.entities.eco.Kingdom kingdom) {
        int n = 0;
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.eco.EnvironmentProgram p
                    && p.species.kingdom() == kingdom) {
                n++;
            }
        }
        return n;
    }

    public MetricSnapshot sample(long tick) {
        int alive = world.countAlive();
        double infected = alive == 0 ? 0.0 : (double) world.countInfected() / alive;
        return new MetricSnapshot(tick,
                world.count(Pill.BLUE),
                world.count(Pill.RED),
                world.countAgents(),
                alive,
                infected,
                (double) world.ledger().balance());
    }
}
