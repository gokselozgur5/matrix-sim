package matrix.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Streaming canonical hasher — the referee of D-010 and of D-027's
 * digest-invariance rule. The hashing {@link StateSink}: it consumes the
 * walk and keeps only the SHA-256 (the retaining sink, {@code
 * Snapshot.Writer}, keeps the bytes). The tagged, prefix-free frame
 * grammar lives in {@link StateFraming}, shared by both sinks — this
 * class owns nothing of the encoding but the hash.
 */
public final class DigestCalculator implements StateSink {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final byte[] frame = new byte[StateFraming.MAX_FRAME];
    private MessageDigest md;

    public DigestCalculator() {
        this.md = newSha256();
    }

    @Override
    public void putInt(int v) {
        md.update(frame, 0, StateFraming.frameInt(v, frame));
    }

    @Override
    public void putLong(long v) {
        md.update(frame, 0, StateFraming.frameLong(v, frame));
    }

    /** Frames a variable-length sequence: feed the size, then exactly that many values. */
    @Override
    public void putCount(int size) {
        md.update(frame, 0, StateFraming.frameCount(size, frame));
    }

    /** Returns the hex digest and resets for the next chain link. */
    public String finishHex() {
        byte[] bytes = md.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX[(b >>> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        md = newSha256();
        return sb.toString();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing — no JVM ships without it", e);
        }
    }
}
