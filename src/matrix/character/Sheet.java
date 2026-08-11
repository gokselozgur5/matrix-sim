package matrix.character;

import java.util.Arrays;
import java.util.Locale;

/**
 * One character's stat sheet: a name, a family, and a value 1..10 on each
 * axis of that family's vocabulary — nothing else. A sheet is a value, not
 * an entity: it holds no reference into the domain and the domain holds no
 * reference to it (the D-042 gate is still open; see the package door).
 *
 * <p>The canonical constructor enforces the whole contract — exactly one
 * value per family axis, every value in 1..10 — and copies the array both
 * in and out, so a sheet can never be mutated into a lie. Equality is by
 * content ({@code int[]} would otherwise compare by identity — the record
 * wart, fixed here).
 *
 * <p>Vocabulary discipline (D-042's stated cost): {@link #stat(String)}
 * answers only for axes of THIS family and throws for every other word.
 * A human never grows replication; the type system says so at the exact
 * point of the ask.
 */
public record Sheet(Family family, String name, int[] values) {

    public Sheet {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a sheet needs a name — sheets derive from identity");
        }
        if (values.length != family.axisCount()) {
            throw new IllegalArgumentException(family + " carries " + family.axisCount()
                    + " axes, got " + values.length + " values");
        }
        for (int v : values) {
            if (v < 1 || v > 10) {
                throw new IllegalArgumentException("stat out of band 1..10: " + v);
            }
        }
        values = values.clone();
    }

    /** The values array, defensively copied — the sheet inside stays sealed. */
    @Override
    public int[] values() {
        return values.clone();
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
        return values[i];
    }

    /**
     * The one-line form, {@link Locale#ROOT} throughout:
     * {@code Trinity [HUMAN] evasion=9 will=8 faith=7 disbelief=2}.
     */
    public String line() {
        StringBuilder sb = new StringBuilder(64);
        sb.append(name).append(" [").append(family).append(']');
        String[] axes = family.axes();
        for (int i = 0; i < axes.length; i++) {
            sb.append(String.format(Locale.ROOT, " %s=%d", axes[i], values[i]));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return line();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Sheet s && family == s.family && name.equals(s.name)
                && Arrays.equals(values, s.values);
    }

    @Override
    public int hashCode() {
        return (family.hashCode() * 31 + name.hashCode()) * 31 + Arrays.hashCode(values);
    }
}
