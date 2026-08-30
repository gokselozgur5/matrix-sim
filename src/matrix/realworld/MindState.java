package matrix.realworld;

import matrix.causal.CausalRecord.Payload;
import matrix.causal.CausalRecord.PerceptRef;
import matrix.causal.CausalRecord.Subject;
import matrix.causal.CausalRecord.MemoryRef;
import matrix.causal.CausalRecord.Channel;
import matrix.causal.CausalRecord.Fidelity;
import matrix.causal.CausalRecord.Principal;
import matrix.causal.CausalId.Percept;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** The bounded, immutable lived history owned by one persistent Human. */
public record MindState(Subject subject, long revision, List<MemoryTrace> history) {
    public static final int MAX_HISTORY_V1 = 64;
    private static final int SCHEMA_V2 = 2;

    /** V1 deliberately records an unresolved visible presentation, not a truth verdict. */
    public record InterpretationV1(Channel channel, Payload presentedContent,
                                   Principal perceivedSource,
                                   int uncertaintyBasisPoints,
                                   Fidelity presentedFidelity,
                                   EpistemicStatus status) {
        public InterpretationV1 {
            Objects.requireNonNull(channel, "interpretation channel");
            Objects.requireNonNull(presentedContent, "interpretation content");
            Objects.requireNonNull(perceivedSource, "interpretation perceived source");
            if (uncertaintyBasisPoints < 0 || uncertaintyBasisPoints > 10_000) {
                throw new IllegalArgumentException(
                        "interpretation uncertainty must be within 0..10000 basis points");
            }
            Objects.requireNonNull(presentedFidelity, "interpretation presented fidelity");
            Objects.requireNonNull(status, "interpretation epistemic status");
        }
    }

    /** V1 makes no true, false, believed, deceptive or persuasive classification. */
    public enum EpistemicStatus { UNRESOLVED }

    /** A visible occurrence and the unresolved presentation retained from it. */
    public record MemoryTrace(MemoryRef id, PerceptRef basis,
                              InterpretationV1 interpretation) {
        public MemoryTrace {
            Objects.requireNonNull(id, "memory identity");
            Objects.requireNonNull(basis, "memory percept basis");
            Objects.requireNonNull(interpretation, "memory interpretation");
        }
    }

    public MindState {
        Objects.requireNonNull(subject, "mind subject");
        if (revision < 0) throw new IllegalArgumentException("mind revision must be nonnegative");
        Objects.requireNonNull(history, "mind history");
        if (history.size() > MAX_HISTORY_V1) {
            throw new IllegalArgumentException("mind history exceeds V1 bound");
        }
        history = List.copyOf(history);
        MemoryRef previous = null;
        Percept previousBasis = null;
        for (MemoryTrace trace : history) {
            Objects.requireNonNull(trace, "mind history trace");
            if (!trace.basis().subject().equals(subject)) {
                throw new IllegalArgumentException("memory basis belongs to another subject");
            }
            if (!trace.id().subject().equals(subject)) {
                throw new IllegalArgumentException("memory identity belongs to another subject");
            }
            if (trace.id().revision() > revision) {
                throw new IllegalArgumentException("memory identity is newer than mind revision");
            }
            if (previous != null && previous.compareTo(trace.id()) >= 0) {
                throw new IllegalArgumentException("memory identities must be unique and ordered");
            }
            if (previousBasis != null && previousBasis.compareTo(trace.basis().id()) > 0) {
                throw new IllegalArgumentException("memory percept bases must not move backward");
            }
            previous = trace.id();
            previousBasis = trace.basis().id();
        }
    }

    /** Birth has identity but no fabricated experience. */
    public static MindState initial(Subject subject) {
        return new MindState(subject, 0, List.of());
    }

    /** Exact schema-versioned bytes; each call returns a fresh array. */
    public byte[] canonicalBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, SCHEMA_V2);
        writeWord(out, subject.key().value());
        writeLong(out, revision);
        writeInt(out, history.size());
        for (MemoryTrace trace : history) {
            writeWord(out, trace.id().subject().key().value());
            writeLong(out, trace.id().revision());
            writeInt(out, trace.id().sequence());
            writeWord(out, trace.basis().subject().key().value());
            writeLong(out, trace.basis().id().tick());
            writeInt(out, trace.basis().id().sequence());
            InterpretationV1 interpretation = trace.interpretation();
            writeWord(out, interpretation.channel().name());
            writeWord(out, interpretation.presentedContent().text());
            writeWord(out, interpretation.perceivedSource().kind().name());
            writeWord(out, interpretation.perceivedSource().key().value());
            writeInt(out, interpretation.uncertaintyBasisPoints());
            writeWord(out, interpretation.presentedFidelity().name());
            writeWord(out, interpretation.status().name());
        }
        return out.toByteArray();
    }

    private static void writeWord(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24); out.write(value >>> 16); out.write(value >>> 8); out.write(value);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        writeInt(out, (int) (value >>> 32)); writeInt(out, (int) value);
    }
}
