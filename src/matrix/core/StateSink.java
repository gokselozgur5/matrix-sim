package matrix.core;

/**
 * The seam between the canonical state walk and what happens to its bytes
 * (D-023 stage 3, crown #179): {@code World.digestInto} feeds a sink and
 * never asks what it is. {@link DigestCalculator} hashes the stream;
 * {@code Snapshot.Writer} retains it — one walk, two sinks. Both speak the
 * one tagged-frame grammar of {@link StateFraming}, so two sinks fed by
 * the same walk cannot disagree about what they saw.
 */
public interface StateSink {

    void putInt(int v);

    void putLong(long v);

    /** Frames a variable-length sequence: feed the size, then exactly that many values. */
    void putCount(int size);
}
