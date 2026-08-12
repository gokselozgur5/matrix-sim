package matrix.realworld;

import matrix.core.NamePool;
import matrix.core.Rng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The datacenter of brains: racks, growth, flush. Grows Humans (D-011);
 * names are drawn from the seeded stream — same seed, same Thomas.
 * Determinism doubles as fate (accepted D-011 spark).
 *
 * <p>The pools themselves are {@link NamePool}'s, not the farm's: the city
 * names its quarters out of them too (D-048, #842), and one truth gets one
 * home. The DRAW stayed here, unchanged — two {@code nextInt}s, first name
 * then family name, in that order and against those bounds — because the
 * pools moving is a refactor and the draw moving would be a new universe.
 */
public final class PodFarm {

    private final List<Pod> pods = new ArrayList<>();

    public Human grow(Rng rng) {
        List<String> first = NamePool.firstNames();
        List<String> family = NamePool.familyNames();
        String name = first.get(rng.nextInt(first.size()))
                + " " + family.get(rng.nextInt(family.size()));
        // D-033 draws nothing here: the breaking point is a pure function
        // of the name — fate was always in the name, and the rng stream
        // never hears about it (AcceptanceLoop owns the derivation).
        return grow(name);
    }

    public Human growNamed(String name) {
        return grow(name);
    }

    private Human grow(String name) {
        int i = pods.size();
        String rackUnit = String.format(Locale.ROOT, "R%02d/U%02d", 1 + i / 24, 1 + i % 24);
        Pod pod = new Pod(rackUnit);
        pods.add(pod);
        return new Human(name, new Brain(name), pod);
    }

    public int occupiedCount() {
        int n = 0;
        for (Pod p : pods) {
            if (p.occupied()) n++;
        }
        return n;
    }
}
