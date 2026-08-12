package matrix.core;

import java.util.List;

/**
 * The name pools of this world: twenty first names, twenty family names,
 * in one place — and this is the only place.
 *
 * <p>Both banks read from here. The pod farm draws a citizen's name off the
 * one seeded stream (D-011: same seed, same Thomas); the district catalog
 * mixes a quarter's name out of its zone name and takes no draw at all
 * (D-048, and Dev8's naming law — our quarters wear our citizens' names,
 * never a borrowed map). They index the same two sequences, so they cannot
 * disagree. Not "are checked for disagreement": cannot. {@code FateAtlas}
 * states the same rule about {@code AcceptanceLoop} — read the one source,
 * never two copies of the same arithmetic with a probe hired to referee
 * them.
 *
 * <p>The pools live in {@code core} because core is already below
 * {@code realworld} in the dependency order and the reverse is forbidden
 * (A1): the farm may import the kernel, the kernel may not import the farm.
 *
 * <p><b>Order is load-bearing on both banks.</b> The farm indexes with the
 * stream, the city indexes with the mixer, so re-sorting either pool
 * renames every citizen and every quarter in the world while the pool's
 * CONTENTS stay identical. That is why these are {@link List#of} — indexed
 * sequences in declaration order — and never a set or a map, whose
 * iteration order is JVM-shaped (the #212 hygiene ruling). It is also why
 * no array leaves this class: a shared {@code String[]} is metal any
 * package-mate can rewrite in place, and the write would be silent.
 */
public final class NamePool {

    private static final List<String> FIRST = List.of(
            "Thomas", "Trin", "Milo", "Dana", "Ezra", "Vera", "Otto", "Nadia",
            "Silas", "June", "Marcus", "Lena", "Hugo", "Iris", "Felix", "Mara",
            "Dario", "Selma", "Ivan", "Noor");

    private static final List<String> LAST = List.of(
            "Anderson", "Vance", "Okafor", "Lindqvist", "Marek", "Osei", "Petrov",
            "Sato", "Weaver", "Kaya", "Moreau", "Iglesias", "Novak", "Reyes",
            "Berg", "Duran", "Kovacs", "Aydin", "Frost", "Adeyemi");

    /** The first names, in declaration order — immutable, and the order every reader indexes. */
    public static List<String> firstNames() {
        return FIRST;
    }

    /** The family names, under the same order law and the same immutability. */
    public static List<String> familyNames() {
        return LAST;
    }

    private NamePool() {}
}
