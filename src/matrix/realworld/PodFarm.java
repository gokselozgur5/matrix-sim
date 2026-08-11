package matrix.realworld;

import matrix.core.Rng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The datacenter of brains: racks, growth, flush. Grows Humans (D-011);
 * names are drawn from the seeded stream — same seed, same Thomas.
 * Determinism doubles as fate (accepted D-011 spark).
 */
public final class PodFarm {
    private static final String[] FIRST = {
            "Thomas", "Trin", "Milo", "Dana", "Ezra", "Vera", "Otto", "Nadia",
            "Silas", "June", "Marcus", "Lena", "Hugo", "Iris", "Felix", "Mara",
            "Dario", "Selma", "Ivan", "Noor"};
    private static final String[] LAST = {
            "Anderson", "Vance", "Okafor", "Lindqvist", "Marek", "Osei", "Petrov",
            "Sato", "Weaver", "Kaya", "Moreau", "Iglesias", "Novak", "Reyes",
            "Berg", "Duran", "Kovacs", "Aydin", "Frost", "Adeyemi"};

    private final List<Pod> pods = new ArrayList<>();

    public Human grow(Rng rng) {
        String name = FIRST[rng.nextInt(FIRST.length)] + " " + LAST[rng.nextInt(LAST.length)];
        return growNamed(name);
    }

    /** For the fated: the ledger does not roll dice on the anomaly's name. */
    public Human growNamed(String name) {
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
