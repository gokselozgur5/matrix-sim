package matrix.core;

import matrix.entities.Pill;

/**
 * Samples the world into the METRIC grammar (D-020). Also emits the ECO
 * line (additive grammar, v2.5): flock cohesion as mean nearest-neighbor
 * distance among birds versus the analytic random-uniform baseline
 * (0.5/sqrt(density)) — flocking proven by numbers, no eyes needed.
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
                    best = Math.min(best, b.pos.euclidSqCm(o.pos));
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

    /**
     * The collector reads the Matrix side; the selfsub count is real-side
     * state and only the root holds both banks (D-012) — so it arrives as
     * an argument, sampled by the root at emit time.
     */
    public MetricSnapshot sample(long tick, long selfsub) {
        int alive = world.countAlive();
        double infected = alive == 0 ? 0.0 : (double) world.countInfected() / alive;
        return new MetricSnapshot(tick,
                world.count(Pill.BLUE),
                world.count(Pill.RED),
                world.countAgents(),
                alive,
                infected,
                (double) world.ledger().balance(),
                selfsub);
    }
}
