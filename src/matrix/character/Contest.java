package matrix.character;

/**
 * One contest law for all four families — the grammar, not the game. A
 * contest is two sheets, one named axis each, and subtraction; the families
 * may differ (that is the point — the rooftop, the overflow, and the
 * Architect's room are all this one function with different words) while
 * each axis must belong to its own sheet's vocabulary
 * ({@link Sheet#stat(String)} enforces the discipline).
 *
 * <p>The one-grammar thesis is the record's and has precedent: the 2005
 * Matrix Online ran fists, guns and hacking through a single opposed check
 * with flavor layered above, and the archaeology's lesson is that tactics
 * belong in the vocabulary layer — modifier tables over one resolver, never
 * four rule sets.
 *
 * <p>Deliberately rng-silent. The kernel takes the shape the Kid's
 * threshold took (pure, stream-neutral); if a later verdict wants opposed
 * rolls, {@code margin} is the single quantity a roll perturbs and nothing
 * else moves. Deterministic, total, boring on purpose.
 *
 * <p>Outcome bands, documented and reachable (values are 1..10, so margin
 * spans -9..+9): a full-spectrum gap is a foregone conclusion, a point or
 * two is a scene worth filming.
 *
 * <pre>
 *   margin &gt;= +4   DECISIVE_A
 *   +1 .. +3       EDGE_A
 *    0             TIE
 *   -1 .. -3       EDGE_B
 *   margin &lt;= -4   DECISIVE_B
 * </pre>
 *
 * <p>The thresholds are asserted, not yet derived — no one has measured how
 * the derived population actually distributes across these five bands, and
 * a vocabulary where most encounters tie is a vocabulary with no drama.
 * That measurement is the band table's own unit and is owed before any
 * domain outcome consults this law.
 */
public final class Contest {

    private Contest() {}

    /** The five bands a margin can land in — symmetric, exhaustive, ordered A-side first. */
    public enum Outcome { DECISIVE_A, EDGE_A, TIE, EDGE_B, DECISIVE_B }

    /**
     * Signed margin: a's value on {@code axisA} minus b's value on
     * {@code axisB}. Positive favors a, negative favors b. Pure arithmetic —
     * no draw, no clock, no state.
     */
    public static int margin(Sheet a, String axisA, Sheet b, String axisB) {
        return a.stat(axisA) - b.stat(axisB);
    }

    /** The margin mapped to its band, thresholds as documented on the class. */
    public static Outcome resolve(Sheet a, String axisA, Sheet b, String axisB) {
        return band(margin(a, axisA, b, axisB));
    }

    /**
     * The band of a margin already in hand — the same law, for a caller who
     * has computed the difference once and wants both halves of the answer
     * without asking twice.
     */
    public static Outcome band(int margin) {
        if (margin >= 4) {
            return Outcome.DECISIVE_A;
        }
        if (margin >= 1) {
            return Outcome.EDGE_A;
        }
        if (margin == 0) {
            return Outcome.TIE;
        }
        if (margin >= -3) {
            return Outcome.EDGE_B;
        }
        return Outcome.DECISIVE_B;
    }
}
