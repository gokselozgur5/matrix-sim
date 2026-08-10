package matrix.core;

import matrix.entities.Pill;

/** Samples the world into the METRIC grammar (D-020). Infection and anomaly wake up in v2/v3. */
public final class MetricsCollector {
    private final World world;

    public MetricsCollector(World world) {
        this.world = world;
    }

    public MetricSnapshot sample(long tick) {
        return new MetricSnapshot(tick,
                world.count(Pill.BLUE),
                world.count(Pill.RED),
                world.countAgents(),
                world.countAlive(),
                0.0,
                0.0);
    }
}
