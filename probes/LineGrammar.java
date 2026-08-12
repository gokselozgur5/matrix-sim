import java.util.List;

/**
 * The instrument line families as runtime data (D-020, D-019).
 *
 * The seven shipped families exist in the daemon only as format strings
 * scattered across their emitters, and every reader — the bench, the
 * lenses, the census sweeps, CI's greps — re-derives the grammar by eye.
 * The family-wide laws that keep the lines parseable forever (Locale.ROOT,
 * UTF-8, one line one fact, additive-only evolution) are honoured by
 * discipline alone: #200 appended {@code selfsub=N} at the end of METRIC by
 * hand, correctly, because someone knew the law.
 *
 * <p>This registry is that knowledge as data: family, field order, type,
 * unit, domain, legal arities, emission cadence. {@link LineLint} verdicts a
 * live stream against it. It is deliberately NOT the spec document (#255
 * writes that, human-readable, with the MxO grammar-swap lesson as law) and
 * NOT the drift check (#260 compares document to runtime and consumes this
 * registry for its instrument-family clause rather than re-implementing the
 * parse). Whichever lands first, the other two cite it.
 *
 * <p>Field order is the contract. A field appended after the last one is
 * legal evolution; a field renamed, moved or retyped is a breaking change,
 * and that is what makes this data worth having.
 */
final class LineGrammar {

    /** What a field's value must look like. Types are grammar: a retype is a break. */
    enum Type {
        /** A whole number, optionally signed: counts, ticks, centimetres. */
        INT,
        /** A fraction printed {@code %.3f} — three decimals, never scientific, never NaN. */
        RATIO,
        /** A decimal printed {@code %.1f} — one decimal, finite (MetricSnapshot refuses NaN). */
        REAL1,
        /** Two whole numbers joined by a slash: {@code pods=189/196}. */
        PAIR,
        /** A double-quoted string that may hold spaces and commas: ATTN's {@code top=}. */
        TEXT,
        /** Sixty-four lowercase hex characters. */
        SHA
    }

    /** One column of one family: its name, its shape, what it counts, what it may hold. */
    record Field(String name, Type type, String unit, String domain) {}

    /**
     * One family: its prefix, its columns in order, the arities that are
     * legal today, and the cadence it is emitted at ({@code 0} = once per
     * run, at the end).
     */
    record Family(String name, List<Field> fields, List<Integer> arities, int cadence) {

        /** The largest legal arity — anything past it is an appended column, i.e. legal evolution. */
        int maxArity() {
            int max = 0;
            for (int a : arities) {
                max = Math.max(max, a);
            }
            return max;
        }
    }

    private static Field count(String name) {
        return new Field(name, Type.INT, "count", ">=0");
    }

    private static Field cm(String name) {
        return new Field(name, Type.INT, "cm", ">=0");
    }

    private static final Field TICK = new Field("tick", Type.INT, "ticks", ">=0");

    /**
     * The seven families, in the order D-020 grew them. METRIC's trailing
     * {@code selfsub} is the append the law already survived once (#200);
     * ECO's two arities are the short form the collector emits when a flock
     * cannot be measured (fewer than two birds — the precedent for
     * "undefined is absence"); ZION's two are the same rule for the trace
     * suffix, which rides the line exactly when open pirate links exist AND
     * both populations are measurable, so links>0 alone does not promise it.
     * SUBSTRATE carries no tick: it is the machine wing's own line, read
     * beside the ATTN census it rations.
     */
    static final List<Family> FAMILIES = List.of(
            new Family("METRIC", List.of(
                    TICK,
                    count("blue"), count("red"), count("agents"), count("total"),
                    new Field("infected", Type.RATIO, "ratio", "0..1"),
                    new Field("anomaly", Type.REAL1, "residue", "finite"),
                    count("selfsub")),
                    List.of(8), 100),
            new Family("ECO", List.of(
                    TICK,
                    count("birds"),
                    cm("flock_mnn_cm"), cm("random_baseline_cm"),
                    count("insects"), count("flora"), count("mammals"), count("weather")),
                    List.of(2, 8), 100),
            new Family("ZION", List.of(
                    TICK,
                    count("census"), count("fleet"), count("links"), count("traced"),
                    cm("trace_mnn_cm"), cm("red_baseline_cm")),
                    List.of(5, 7), 100),
            new Family("ATTN", List.of(
                    TICK,
                    count("regions"), count("hot"), count("cold"),
                    new Field("top", Type.TEXT, "text", "quoted")),
                    List.of(5), 100),
            new Family("SUBSTRATE", List.of(
                    new Field("pods", Type.PAIR, "count", "n/n"),
                    new Field("budget", Type.INT, "permille", ">=0"),
                    count("slots"), count("stretch"), count("glitches")),
                    List.of(5), 100),
            new Family("PERF", List.of(
                    new Field("ticks_per_s", Type.INT, "rate", ">=0"),
                    count("entities"),
                    new Field("ticks", Type.INT, "ticks", ">=0")),
                    List.of(3), 0),
            new Family("DIGEST", List.of(
                    TICK,
                    new Field("sha", Type.SHA, "sha256", "64 hex")),
                    List.of(2), 100));

    /** The family with this prefix, or null — a null is an unknown family, never a pass. */
    static Family family(String name) {
        for (Family f : FAMILIES) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private LineGrammar() {}
}
