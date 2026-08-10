package matrix.core;

/** One link of the determinism chain: the canonical state hash at a tick (D-010, D-020). */
public record Digest(long tick, String sha256) {

    public String format() {
        return "DIGEST tick=" + tick + " sha=" + sha256;
    }
}
