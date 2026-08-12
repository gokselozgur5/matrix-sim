package matrix.character;

import java.util.List;
import java.util.Locale;

/**
 * One character's stat sheet: a name, a family, and a value 1..10 on each
 * axis of that family's vocabulary — nothing else. A sheet is a value, not
 * an entity: it holds no reference into the domain and the domain holds no
 * reference to it (adoption is residency, not coupling — see the package
 * door).
 *
 * <p>The canonical constructor enforces the whole contract — exactly one
 * value per family axis, every value in 1..10 — and seals the values with
 * {@link List#copyOf}, so a sheet can never be mutated into a lie.
 *
 * <p>The values are an immutable {@code List<Integer>} rather than the
 * {@code int[]} the parked draft carried, and that is the mixer boundary
 * showing up in a place nobody expects. An array component makes a record's
 * generated equality compare by IDENTITY, so the draft hand-wrote
 * {@code equals}/{@code hashCode} — and a hand-written hash is platform
 * hashing, inside the one package the birth-seed ruling's hygiene clause
 * says must contain none. A list component gives content equality for free,
 * from the compiler, with no hash of our own writing anywhere in the layer.
 * The wart and the clause close each other.
 *
 * <p>Vocabulary discipline (D-042's stated cost): {@link #stat(String)}
 * answers only for axes of THIS family and throws for every other word.
 * A human never grows replication; the JVM says so at the exact point of
 * the ask, and the reviewer gets a stack trace instead of a style note.
 */
public record Sheet(Family family, String name, List<Integer> values) {

    public Sheet {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a sheet needs a name — sheets derive from identity");
        }
        if (values.size() != family.axisCount()) {
            throw new IllegalArgumentException(family + " carries " + family.axisCount()
                    + " axes, got " + values.size() + " values");
        }
        for (int v : values) {
            if (v < 1 || v > 10) {
                throw new IllegalArgumentException("stat out of band 1..10: " + v);
            }
        }
        values = List.copyOf(values);
    }

    /**
     * The value on one axis of this family's vocabulary. Asking a foreign
     * word is a programming error and throws: the contest grammar spans
     * families, the vocabularies never blur.
     */
    public int stat(String axis) {
        int i = family.axisIndex(axis);
        if (i < 0) {
            throw new IllegalArgumentException("no '" + axis + "' in " + family
                    + " — vocabulary discipline (D-042): " + String.join("/", family.axes()));
        }
        return values.get(i);
    }

    /**
     * The one-line form, {@link Locale#ROOT} throughout:
     * {@code Trinity [HUMAN] evasion=9 will=8 faith=7 disbelief=2 integrity=5}.
     */
    public String line() {
        StringBuilder sb = new StringBuilder(80);
        sb.append(name).append(" [").append(family).append(']');
        String[] axes = family.axes();
        for (int i = 0; i < axes.length; i++) {
            sb.append(String.format(Locale.ROOT, " %s=%d", axes[i], values.get(i)));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return line();
    }
}
