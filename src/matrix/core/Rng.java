package matrix.core;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The only legal randomness (D-010): one seeded stream. Bare Random anywhere
 * else is a constitution violation. The API is deliberately wide enough that
 * feature code never has a reason to smuggle its own generator in.
 */
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

    public int nextInt(int originInclusive, int boundExclusive) {
        return random.nextInt(originInclusive, boundExclusive);
    }

    public long nextLong(long bound) {
        return random.nextLong(bound);
    }

    public <T> void shuffle(List<T> list) {
        Collections.shuffle(list, random);
    }
}
