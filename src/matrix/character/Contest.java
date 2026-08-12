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

    // ---- #357: the p-curve — one function, pinned at 0.10 ----

    /**
     * The pin: the probability a contest returns when neither side is ahead —
     * the flat constant the AGENT catch has drawn against since v1,
     * {@code matrix.core.Config.AGENT_KILL_CHANCE}, written here as the same
     * double literal.
     *
     * <p>A literal and NOT an import, deliberately. The package door forbids
     * this layer from importing {@code matrix.core} at all — "no
     * {@code matrix.core}, no rng, no clock" — and that law is worth more
     * than the convenience: a stat layer that reaches into the daemon's
     * tuning table has stopped being a pure function of identity. The
     * equality that matters — this double and the legacy double, bit for bit
     * — is therefore asserted at the BOUNDARY, where the migration happens
     * and both names are already in scope: #467 owns that assertion, and the
     * catch's own adoption in #352 spends it. A pin nobody checks is a wish;
     * a pin checked in the wrong package is a broken door.
     */
    public static final double NEUTRAL_P = 0.10;

    /** No evader is ever safe: the floor a full-spectrum disadvantage cannot get under. */
    public static final double FLOOR_P = 0.01;

    /** No hunter is ever certain: the ceiling a full-spectrum advantage cannot get over. */
    public static final double CEILING_P = 0.50;

    /** The widest margin the vocabularies can produce — values are 1..10. */
    public static final int SPAN = 9;

    /**
     * The contest's probability curve: how likely the A side's attempt lands,
     * given both sides' numbers. Pure, total, and rng-silent — this function
     * decides a THRESHOLD, never a draw. Under D-042's two-die law a migrated
     * site keeps its exact existing draw and lets the sheets move only this
     * number.
     *
     * <p><b>The pin, and why it is bit-exact.</b> p is a function of the
     * MARGIN, not of the absolute values, so it returns the legacy constant at
     * every equal pair — 1v1, 5v5, 10v10 alike — and the pin therefore does
     * not depend on which value the permanent-NEUTRAL flag (#336) picks for a
     * neutral sheet. At margin zero {@code t} is exactly {@code 0.0}, so the
     * endpoint term is multiplied by exactly zero and the neutral term by
     * exactly one: IEEE-754 gives back {@link #NEUTRAL_P} bit for bit, with no
     * branch special-casing the pin. Note that the sign test below is
     * immaterial at zero — both endpoints are annihilated by {@code t == 0.0},
     * so the curve has no seam where its two halves meet.
     *
     * <p><b>The shape.</b> Linear in the margin, anchored at the pin and
     * running to a declared floor below and a declared ceiling above. The
     * two-sided form {@code (1-t)*A + t*B} is used rather than
     * {@code A + (B-A)*t} because only the former is exact at BOTH ends: the
     * naive form lands 5e-18 under {@link #FLOOR_P} at margin -9 and would
     * quietly break the very bound this class advertises. The interior is
     * deliberately the simplest thing that could work — #469 owns re-settling
     * it by measurement, and it may do so without touching the pin, which is
     * this unit's whole claim.
     *
     * <p>Margins beyond {@link #SPAN} clamp rather than extrapolate, so a
     * future vocabulary with a wider band cannot push p out of its bounds.
     */
    public static double p(int attackerStat, int defenderStat) {
        long m = (long) attackerStat - defenderStat;
        double t = Math.min(Math.abs(m), SPAN) / (double) SPAN;
        double end = m >= 0 ? CEILING_P : FLOOR_P;
        return (1.0 - t) * NEUTRAL_P + t * end;
    }

    /**
     * The curve read through two sheets and their named axes — the same
     * grammar {@link #margin} and {@link #resolve} speak, so a cross-family
     * contest (a program's hunt against a human's evasion) gets its
     * probability from the same call shape as its band.
     */
    public static double p(Sheet a, String axisA, Sheet b, String axisB) {
        return p(a.stat(axisA), b.stat(axisB));
    }
}
