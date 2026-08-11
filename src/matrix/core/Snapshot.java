package matrix.core;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * The retained canonical byte stream of one tick (D-023 stage 3, crown
 * #179): the digestInto field-walk with values kept, plus tick, version
 * and its SHA-256. An equality certificate and a divergence microscope —
 * NOT a save-game: object resurrection is replay's job in the
 * coarse+seeded model (boot from genesis-of-epoch, re-execute).
 *
 * DIGEST = hash(snapshot bytes) holds by construction, never by
 * discipline: {@link #of} runs the one walk into the retaining
 * {@link Writer} and hashes the bytes it kept, and the Writer emits the
 * exact tagged frames DigestCalculator hashes — both sinks call
 * {@link StateFraming}, one grammar, no second serialization to drift.
 *
 * Content questions go through {@link #sha256Hex} and
 * {@link #firstDifference}; the record's own equals stays Java's
 * reference semantics for arrays — certificates are compared, not
 * interchanged. The byte array is owned by the certificate: callers
 * read, never write. The ChronosLog edge of the crown ({@code ChronosLog
 * o-- Snapshot}, epoch-boundary markers in the record) lands with stage
 * 4's reload flip, where the fold's reader learns the marker grammar in
 * the same breath.
 */
public record Snapshot(long tick, int version, String sha256Hex, byte[] bytes) {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * The retaining {@link StateSink}: same walk, same frames as the
     * hashing sink, bytes kept instead of hashed.
     */
    public static final class Writer implements StateSink {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        private final byte[] frame = new byte[StateFraming.MAX_FRAME];

        @Override
        public void putInt(int v) {
            out.write(frame, 0, StateFraming.frameInt(v, frame));
        }

        @Override
        public void putLong(long v) {
            out.write(frame, 0, StateFraming.frameLong(v, frame));
        }

        @Override
        public void putCount(int size) {
            out.write(frame, 0, StateFraming.frameCount(size, frame));
        }

        public byte[] toBytes() {
            return out.toByteArray();
        }
    }

    /**
     * One walk into the retaining sink; the sha is then hashed FROM the
     * retained bytes — not computed beside them — so the certificate
     * cannot disagree with its own preimage. Reads the world, moves
     * nothing, draws nothing.
     */
    public static Snapshot of(World world) {
        return of(world, w -> { });
    }

    /**
     * The full preimage: the world walk plus whatever segments the root
     * appends after it (the D-033 realworld segment, and every future one)
     * — the snapshot must retain exactly what the digest hashes, or
     * hash-equals-DIGEST breaks the day a segment lands.
     */
    public static Snapshot of(World world, java.util.function.Consumer<StateSink> extraSegments) {
        Writer writer = new Writer();
        world.digestInto(writer);
        extraSegments.accept(writer);
        byte[] bytes = writer.toBytes();
        return new Snapshot(world.tick(), world.version(), sha256Hex(bytes), bytes);
    }

    /** {@code SNAPSHOT tick=N sha=… bytes=N} — the instrument line, Digest.format's sibling (D-020). */
    public String format() {
        return "SNAPSHOT tick=" + tick + " sha=" + sha256Hex + " bytes=" + bytes.length;
    }

    /**
     * The microscope's objective: byte offset of the first difference
     * against another snapshot, or -1 when the streams are identical.
     * When one stream is a prefix of the other, the offset is the
     * shorter length — the first byte one side has and the other lacks.
     */
    public int firstDifference(Snapshot other) {
        int n = Math.min(bytes.length, other.bytes.length);
        for (int i = 0; i < n; i++) {
            if (bytes[i] != other.bytes[i]) {
                return i;
            }
        }
        return bytes.length == other.bytes.length ? -1 : n;
    }

    /**
     * The microscope's one-line verdict: {@code DIFF at byte N} with the
     * two bytes and the two ticks, or {@code IDENTICAL}. Offset plus
     * context is the stage-3 magnification; naming the differing FIELD
     * from the offset is the stage-4/5 refinement, when divergence
     * hunting starts in earnest.
     */
    public String diffLine(Snapshot other) {
        int at = firstDifference(other);
        if (at < 0) {
            return "IDENTICAL bytes=" + bytes.length;
        }
        return "DIFF at byte " + at
                + " (tick " + tick + ": " + byteAt(bytes, at)
                + " vs tick " + other.tick + ": " + byteAt(other.bytes, at) + ")";
    }

    private static String byteAt(byte[] b, int i) {
        return i < b.length ? String.format(Locale.ROOT, "0x%02x", b[i]) : "end-of-stream";
    }

    private static String sha256Hex(byte[] bytes) {
        byte[] digest = sha256().digest(bytes);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(HEX[(b >>> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return sb.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing — no JVM ships without it", e);
        }
    }
}
