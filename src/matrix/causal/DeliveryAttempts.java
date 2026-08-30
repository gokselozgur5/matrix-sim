package matrix.causal;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;

/**
 * Pure phase-two projection from frozen V1 truth to root-only delivery audits.
 *
 * <p>The projection has no policy callback or runtime dependency. Its complete
 * input is one immutable {@link TruthSnapshot}; index {@code i} always derives
 * the attempt whose tick, sequence, subject, source and payload come from truth
 * entry {@code i}. The returned random-access view is immutable and lazy, so an
 * empty snapshot is an explicit empty list and a populated snapshot allocates a
 * carrier only when that index is requested.
 *
 * <p>V1 delivers every admitted self fact at full fidelity. Its authority and
 * consent remain unestablished, its constraint classification records only a
 * lack of cited evidence, and {@code NONE_CITED} does not mean that an
 * obligation is inapplicable, absent elsewhere, repaired or discharged.
 */
public final class DeliveryAttempts {

    private static final CausalRecord.Symbol BRAIN_ALIVE =
            new CausalRecord.Symbol("brain.alive");
    private static final CausalRecord.Symbol AVATAR_PILL =
            new CausalRecord.Symbol("avatar.pill");
    private static final CausalRecord.Symbol AVATAR_POSITION_CM =
            new CausalRecord.Symbol("avatar.position_cm");

    /**
     * Map the exact canonical V1 entry view to one audit attempt per entry.
     *
     * @param snapshot immutable tick-start truth owned by the root
     * @return an immutable lazy list in the snapshot's canonical order
     */
    public static List<CausalRecord.DeliveryAttempt> connectedResidentSelfV1(
            TruthSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "delivery truth snapshot");
        if (snapshot.eligibility()
                != TruthSnapshot.EligibilityRule.CONNECTED_RESIDENT_SELF_V1) {
            throw new IllegalArgumentException("unsupported delivery eligibility rule");
        }
        return new AttemptView(snapshot.entries());
    }

    /** One read-only index-preserving projection; no mutable staging is retained. */
    private static final class AttemptView
            extends AbstractList<CausalRecord.DeliveryAttempt>
            implements RandomAccess {
        private final List<CausalRecord.TruthEntry> truth;

        private AttemptView(List<CausalRecord.TruthEntry> truth) {
            this.truth = Objects.requireNonNull(truth, "delivery truth entries");
        }

        @Override
        public CausalRecord.DeliveryAttempt get(int index) {
            CausalRecord.TruthEntry entry = truth.get(index);
            CausalRecord.Subject subject = new CausalRecord.Subject(entry.subject().key());
            return new CausalRecord.DeliveryAttempt(
                    entry.tick(),
                    entry.sequence(),
                    subject,
                    channel(entry.fact().predicate()),
                    entry.provenance(),
                    entry.provenance(),
                    entry,
                    CausalRecord.Fidelity.FULL,
                    CausalRecord.DeliveryOutcome.DELIVERED,
                    Optional.of(entry.fact().value()),
                    CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                    CausalRecord.AuthorityClass.UNESTABLISHED,
                    CausalRecord.ConsentClass.UNESTABLISHED,
                    CausalRecord.DisclosureClass.AUDIT_MATCHED,
                    CausalRecord.ConstraintClass.NO_EVIDENCE,
                    CausalRecord.ObligationClass.NONE_CITED);
        }

        @Override
        public int size() {
            return truth.size();
        }
    }

    /** Closed V1 predicate-to-sensory-channel law. */
    private static CausalRecord.Channel channel(CausalRecord.Symbol predicate) {
        Objects.requireNonNull(predicate, "delivery truth predicate");
        if (predicate.equals(BRAIN_ALIVE) || predicate.equals(AVATAR_PILL)) {
            return CausalRecord.Channel.INTERNAL;
        }
        if (predicate.equals(AVATAR_POSITION_CM)) {
            return CausalRecord.Channel.VISION;
        }
        throw new IllegalArgumentException(
                "unsupported CONNECTED_RESIDENT_SELF_V1 predicate: " + predicate.value());
    }

    private DeliveryAttempts() {}
}
