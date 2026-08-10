package matrix.core;

/** One immutable fact on the observability plane (D-020). */
public record Event(long tick, Severity sev, String msg) {
}
