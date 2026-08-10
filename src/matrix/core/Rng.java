package matrix.core;

import java.util.Random;

/** The only legal randomness (D-010): one seeded stream. Bare Random anywhere else is a constitution violation. */
public final class Rng {
    private final Random random;

    public Rng(long seed) {
        this.random = new Random(seed);
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
