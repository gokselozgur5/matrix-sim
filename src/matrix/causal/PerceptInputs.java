package matrix.causal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Canonical subject-local handoff from root receipt audits to one mind input.
 *
 * <p>Allocation reads the provisional receipt projection only. Its existing
 * Percept sequence and the paired DeliveryAttempt are not ordering inputs.
 * The complete visible tuple determines a canonical run order, every visible
 * occurrence is retained, and dense identities are then allocated inside one
 * subject and tick. Equal visible values are not collapsed: without an
 * already assigned visible occurrence identity, two genuine perceptions and
 * one retransmission are indistinguishable.
 *
 * <p>After allocation, {@link MindInput} makes retransmission idempotent by
 * scoped {@code (Subject, Percept)} identity. An exact repeated receipt is
 * retained once; the same identity carrying another visible value is refused.
 * The root-side {@link Allocation} separately retains one reconstructed audit
 * for every input attempt, including attempts whose visible values are equal.
 */
public final class PerceptInputs {

    /**
     * The complete immutable input one subject may hand to a future reducer.
     *
     * <p>The list is normalized by Percept identity, exact retransmissions are
     * deduplicated, and the remaining identities must form the dense local
     * sequence {@code 0..n-1}. {@link #canonical()} is a length-delimited,
     * locale-independent spelling of every visible field; equal MindInputs
     * therefore provide byte-identical UTF-8 reducer input.
     */
    public record MindInput(long tick, CausalRecord.Subject subject,
                            List<CausalRecord.PerceptReceipt> receipts) {
        public MindInput {
            if (tick < 0) {
                throw new IllegalArgumentException("mind-input tick must be nonnegative");
            }
            Objects.requireNonNull(subject, "mind-input subject");
            receipts = normalize(tick, subject, receipts);
        }

        /** Stable complete visible spelling; it contains no root audit field. */
        public String canonical() {
            StringBuilder text = new StringBuilder("mind-input/1;");
            number(text, tick);
            word(text, subject.key().value());
            number(text, receipts.size());
            for (CausalRecord.PerceptReceipt receipt : receipts) {
                word(text, receipt.id().canonical());
                word(text, receipt.channel().name());
                word(text, receipt.content().text());
                word(text, receipt.perceivedSource().kind().name());
                word(text, receipt.perceivedSource().key().value());
                number(text, receipt.uncertaintyBasisPoints());
                word(text, receipt.fidelity().name());
            }
            return text.toString();
        }
    }

    /** Root-owned result: the mind input beside every audit that licensed it. */
    public record Allocation(MindInput input,
                             List<CausalRecord.ReceiptAudit> audits) {
        public Allocation {
            Objects.requireNonNull(input, "allocated mind input");
            audits = List.copyOf(Objects.requireNonNull(audits, "allocated audits"));
            if (audits.size() != input.receipts().size()) {
                throw new IllegalArgumentException(
                        "allocation needs exactly one root audit per visible occurrence");
            }
            boolean[] covered = new boolean[input.receipts().size()];
            for (int auditIndex = 0; auditIndex < audits.size(); auditIndex++) {
                CausalRecord.ReceiptAudit audit = audits.get(auditIndex);
                Objects.requireNonNull(audit, "allocated audit");
                CausalRecord.PerceptReceipt receipt = audit.receipt();
                if (receipt.tick() != input.tick()
                        || !receipt.subject().equals(input.subject())) {
                    throw new IllegalArgumentException(
                            "an allocated audit must belong to its mind input");
                }
                int sequence = receipt.id().sequence();
                if (sequence >= input.receipts().size()
                        || !input.receipts().get(sequence).equals(receipt)) {
                    throw new IllegalArgumentException(
                            "an allocated audit must cite an exact visible receipt");
                }
                if (covered[sequence]) {
                    throw new IllegalArgumentException(
                            "one visible occurrence cannot consume two root audits");
                }
                for (int earlier = 0; earlier < auditIndex; earlier++) {
                    CausalRecord.DeliveryAttempt previous = audits.get(earlier).delivery();
                    if (previous.tick() == audit.delivery().tick()
                            && previous.sequence() == audit.delivery().sequence()) {
                        throw new IllegalArgumentException(
                                "one root attempt cannot mint two visible occurrences");
                    }
                }
                covered[sequence] = true;
            }
            for (boolean present : covered) {
                if (!present) {
                    throw new IllegalArgumentException(
                            "every visible receipt needs at least one root audit");
                }
            }
        }
    }

