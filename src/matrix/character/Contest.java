package matrix.character;

/**
 * One contest law for all four families — the grammar, not the game. A
 * contest is two sheets, one named axis each, and subtraction; the families
 * may differ (that is the point — the rooftop, the overflow, and the
 * Architect's room are all this one function with different words) while
 * each axis must belong to its own sheet's vocabulary
 * ({@link Sheet#stat(String)} enforces the discipline).
 *
 * <p>Deliberately rng-silent: gate question (c) — opposed rolls from the
 * stream vs threshold-pure — is still open in #212, so this draft takes the
 * shape the Kid took (threshold-pure, stream-neutral). If the verdict wants
 * rolls, the margin is the quantity a roll would perturb; nothing else
 * moves. Deterministic, total, boring on purpose.
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
        int m = margin(a, axisA, b, axisB);
        if (m >= 4) {
            return Outcome.DECISIVE_A;
        }
        if (m >= 1) {
            return Outcome.EDGE_A;
        }
        if (m == 0) {
            return Outcome.TIE;
        }
        if (m >= -3) {
            return Outcome.EDGE_B;
        }
        return Outcome.DECISIVE_B;
    }
}
