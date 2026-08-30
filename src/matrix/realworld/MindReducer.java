package matrix.realworld;

import matrix.causal.CausalRecord.MemoryRef;
import matrix.causal.CausalRecord.PerceptReceipt;
import matrix.causal.PerceptInputs.MindInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The pure V1 transition from visible presentations to unresolved lived memory. */
public final class MindReducer {

    public static MindState reduce(MindState prior, MindInput input) {
        Objects.requireNonNull(prior, "prior mind state");
        Objects.requireNonNull(input, "mind input");
        if (!prior.subject().equals(input.subject())) {
            throw new IllegalArgumentException("mind input belongs to another subject");
        }
        if (input.receipts().isEmpty()) return prior;
        if (input.receipts().size() > MindState.MAX_HISTORY_V1) {
            throw new IllegalArgumentException("one mind input exceeds the V1 history bound");
        }
        List<MindState.MemoryTrace> old = prior.history();
        if (!old.isEmpty()
                && input.tick() <= old.get(old.size() - 1).basis().id().tick()) {
            throw new IllegalArgumentException("nonempty mind input must follow retained history");
        }

        long revision = Math.addExact(prior.revision(), 1);
        int keep = Math.min(old.size(), MindState.MAX_HISTORY_V1 - input.receipts().size());
        ArrayList<MindState.MemoryTrace> history =
                new ArrayList<>(keep + input.receipts().size());
        history.addAll(old.subList(old.size() - keep, old.size()));
        for (int sequence = 0; sequence < input.receipts().size(); sequence++) {
            PerceptReceipt receipt = input.receipts().get(sequence);
            history.add(new MindState.MemoryTrace(
                    new MemoryRef(prior.subject(), revision, sequence),
                    receipt.ref(),
                    new MindState.InterpretationV1(
                            receipt.channel(), receipt.content(), receipt.perceivedSource(),
                            receipt.uncertaintyBasisPoints(), receipt.fidelity(),
                            MindState.EpistemicStatus.UNRESOLVED)));
        }
        return new MindState(prior.subject(), revision, history);
    }

    private MindReducer() {}
}