    /**
     * Allocate the complete visible candidate multiset for one subject/tick.
     *
     * @param tick declared visible input tick, including for an empty input
     * @param subject sole recipient of this input
     * @param provisional one root audit per visible occurrence; provisional
     *                    Percept sequence and hidden delivery fields are ignored
     */
    public static Allocation allocate(long tick, CausalRecord.Subject subject,
                                      List<CausalRecord.ReceiptAudit> provisional) {
        if (tick < 0) {
            throw new IllegalArgumentException("allocation tick must be nonnegative");
        }
        Objects.requireNonNull(subject, "allocation subject");
        Objects.requireNonNull(provisional, "provisional receipt audits");

        ArrayList<CausalRecord.ReceiptAudit> ordered = new ArrayList<>(provisional.size());
        for (CausalRecord.ReceiptAudit audit : provisional) {
            Objects.requireNonNull(audit, "provisional receipt audit");
            CausalRecord.PerceptReceipt receipt = audit.receipt();
            if (receipt.tick() != tick || !receipt.subject().equals(subject)) {
                throw new IllegalArgumentException(
                        "every provisional receipt must share allocation subject and tick");
            }
            int position = ordered.size();
            while (position > 0
                    && visibleCompare(receipt, ordered.get(position - 1).receipt()) < 0) {
                position--;
            }
            ordered.add(position, audit);
        }

        ArrayList<CausalRecord.PerceptReceipt> receipts = new ArrayList<>(ordered.size());
        ArrayList<CausalRecord.ReceiptAudit> audits = new ArrayList<>(ordered.size());
        for (int sequence = 0; sequence < ordered.size(); sequence++) {
            CausalRecord.ReceiptAudit provisionalAudit = ordered.get(sequence);
            CausalRecord.PerceptReceipt visible = provisionalAudit.receipt();
            CausalRecord.PerceptReceipt allocated = new CausalRecord.PerceptReceipt(
                    new CausalId.Percept(tick, sequence),
                    subject,
                    visible.channel(),
                    visible.content(),
                    visible.perceivedSource(),
                    visible.uncertaintyBasisPoints(),
                    visible.fidelity());
            receipts.add(allocated);
            audits.add(new CausalRecord.ReceiptAudit(
                    allocated, provisionalAudit.delivery()));
        }
        return new Allocation(new MindInput(tick, subject, receipts), audits);
    }

    /** Compare every mind-visible field except the provisional identity. */
    private static int visibleCompare(CausalRecord.PerceptReceipt left,
                                      CausalRecord.PerceptReceipt right) {
        int order = Integer.compare(channelRank(left.channel()), channelRank(right.channel()));
        if (order == 0) order = left.content().text().compareTo(right.content().text());
        if (order == 0) {
            order = Integer.compare(principalKindRank(left.perceivedSource().kind()),
                    principalKindRank(right.perceivedSource().kind()));
        }
        if (order == 0) {
            order = left.perceivedSource().key().compareTo(right.perceivedSource().key());
        }
        if (order == 0) {
            order = Integer.compare(left.uncertaintyBasisPoints(),
                    right.uncertaintyBasisPoints());
        }
        if (order == 0) {
            order = Integer.compare(fidelityRank(left.fidelity()),
                    fidelityRank(right.fidelity()));
        }
        return order;
    }

    private static int channelRank(CausalRecord.Channel channel) {
        return switch (channel) {
            case VISION -> 0;
            case AUDIO -> 1;
            case TEXT -> 2;
            case HAPTIC -> 3;
            case INTERNAL -> 4;
            case NO_SIGNAL -> 5;
        };
    }

    private static int principalKindRank(CausalRecord.PrincipalKind kind) {
        return switch (kind) {
            case HUMAN -> 0;
            case MACHINE -> 1;
            case SYSTEM -> 2;
            case INSTITUTION -> 3;
            case PLACE -> 4;
            case UNKNOWN -> 5;
        };
    }

    private static int fidelityRank(CausalRecord.Fidelity fidelity) {
        return switch (fidelity) {
            case FULL -> 0;
            case PARTIAL -> 1;
            case NONE -> 2;
        };
    }

    /** Normalize a post-allocation list by scoped identity. */
    private static List<CausalRecord.PerceptReceipt> normalize(
            long tick, CausalRecord.Subject subject,
            List<CausalRecord.PerceptReceipt> source) {
        Objects.requireNonNull(source, "mind-input receipts");
        ArrayList<CausalRecord.PerceptReceipt> ordered = new ArrayList<>(source.size());
        for (CausalRecord.PerceptReceipt receipt : source) {
            Objects.requireNonNull(receipt, "mind-input receipt");
            if (receipt.tick() != tick || !receipt.subject().equals(subject)) {
                throw new IllegalArgumentException(
                        "every receipt must share its mind-input subject and tick");
            }
            int position = ordered.size();
            while (position > 0
                    && receipt.id().compareTo(ordered.get(position - 1).id()) < 0) {
                position--;
            }
            ordered.add(position, receipt);
        }

        ArrayList<CausalRecord.PerceptReceipt> unique = new ArrayList<>(ordered.size());
        for (CausalRecord.PerceptReceipt receipt : ordered) {
            if (!unique.isEmpty()
                    && unique.get(unique.size() - 1).id().equals(receipt.id())) {
                if (!unique.get(unique.size() - 1).equals(receipt)) {
                    throw new IllegalArgumentException(
                            "one scoped percept identity cannot carry conflicting values");
                }
                continue;
            }
            if (receipt.id().sequence() != unique.size()) {
                throw new IllegalArgumentException(
                        "mind-input percept identities must be dense from zero");
            }
            unique.add(receipt);
        }
        return List.copyOf(unique);
    }

    private static void word(StringBuilder target, String value) {
        target.append(value.getBytes(StandardCharsets.UTF_8).length)
                .append(':').append(value).append(';');
    }

    private static void number(StringBuilder target, long value) {
        target.append(value).append(';');
    }

    private PerceptInputs() {}
}
