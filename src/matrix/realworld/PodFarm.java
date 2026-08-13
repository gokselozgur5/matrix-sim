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

    /**
     * The birth event is assembled HERE, at the only seam that holds all
     * five of its facts: the stream names the universe it belongs to and
     * hands over the name, the caller brings the clock, and the farm owns
     * the rack unit and the growth ordinal. D-033 still draws nothing for
     * fate — the two draws below are the name's, exactly as before (#373
     * kept the stream byte-identical; only what the name is USED for
     * changed).
     */
    public Human grow(Rng rng, long birthTick) {
        List<String> first = NamePool.firstNames();
        List<String> family = NamePool.familyNames();
        String name = first.get(rng.nextInt(first.size()))
                + " " + family.get(rng.nextInt(family.size()));
        return grow(name, rng.seed(), birthTick);
    }

    public Human growNamed(String name, long seed, long birthTick) {
        return grow(name, seed, birthTick);
    }

    /**
     * The growth ordinal and the rack unit are assigned from the same {@code i}
     * and handed to the mind together (#847): the ordinal because the birth
     * record has to state which mind this was, the rack because it has to
     * state where. Both are also inputs to the key the die reads (#373), so
     * this line is now the place where a birth becomes a fate as well as the
     * place where it becomes a record.
     *
     * <p>Today they are the same number twice: this farm racks every mind it
     * grows, so {@code id} and the slot index agree by construction and each
     * can be computed from the other. That is a property of the FARM, not of
     * the record — the record's duty is to write down what the derivation
     * read, and the derivation reads both. It stops being true the day a mind
     * is grown without a rack, and on that day {@code pods.size()} is also the
     * wrong source for the ordinal: a podless growth would not advance the
     * count, and the next racked mind would take an ordinal already spent —
     * and would take a fate already spent with it.
     */
    private Human grow(String name, long seed, long birthTick) {
        int i = pods.size();
        String rackUnit = String.format(Locale.ROOT, "R%02d/U%02d", 1 + i / 24, 1 + i % 24);
        Pod pod = new Pod(rackUnit);
        pods.add(pod);
        return new Human(name, new Brain(name), pod, i, seed, birthTick);
    }

    public int occupiedCount() {
        int n = 0;
        for (Pod p : pods) {
            if (p.occupied()) n++;
        }
        return n;
    }
}
