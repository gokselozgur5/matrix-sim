package matrix.core;

/**
 * One immutable fact on the observability plane (D-020).
 * The message is forced single-line so the log stays one-event-per-line.
 */
public record Event(long tick, Severity sev, String msg) {
    public Event {
        msg = msg.replace('\n', ' ').replace('\r', ' ');
    }
}
