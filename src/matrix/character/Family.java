package matrix.character;

/**
 * The four families of being, each carrying its own stat vocabulary — D-042
 * as law (gate #212, accepted 2026-08-12). One contest grammar is shared by
 * all; the WORDS differ per family, because every iconic scene is a
 * cross-family contest and the words are what make it legible: a human's
 * evasion against a program's pursuit, a human's will against a system's
 * authority, a system's tolerance against a program's replication.
 *
 * <p>Axis names follow the record verbatim (the record spells one of them
 * {@code version-fatigue}; code bends it to {@code versionFatigue}), plus
 * the two edits the gate thread tabled and the verdict carried:
 * <ul>
 *   <li><b>{@code pursuit}</b> appends to PROGRAM. Hunting is THE agent
 *       verb and the vocabulary had no word for it — the parked kernel had
 *       to borrow {@code privilege} to stage the rooftop. Borrowing it
 *       would have merged two different mechanisms under one number:
 *       {@code privilege} is INSTITUTIONAL (rank bypasses validation — an
 *       agent carries no disbelief, it carries permission), while the hunt
 *       is a capability that a runner can out-run. Two mechanisms, two
 *       axes. The hunt is named once, at {@link #HUNT_AXIS}, and every
 *       reader asks for it by that name rather than holding a literal.</li>
 *   <li><b>{@code integrity}</b> appends to HUMAN: how tightly a mind holds
 *       its own self-image, the feeder of the disbelief DEFENCE projection
 *       (rejecting the world's frame about you is the cheap projection, and
 *       integrity is what pays for it). Take the mechanic, leave the
 *       mirror — how a mind LOOKS stays out of scope under D-019, and no
 *       such concept enters this package under any spelling. Integrity is
 *       how hard the self-image is held, never what it depicts.</li>
 * </ul>
 *
 * <p>The ORDER of axes is canonical and load-bearing: derivation salts each
 * axis by its index, so reordering a vocabulary re-rolls every sheet in
 * that family. Append if a verdict grows a vocabulary; never reorder. Both
 * edits above are therefore appends, at the end of their lists.
 *
 * <p>Vocabulary discipline is the review axis this design buys with its
 * flexibility: a human never grows {@code replication}. The enum is where
 * that law lives — {@link Sheet#stat(String)} refuses any axis outside its
 * family's list.
 *
 * <p>Capitalization is identity, here and everywhere downstream: a name is
 * mixed as bytes, so {@code the Architect} and {@code The Architect} are
 * two different souls. The canonical spellings are the ones the record
 * uses; a roster that feeds this package quotes them exactly.
 */
public enum Family {

    /**
     * Minds in pods: what they dodge, what they insist on, what they
     * believe, what they refuse to, and how tightly they hold themselves.
     */
    HUMAN("evasion", "will", "faith", "disbelief", "integrity"),

    /** The hardware side of the war: sentinels, harvesters, the city itself in motion. */
    MACHINE("power", "precision", "relentlessness"),

    /** Running platforms — the Matrix ITSELF gets a sheet; reload becomes a tired system's character decision. */
    SYSTEM("stability", "tolerance", "authority", "versionFatigue"),

    /** Software with a purpose: agents, exiles, oracles — and one licensed anomaly (D-014). */
    PROGRAM("purposeIntegrity", "privilege", "replication", "pursuit");

    /**
     * The hunt, named in exactly one place. Every downstream reader — the
     * agent catch above all — asks {@code sheet.stat(Family.HUNT_AXIS)}
     * rather than holding the string, so the day a verdict renames the
     * hunt there is one edit and no silent survivor.
     */
    public static final String HUNT_AXIS = "pursuit";

    /**
     * The one axis in the whole grammar that is READ rather than derived.
     *
     * <p>Every other value on every sheet is a pure function of a name — a
     * christening rolls no dice. Fatigue is the exception, and it is an
     * exception on purpose: the Matrix has lived through its reloads, the
     * count of them has been sitting in the digest since v1, and deriving a
     * number from the string {@code "the Matrix"} to describe how tired the
     * system is would be inventing a fact the world already knows (#661).
     *
     * <p>Named here for the same reason {@link #HUNT_AXIS} is: the reader
     * that fills it asks for it by name, so a verdict that renames the axis
     * is one edit rather than a silent survivor.
     */
    public static final String FATIGUE_AXIS = "versionFatigue";

    static {
        // The constants and the vocabulary cannot drift: a typo here is a
        // class-init failure, not a mystery at the catch site.
        if (PROGRAM.axisIndex(HUNT_AXIS) < 0) {
            throw new IllegalStateException("HUNT_AXIS '" + HUNT_AXIS + "' is not in the PROGRAM vocabulary");
        }
        if (SYSTEM.axisIndex(FATIGUE_AXIS) < 0) {
            throw new IllegalStateException("FATIGUE_AXIS '" + FATIGUE_AXIS + "' is not in the SYSTEM vocabulary");
        }
    }

    private final String[] axes;

    Family(String... axes) {
        this.axes = axes;
    }

    /** This family's axis names, in canonical order (defensive copy). */
    public String[] axes() {
        return axes.clone();
    }

    /** How many axes this family's vocabulary carries. */
    public int axisCount() {
        return axes.length;
    }

    /**
     * Canonical index of {@code axis} in this family's vocabulary, or -1 if
     * the word does not belong here. The -1 is deliberate: this is a lookup,
     * not a verdict — {@link Sheet#stat(String)} owns the refusal.
     */
    public int axisIndex(String axis) {
        for (int i = 0; i < axes.length; i++) {
            if (axes[i].equals(axis)) {
                return i;
            }
        }
        return -1;
    }
}
