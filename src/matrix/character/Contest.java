package matrix.character;

import java.util.List;

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
 * <p><b>The thresholds were asserted; they are measured now.</b> #835
 * enumerated every ordered pair of distinct souls derivable from the pod
 * farm's own 400-name pool, over all 120 pairs of vocabulary words —
 * 19,152,000 contests, run through {@link #resolve} itself rather than
 * through a copy of its arithmetic. The derived population lands where an
 * independent uniform 1..10 pair would:
 *
 * <pre>
 *   band          measured   uniform 1..10
 *   DECISIVE_A     21.16%       21.00%
 *   EDGE_A         23.85%       24.00%
 *   TIE            10.00%       10.00%
 *   EDGE_B         23.83%       24.00%
 *   DECISIVE_B     21.16%       21.00%
 * </pre>
 *
 * <p>The failure these bands were afraid of — a vocabulary where most
 * encounters tie — is absent: TIE lands 0.002 percentage points off its
 * analytic expectation, inside a 0.225 tolerance derived from the pool
 * size and not from taste. The number the record now has to live with is
 * the other one: <b>42.3% of staged contests are a foregone conclusion</b>
 * under this vocabulary. That is the figure a later drama target has to
 * argue against, and until one is stated, moving ±4 would swap an asserted
 * threshold for another asserted threshold. The edge is not free in any
 * case — {@link #EXCHANGE_BANDS} opens its exotic outcomes at exactly gap
 * 4, deliberately, so the two tables share one law.
 *
 * <p>Reproduce with {@code java -cp out:probes/out SheetBench --bands}.
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

    // ---- #359: the three-outcome bands — hit-hit, hit-miss, miss-miss, as rows ----

    /**
     * What one exchange can come to. The MxO archaeology (#212) found the
     * round resolving to three outcomes rather than a coin's two faces, and
     * reported that "the two extra outcomes are where the film feel lived":
     * both can connect, both can parry.
     *
     * <p>Not to be confused with {@link Outcome}, which bands a MARGIN — how
     * lopsided the matchup is. This enum bands an EXCHANGE — what actually
     * happened when it was tried.
     */
    public enum Exchange { HIT_HIT, HIT_MISS, MISS_MISS }

    /**
     * One row of the band table: at this margin gap, the share of exchanges
     * that end in mutual connection and the share that end in mutual parry.
     * The rest is the decisive share, and the p-curve splits that.
     */
    public record ExchangeBand(int gap, double mutual, double parry) {

        /** The share left for a decisive exchange — what the p-curve gets to split. */
        public double decisive() {
            return 1.0 - mutual - parry;
        }
    }

    /**
     * The band table, as DATA — ten rows, one per margin gap, transcribable
     * verbatim into a {@code docs/spec/} sheet (D-058) and printable by a
     * probe. The archaeology's closing lesson is the reason this is a table
     * and not arithmetic in a resolver: sixteen years on, MxO's world survived
     * because it was data and its combat did not because it was logic inside a
     * closed implementation.
     *
     * <p><b>Why the mutual outcomes open with the GAP.</b> The rows are zero
     * until the gap reaches 4 — precisely where {@link #band(int)} starts
     * saying DECISIVE, so the exchange table and the margin bands share one
     * law rather than inventing a second. That sentence was the whole joint
     * until #988: the class init below reads the edge out of {@code band} and
     * the opening row out of this table, and refuses a tree where the two
     * have stopped agreeing. This is deliberately the opposite of the
     * intuition that evenly matched fighters tangle most: the neutral row
     * MUST be zero-width or the pin is broken, because a zero-width neutral
     * row is exactly what collapses this table back to the legacy two faces.
     * The mutual outcomes are the flourish a real capability gap buys — the
     * hunter so far ahead he lands and still eats the counter, the two so far
     * apart the exchange whiffs entirely.
     */
    public static final List<ExchangeBand> EXCHANGE_BANDS = List.of(
            new ExchangeBand(0, 0.00, 0.00),
            new ExchangeBand(1, 0.00, 0.00),
            new ExchangeBand(2, 0.00, 0.00),
            new ExchangeBand(3, 0.00, 0.00),
            new ExchangeBand(4, 0.02, 0.02),
            new ExchangeBand(5, 0.04, 0.04),
            new ExchangeBand(6, 0.06, 0.06),
            new ExchangeBand(7, 0.08, 0.08),
            new ExchangeBand(8, 0.10, 0.10),
            new ExchangeBand(9, 0.12, 0.12));

    /**
     * Where {@link #band(int)} starts saying DECISIVE — the smallest margin it
     * bands DECISIVE_A. Read out of the function rather than transcribed
     * beside it, so a moved threshold moves this number with it and the class
     * init below stays a check instead of becoming a second copy of the law.
     */
    public static final int DECISIVE_EDGE = decisiveEdge();

    /**
     * Where {@link #EXCHANGE_BANDS} opens its exotic outcomes — the first row
     * that is not the legacy two faces. The other half of the same law, read
     * the same way, out of the table.
     */
    public static final int EXCHANGE_OPENS_AT = exchangeOpensAt();

    static {
        if (EXCHANGE_BANDS.size() != SPAN + 1) {
            throw new AssertionError("the band table must cover every gap 0.." + SPAN);
        }
        if (EXCHANGE_BANDS.get(0).mutual() != 0.0 || EXCHANGE_BANDS.get(0).parry() != 0.0) {
            throw new AssertionError("the neutral row must be zero-width, or the 0.10 pin is broken");
        }
        for (int gap = 0; gap <= SPAN; gap++) {
            ExchangeBand b = EXCHANGE_BANDS.get(gap);
            if (b.gap() != gap) {
                throw new AssertionError("band rows must be ordered by gap, found " + b.gap() + " at " + gap);
            }
            if (b.mutual() < 0.0 || b.parry() < 0.0 || b.mutual() + b.parry() >= 1.0) {
                throw new AssertionError("band row leaves no decisive share: gap=" + gap);
            }
        }
        if (EXCHANGE_OPENS_AT != DECISIVE_EDGE) {
            throw new AssertionError("the exchange table opens at gap " + EXCHANGE_OPENS_AT
                    + " and band() turns DECISIVE at " + DECISIVE_EDGE + " — one law, two tables");
        }
        int asymmetric = firstAsymmetry();
        if (asymmetric != 0) {
            throw new AssertionError("band() reads +" + asymmetric + " as " + band(asymmetric)
                    + " and -" + asymmetric + " as " + band(-asymmetric) + ", and Outcome says symmetric");
        }
    }

    /** The smallest margin {@link #band(int)} calls DECISIVE_A, or one past {@link #SPAN} if none is. */
    private static int decisiveEdge() {
        for (int margin = 0; margin <= SPAN; margin++) {
            if (band(margin) == Outcome.DECISIVE_A) {
                return margin;
            }
        }
        return SPAN + 1;
    }

    /** The first gap whose row opens a mutual outcome, or one past {@link #SPAN} if none does. */
    private static int exchangeOpensAt() {
        for (ExchangeBand row : EXCHANGE_BANDS) {
            if (row.mutual() > 0.0 || row.parry() > 0.0) {
                return row.gap();
            }
        }
        return SPAN + 1;
    }

    /**
     * The first margin at which {@link #band(int)} does not read the same on
     * both sides, or 0 when it never does. {@link Outcome} is declared
     * symmetric and ordered A-side first, which makes the mirror of a band its
     * reversed ordinal — so the enum's own sentence is the test, checked here
     * rather than quoted. Public because the bench prints the answer: one
     * derivation, two readers, no second copy of it in a probe.
     */
    public static int firstAsymmetry() {
        Outcome[] outcomes = Outcome.values();
        for (int margin = 1; margin <= SPAN; margin++) {
            if (band(-margin) != outcomes[outcomes.length - 1 - band(margin).ordinal()]) {
                return margin;
            }
        }
        return 0;
    }

    /** The row governing this matchup — the table read by the magnitude of the margin. */
    public static ExchangeBand exchangeBand(int attackerStat, int defenderStat) {
        long gap = Math.abs((long) attackerStat - defenderStat);
        return EXCHANGE_BANDS.get((int) Math.min(gap, SPAN));
    }

    /** One exchange's result: which band it landed in, and who actually connected. */
    public record Round(Exchange exchange, boolean attackerConnects, boolean defenderConnects) {}

    /**
     * Resolve one exchange from ONE roll in {@code [0, 1)}. The roll is a
     * parameter, never a draw taken here: under the two-die law a migrated
     * site keeps its own draw, and this function must be usable by a site that
     * already spent it. Nothing in the world calls this yet — the AGENT catch
     * adopts it in #352.
     *
     * <p>The unit interval is partitioned decisive-first, and that order is
     * load-bearing. At the neutral row {@code mutual} and {@code parry} are
     * exactly zero, so {@code decisive} is exactly {@code 1.0} and the first
     * cut sits at exactly {@code p} — which makes {@code roll < cut} the same
     * comparison, against the same number, that {@code Rng.chance(p)} makes.
     * The legacy two faces are reproduced bit for bit, from one draw, and the
     * two exotic outcomes are unreachable because every roll is under 1.0.
     * Put the exotic slices first and that exactness is gone.
     *
     * <p><b>Known and measured: once the exotic outcomes open, {@code p} is
     * the decisive split, not the marginal connect rate.</b> The attacker
     * connects on HIT_MISS-for-A <i>or</i> HIT_HIT, which totals
     * {@code decisive*p + mutual} — equal to {@code p} exactly on gaps 0..3
     * (the pin's zone, where {@code mutual} is zero) and again at gap 9 by
     * the table's symmetry, but above it in between:
     *
     * <pre>
     *   gap  p         P(attacker connects)  delta
     *   0-3  =p        =p                    +0.000000
     *   4    0.277778  0.286667              +0.008889
     *   5    0.322222  0.336444              +0.014222
     *   6    0.366667  0.382667              +0.016000
     *   7    0.411111  0.425333              +0.014222
     *   8    0.455556  0.464444              +0.008889
     *   9    0.500000  0.500000              +0.000000
     * </pre>
     *
     * The bias is bounded by 0.016 and never touches the pin, so the
     * equivalence proof is unaffected. It is disclosed here rather than
     * silently absorbed because a reader will otherwise assume {@code p} is
     * the kill rate: whether the catch's observed lethality must equal
     * {@code p} on the enabled lane is #352's call at adoption, and
     * re-deriving the mutual slice proportionally from both sides is the fix
     * if it must.
     */
    public static Round round(double roll, int attackerStat, int defenderStat) {
        ExchangeBand band = exchangeBand(attackerStat, defenderStat);
        double decisive = band.decisive();
        if (roll < decisive * p(attackerStat, defenderStat)) {
            return new Round(Exchange.HIT_MISS, true, false);
        }
        if (roll < decisive) {
            return new Round(Exchange.HIT_MISS, false, true);
        }
        if (roll < decisive + band.mutual()) {
            return new Round(Exchange.HIT_HIT, true, true);
        }
        return new Round(Exchange.MISS_MISS, false, false);
    }
}
