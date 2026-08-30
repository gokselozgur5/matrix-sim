package matrix.realworld;

import matrix.causal.CausalRecord.Payload;
import matrix.causal.CausalRecord.PerceptRef;
import matrix.causal.CausalRecord.Subject;
import matrix.causal.CausalRecord.MemoryRef;
import matrix.causal.CausalRecord.Channel;
import matrix.causal.CausalRecord.ClaimClass;
import matrix.causal.CausalRecord.ClaimKey;
import matrix.causal.CausalRecord.ClaimPosition;
import matrix.causal.CausalRecord.Fidelity;
import matrix.causal.CausalRecord.Principal;
import matrix.causal.CausalRecord.PrincipalKind;
import matrix.causal.CausalRecord.PresentedClaim;
import matrix.causal.CausalId.Percept;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The bounded, immutable lived history owned by one persistent Human. */
public record MindState(Subject subject, long revision, List<MemoryTrace> history) {
    public static final int MAX_HISTORY_V1 = 64;
    private static final int SCHEMA_V3 = 3;

    /** V1 deliberately records an unresolved visible presentation, not a truth verdict. */
    public record InterpretationV1(Channel channel, Payload presentedContent,
                                   Principal perceivedSource,
                                   int uncertaintyBasisPoints,
                                   Fidelity presentedFidelity,
                                   EpistemicStatus status,
                                   PresentedClaim presentedClaim) {
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
            Objects.requireNonNull(presentedClaim, "interpretation presented claim");
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

    /**
     * Reconstructs exactly one value written by {@link #canonicalBytes()}.
     *
     * <p>This is deliberately a V3 value codec, not a Human or world restore
     * operation. It neither migrates earlier schemas nor accepts fields that
     * a future MindState may add. Every decoded component passes through the
     * same causal-value and MindState constructors as an in-memory value.
     */
    public static MindState fromCanonicalBytes(byte[] canonical) {
        Reader in = new Reader(Objects.requireNonNull(canonical, "canonical mind bytes"));
        int schema = in.readInt("schema");
        if (schema != SCHEMA_V3) {
            throw new IllegalArgumentException("unsupported MindState schema: " + schema);
        }

        Subject subject = new Subject(in.readSymbol("mind subject"));
        long revision = in.readLong("mind revision");
        int count = in.readInt("history count");
        if (count < 0 || count > MAX_HISTORY_V1) {
            throw new IllegalArgumentException("mind history count outside V1 bound: " + count);
        }

        ArrayList<MemoryTrace> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Subject memorySubject = new Subject(in.readSymbol("memory subject"));
            long memoryRevision = in.readLong("memory revision");
            int memorySequence = in.readInt("memory sequence");
            Subject basisSubject = new Subject(in.readSymbol("basis subject"));
            long perceptTick = in.readLong("percept tick");
            int perceptSequence = in.readInt("percept sequence");

            Channel channel = in.readEnum(Channel.class, "channel");
            Payload content = new Payload(in.readPayload("presented content"));
            PrincipalKind principalKind = in.readEnum(PrincipalKind.class,
                    "perceived principal kind");
            Principal perceivedSource = new Principal(principalKind,
                    in.readSymbol("perceived principal key"));
            int uncertainty = in.readInt("uncertainty basis points");
            Fidelity fidelity = in.readEnum(Fidelity.class, "presented fidelity");
            EpistemicStatus status = in.readEnum(EpistemicStatus.class,
                    "epistemic status");
            ClaimClass claimClass = in.readEnum(ClaimClass.class, "claim class");
            PresentedClaim claim = new PresentedClaim(claimClass,
                    new ClaimKey(in.readSymbol("claim key")),
                    new ClaimPosition(in.readSymbol("claim position")));

            history.add(new MemoryTrace(
                    new MemoryRef(memorySubject, memoryRevision, memorySequence),
                    new PerceptRef(basisSubject,
                            new Percept(perceptTick, perceptSequence)),
                    new InterpretationV1(channel, content, perceivedSource,
                            uncertainty, fidelity, status, claim)));
        }
        in.requireEnd();
        return new MindState(subject, revision, history);
    }

    /** Exact schema-versioned bytes; each call returns a fresh array. */
    public byte[] canonicalBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, SCHEMA_V3);
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
            writeWord(out, interpretation.presentedClaim().claimClass().name());
            writeWord(out, interpretation.presentedClaim().claim().key().value());
            writeWord(out, interpretation.presentedClaim().position().key().value());
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

    /** A bounded, forward-only reader for the closed V3 frame. */
    private static final class Reader {
        private static final int MAX_SYMBOL_BYTES = 64;
        // Payload permits 4,096 UTF-16 units; three UTF-8 bytes per unit is its maximum.
        private static final int MAX_PAYLOAD_BYTES = 3 * 4096;
        private static final int MAX_ENUM_BYTES = 64;

        private final byte[] source;
        private int cursor;

        private Reader(byte[] source) {
            this.source = source;
        }

        private int readInt(String field) {
            requireRemaining(Integer.BYTES, field);
            int value = (Byte.toUnsignedInt(source[cursor]) << 24)
                    | (Byte.toUnsignedInt(source[cursor + 1]) << 16)
                    | (Byte.toUnsignedInt(source[cursor + 2]) << 8)
                    | Byte.toUnsignedInt(source[cursor + 3]);
            cursor += Integer.BYTES;
            return value;
        }

        private long readLong(String field) {
            long high = Integer.toUnsignedLong(readInt(field));
            long low = Integer.toUnsignedLong(readInt(field));
            return (high << 32) | low;
        }

        private String readSymbol(String field) {
            return readWord(MAX_SYMBOL_BYTES, field);
        }

        private String readPayload(String field) {
            return readWord(MAX_PAYLOAD_BYTES, field);
        }

        private <E extends Enum<E>> E readEnum(Class<E> type, String field) {
            String name = readWord(MAX_ENUM_BYTES, field);
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("unknown " + field + ": " + name, unknown);
            }
        }

        private String readWord(int maximum, String field) {
            int length = readInt(field + " length");
            if (length < 0 || length > maximum) {
                throw new IllegalArgumentException(field + " byte length outside bound: " + length);
            }
            requireRemaining(length, field);
            try {
                String value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(source, cursor, length))
                        .toString();
                cursor += length;
                return value;
            } catch (CharacterCodingException malformed) {
                throw new IllegalArgumentException(field + " is not strict UTF-8", malformed);
            }
        }

        private void requireRemaining(int needed, String field) {
            if (needed < 0 || needed > source.length - cursor) {
                throw new IllegalArgumentException("truncated " + field);
            }
        }

        private void requireEnd() {
            if (cursor != source.length) {
                throw new IllegalArgumentException(
                        "trailing bytes after MindState V3: " + (source.length - cursor));
            }
        }
    }
}
