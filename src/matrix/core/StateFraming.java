package matrix.core;

/**
 * The one tagged-frame grammar of the canonical state stream. The encoding
 * is prefix-free by construction (skeptic finding, 2026-08-10: an untagged
 * stream let putLong(v) collide with putInt(hi)+putInt(lo)): every value
 * is a domain tag byte followed by big-endian bytes, and variable-length
 * sequences are framed with a count first. Two different feed sequences
 * therefore cannot produce one byte stream.
 *
 * Package-private and shared by both {@link StateSink} implementations —
 * the hashing sink ({@link DigestCalculator}) and the retaining sink
 * ({@code Snapshot.Writer}) — so there is exactly one encoding to drift,
 * and therefore none: DIGEST = hash(snapshot bytes) rests on this file.
 */
final class StateFraming {
    static final byte TAG_INT = 0x01;
    static final byte TAG_LONG = 0x02;
    static final byte TAG_COUNT = 0x03;
    /** The widest frame: TAG_LONG plus eight value bytes. */
    static final int MAX_FRAME = 9;

    /** Writes TAG_INT + 4 value bytes into dst[0..4]; returns the frame length. */
    static int frameInt(int v, byte[] dst) {
        dst[0] = TAG_INT;
        be32(v, dst, 1);
        return 5;
    }

    /** Writes TAG_LONG + 8 value bytes into dst[0..8]; returns the frame length. */
    static int frameLong(long v, byte[] dst) {
        dst[0] = TAG_LONG;
        be32((int) (v >>> 32), dst, 1);
        be32((int) v, dst, 5);
        return 9;
    }

    /** Writes TAG_COUNT + 4 size bytes into dst[0..4]; returns the frame length. */
    static int frameCount(int size, byte[] dst) {
        dst[0] = TAG_COUNT;
        be32(size, dst, 1);
        return 5;
    }

    private static void be32(int v, byte[] dst, int off) {
        dst[off] = (byte) (v >>> 24);
        dst[off + 1] = (byte) (v >>> 16);
        dst[off + 2] = (byte) (v >>> 8);
        dst[off + 3] = (byte) v;
    }

    private StateFraming() {}
}
