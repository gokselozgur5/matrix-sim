package matrix.entities;

/**
 * The recorded assumption collapsing at runtime. The repo's constitution
 * says it plainly: `processes accept SIGTERM. (This will age badly.)`
 * This is how it ages (D-003 — a deliberate, fenced anti-pattern).
 */
public final class DeletionRefusedException extends RuntimeException {
    public DeletionRefusedException(String message) {
        super(message);
    }
}
