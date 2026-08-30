package matrix.causal;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure one-attempt projection from a root delivery audit to mind-visible data.
 *
 * <p>This class does not allocate percept identities, traverse a batch, sort,
 * deduplicate, or choose uncertainty or claim semantics. Those are
 * visible-input policy decisions outside this leaf. The caller supplies an immutable
 * {@link Presentation} only when the attempt presented something. The mapper
 * copies only presented content, declared source, and the delivery system's
 * presented fidelity classification; actual source, frozen truth, provenance,
 * audit classifications, and delivery identity remain reachable only through
 * the root-owned {@link CausalRecord.ReceiptAudit} returned beside the receipt.
 *
 * <p>An occluded attempt requires an absent presentation and returns empty.
 * It never manufactures an empty-content or {@code NO_SIGNAL} receipt. This
 * mapper refuses that channel until a future observable-silence rule begins
 * with typed availability evidence the subject could itself perceive.
 */
public final class PerceptReceipts {

    /**
     * Visible allocation, uncertainty, and asserted claim chosen before projection.
     * The claim says what was presented, never whether it was true or believed.
     */
    public record Presentation(CausalId.Percept id, int uncertaintyBasisPoints,
                               CausalRecord.PresentedClaim presentedClaim) {
        public Presentation {
            Objects.requireNonNull(id, "visible percept id");
            Objects.requireNonNull(presentedClaim, "visible presented claim");
            if (uncertaintyBasisPoints < 0 || uncertaintyBasisPoints > 10_000) {
                throw new IllegalArgumentException(
                        "presented uncertainty must be between 0 and 10000 basis points");
            }
        }
    }

    /**
     * Project one attempt without deriving visible identity or uncertainty.
     *
     * @param attempt complete root-side delivery audit
     * @param presentation present exactly when the attempt presented content
     * @return the root audit pairing, or empty for an occluded attempt
     */
    public static Optional<CausalRecord.ReceiptAudit> project(
            CausalRecord.DeliveryAttempt attempt,
            Optional<Presentation> presentation) {
        Objects.requireNonNull(attempt, "delivery attempt");
        presentation = Objects.requireNonNull(presentation, "visible presentation");

        if (attempt.outcome() == CausalRecord.DeliveryOutcome.OCCLUDED) {
            if (presentation.isPresent()) {
                throw new IllegalArgumentException(
                        "an occluded attempt cannot consume visible presentation metadata");
            }
            return Optional.empty();
        }
        if (attempt.channel() == CausalRecord.Channel.NO_SIGNAL) {
            throw new IllegalArgumentException(
                    "NO_SIGNAL needs typed observable availability evidence");
        }

        if (presentation.isEmpty()) {
            throw new IllegalArgumentException(
                    "a delivered or degraded attempt needs visible presentation metadata");
        }
        Presentation visible = presentation.get();
        if (visible.id().tick() != attempt.tick()) {
            throw new IllegalArgumentException(
                    "visible percept tick must match its delivery attempt");
        }

        CausalRecord.PerceptReceipt receipt = new CausalRecord.PerceptReceipt(
                visible.id(),
                attempt.subject(),
                attempt.channel(),
                attempt.presentedContent().get(),
                visible.presentedClaim(),
                attempt.declaredSource(),
                visible.uncertaintyBasisPoints(),
                attempt.fidelity());
        return Optional.of(new CausalRecord.ReceiptAudit(receipt, attempt));
    }

    private PerceptReceipts() {}
}
