package matrix.causal;

import java.util.List;

/**
 * The closed order of one Human causal tick (D-066).
 *
 * <p>This enum names hand-over points; it does not execute them and holds no
 * world, mind, record, callback, or mutable state. {@code Simulation} is the
 * only production class allowed to advance the order because it is the only
 * composition root above both worlds. Keeping the vocabulary here gives
 * probes and future phase implementations one exact sequence without turning
 * the vocabulary into a second coordinator.
 *
 * <p>The digest and observation hooks are deliberately separate. A combined
 * final label would let an observer move before the seal while the phase list
 * still looked unchanged.
 */
public enum CausalPhase {
    SNAPSHOT_TRUTH,
    DELIVER_PERCEPTS,
    REDUCE_MINDS,
    PROPOSE_INTENTS,
    VALIDATE_AND_COMMIT,
    APPLY_EFFECTS,
    SETTLE_CONSEQUENCES,
    DIGEST,
    OBSERVE;

    private static final List<CausalPhase> CANONICAL = List.of(values());

    /**
     * The immutable complete order. The returned list is shared safely: enum
     * values are immutable and {@link List#of(Object[])} copied the values
     * array when this class was initialized.
     */
    public static List<CausalPhase> canonicalOrder() {
        return CANONICAL;
    }
}
