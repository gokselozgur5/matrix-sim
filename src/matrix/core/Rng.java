package matrix.core;

import java.util.List;
import java.util.Random;

/**
 * The only legal randomness (D-010): one seeded stream. Bare Random anywhere
 * else is a constitution violation.
 *
 * Every draw is implemented HERE from Random's normatively specified
 * primitives (nextInt(), nextInt(bound), nextLong(), nextDouble()) —
 * never from RandomGenerator default methods or Collections.shuffle,
 * whose algorithms are implementation details that may drift between
 * JDK versions (skeptic finding N1, 2026-08-10). Bit-stability here is
 * de jure, not de facto.
 */
public final class Rng {
    private final Random random;
    private long draws = 0;

    public Rng(long seed) {
        this.random = new Random(seed);
    }

    /** Stream position: how many primitive draws fate has consumed. Fed to the digest (skeptic finding). */
    public long draws() {
        return draws;
    }

    public boolean chance(double probability) {
        draws++;
        return random.nextDouble() < probability;
    }

    public int nextInt(int bound) {
        draws++;
        return random.nextInt(bound);
    }

    public int nextInt(int originInclusive, int boundExclusive) {
        draws++;
        return originInclusive + random.nextInt(boundExclusive - originInclusive);
    }

    /** Rejection sampling over the specified nextLong() — version-stable by construction. */
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        long m = bound - 1;
        if ((bound & m) == 0) {
            draws++;
            return random.nextLong() & m;
        }
        long r;
        do {
            draws++;
            r = random.nextLong() >>> 1;
        } while (r + m - (r % bound) < 0);
        return r % bound;
    }

    /** Fisher-Yates over the specified nextInt(bound) — independent of library shuffle internals. */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            draws++;
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
