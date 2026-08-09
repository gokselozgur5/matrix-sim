package matrix.core;

import java.util.List;
import java.util.Random;

public final class Rng {
    private final Random r;

    public Rng(long seed) {
        this.r = new Random(seed);
    }

    public boolean chance(double p) {
        return r.nextDouble() < p;
    }

    public int nextInt(int bound) {
        return r.nextInt(bound);
    }

    public int step() {
        return r.nextInt(3) - 1;
    }

    public <T> T pick(List<T> list) {
        return list.get(r.nextInt(list.size()));
    }
}
