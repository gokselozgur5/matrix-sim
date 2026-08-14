import java.util.List;

/**
 * The instrument line families as runtime data (D-020, D-019).
 *
 * The eight shipped families exist in the daemon only as format strings
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
        /**
         * A bare token — uppercase letters and underscores, no quotes, no
         * spaces: BIRTH's {@code family=HUMAN}. Distinct from TEXT because
         * the quotes are the difference a reader keys on: a TEXT value is
         * delimited and may hold anything, a WORD is a name out of a closed
         * set the emitter owns, and swapping one for the other is a retype.
         */
        WORD,
        /** Sixty-four lowercase hex characters. */
        SHA
    }

    /** One column of one family: its name, its shape, what it counts, what it may hold. */
    record Field(String name, Type type, String unit, String domain) {}

    /**
     * One family: its prefix, its columns in order, the arities that are
     * legal today, and the cadence it is emitted at.
     *
     * <p>{@code cadence == 0} means the family is not a sample, so no
     * interval governs it. Two shapes live there: a line printed once at the
     * end of a run (PERF) and a line printed when something happens (BIRTH,
     * zero or many times, at ticks the world chose). Both are cadence 0 for
     * the same reason — there is no period to compare against, and inventing
     * one would make the validator report drift in a family that never
     * promised regularity.
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
     * The eight families, in the order they grew. METRIC's trailing
     * {@code selfsub} is the append the law already survived once (#200);
     * ECO's two arities are the short form the collector emits when a flock
     * cannot be measured (fewer than two birds — the precedent for
     * "undefined is absence"); ZION's two are the same rule for the trace
     * suffix, which rides the line exactly when open pirate links exist AND
     * both populations are measurable, so links>0 alone does not promise it.
     * SUBSTRATE carries no tick: it is the machine wing's own line, read
     * beside the ATTN census it rations.
     *
     * <p>ZION's {@code deferred} (#846, #809) sits after {@code traced} and
     * BEFORE the trace pair, which moved both of them one place right. That
     * is a declared move, not an append, and it is the only shape this
     * registry can hold. The field list is one ordered sequence and every
     * legal arity must be a PREFIX of it — that is what the positional check
     * in {@link LineLint} means by field order. A mandatory column written
     * past the optional rider would be at position 5 on the short line and
     * position 7 on the long one, and no single sequence describes both:
     * the short line then reads as {@code trace_mnn_cm} renamed. The trace
     * pair therefore has no fixed index in this family and never did; it has
     * a fixed suffix position, which is a different promise, and one this
     * registry has no vocabulary for.
     *
     * <p>ZION's {@code treaty} and {@code selfsub} (#831) take the same
     * road for the same reason: they are mandatory columns, so they go at
     * the end of the mandatory block and push the trace pair two places
     * right, and the arities become 8 and 10. They belong beside
     * {@code census}, which they partition, and they are not written there
     * — moving {@code links}, {@code traced} and {@code deferred} one place
     * right to make room is a rename of three shipped columns, and this
     * family's law is that a reader keys on position. The registry records
     * the door vocabulary at the width the emitter had when it was written:
     * {@link matrix.realworld.Origin} generates the columns, so a third
     * door grows the line by one column and this list must grow with it.
     *
     * <p>ZION's {@code living} (#1007) is the third mandatory column and the
     * second arity move on this family, 9 and 11, made the same way for the
     * third time: at the end of the mandatory block, behind the doors,
     * pushing the trace pair one place right. It reads beside {@code census}
     * — the registry counts the fallen by law, this counts who can still
     * crew a hull — and it is not written there, because the sequence is
     * positional and the alternative renames five shipped columns.
     *
     * <p>BIRTH is the eighth and the first that D-020 did not grow: it is
     * D-023's, the stdout echo of the chronos birth record (#553), and it is
     * registered here while it still prints only where a recorder is attached
     * — before the lane flag (#526/#543) widens that gate and a plain enabled
     * run starts printing it. A registry that learns a family only after the
     * family reaches a shipped lane spends the interval calling a correct line
     * an unknown one, which is a red bench row over working code.
     *
     * <p>BIRTH's {@code rack} and {@code id} (#847) are an append at the end,
     * the shape the law calls legal evolution, and the arity moves 3 -> 5
     * rather than gaining a second entry: the echo mirrors the chronos record
     * field for field, and the record has no short form. A stream captured
     * before #847 therefore lints as an arity break, correctly — it is the
     * old grammar, and the registry's job is to say so.
     *
     * <p>{@code rack} is TEXT and not WORD even though {@code R06/U22} needs
     * no quotes: a mind grown with no slot records the empty string, and a
     * bare {@code rack=} would leave the splitter reading the next key as
     * this field's value. The delimiter is what makes "no rack unit" a value
     * a reader can see rather than a hole it has to infer.
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
                    count("deferred"),
                    count("treaty"), count("selfsub"),
                    count("living"),
                    cm("trace_mnn_cm"), cm("red_baseline_cm")),
                    List.of(9, 11), 100),
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
                    new Field("ticks", Type.INT, "ticks", ">=0"),
                    // #825's append: the far-mover ledger's high-water mark
                    // over the run, and the ceiling it is judged against.
                    // Both deterministic — on a line whose first column is
                    // the only thing here that measures the box.
                    count("far_max"), count("far_ceiling")),
                    List.of(3, 5), 0),
            new Family("DIGEST", List.of(
                    TICK,
                    new Field("sha", Type.SHA, "sha256", "64 hex")),
                    List.of(2), 100),
            new Family("BIRTH", List.of(
                    TICK,
                    new Field("name", Type.TEXT, "text", "quoted"),
                    new Field("family", Type.WORD, "text", "A-Z_"),
                    new Field("rack", Type.TEXT, "text", "quoted"),
                    count("id")),
                    List.of(5), 0));

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
