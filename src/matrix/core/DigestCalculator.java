package matrix.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Streaming canonical hasher — the referee of D-010 and of D-027's
 * digest-invariance rule.
 *
 * The encoding is prefix-free by construction (skeptic finding, 2026-08-10:
 * an untagged stream let putLong(v) collide with putInt(hi)+putInt(lo)).
 * Every value is written as a domain tag byte followed by big-endian bytes;
 * variable-length sequences MUST be framed with putCount(size) first.
 * Two different feed sequences therefore cannot produce one byte stream.
 */
public final class DigestCalculator {
    private static final byte TAG_INT = 0x01;
    private static final byte TAG_LONG = 0x02;
    private static final byte TAG_COUNT = 0x03;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MessageDigest md;

    public DigestCalculator() {
        this.md = newSha256();
    }

    public void putInt(int v) {
        md.update(TAG_INT);
        raw32(v);
    }

    public void putLong(long v) {
        md.update(TAG_LONG);
        raw32((int) (v >>> 32));
        raw32((int) v);
    }

    /** Frames a variable-length sequence: feed the size, then exactly that many values. */
    public void putCount(int size) {
        md.update(TAG_COUNT);
        raw32(size);
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

    private void raw32(int v) {
        md.update((byte) (v >>> 24));
        md.update((byte) (v >>> 16));
        md.update((byte) (v >>> 8));
        md.update((byte) v);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing — no JVM ships without it", e);
        }
    }
}
