package matrix.core;

import java.util.Locale;

/**
 * Counts of everything at a tick — the METRIC line in object form (D-020).
 * Non-finite doubles are rejected at construction: NaN in a stable grammar
 * is a lie with a stack trace deferred.
 */
public record MetricSnapshot(long tick, int blue, int red, int agents, int total,
                             double infectedFraction, double anomaly) {

    public MetricSnapshot {
        if (!Double.isFinite(infectedFraction) || !Double.isFinite(anomaly)) {
            throw new IllegalArgumentException("non-finite metric at tick " + tick);
        }
    }

    /** Locale.ROOT so the line is byte-identical on every machine. */
    public String format() {
        return String.format(Locale.ROOT,
                "METRIC tick=%d blue=%d red=%d agents=%d total=%d infected=%.3f anomaly=%.1f",
                tick, blue, red, agents, total, infectedFraction, anomaly);
    }
}
