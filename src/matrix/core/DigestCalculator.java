package matrix.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Streaming canonical hasher. Values are fed in a fixed, documented order
 * (big-endian bytes); two runs feeding the same values produce the same hex.
 * This class is the referee of D-010 and the digest-invariance rule of D-027.
 */
public final class DigestCalculator {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MessageDigest md;

    public DigestCalculator() {
        this.md = newSha256();
    }

    public void putInt(int v) {
        md.update((byte) (v >>> 24));
        md.update((byte) (v >>> 16));
        md.update((byte) (v >>> 8));
        md.update((byte) v);
    }

    public void putLong(long v) {
        putInt((int) (v >>> 32));
        putInt((int) v);
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
