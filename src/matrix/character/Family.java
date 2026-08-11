package matrix.character;

/**
 * The four families of being, each carrying its own stat vocabulary — the
 * D-042 proposal (gate #212) made concrete. One contest grammar is shared
 * by all; the WORDS differ per family, because every iconic scene is a
 * cross-family contest and the words are what make it legible: a human's
 * evasion against a program's privilege, a human's will against a system's
 * authority, a system's tolerance against a program's replication.
 *
 * <p>Axis names follow the D-042 record verbatim (the record spells one of
 * them {@code version-fatigue}; code bends it to {@code versionFatigue}).
 * The ORDER of axes is canonical and load-bearing: derivation salts each
 * axis by its index, so reordering a vocabulary re-rolls every sheet in
 * that family. Append if the verdict grows a vocabulary; never reorder.
 *
 * <p>Vocabulary discipline is the review axis this design buys with its
 * flexibility: a human never grows {@code replication}. The enum is where
 * that law lives — {@link Sheet#stat(String)} refuses any axis outside its
 * family's list.
 */
public enum Family {

    /** Minds in pods: what they dodge, what they insist on, what they believe, what they refuse to. */
    HUMAN("evasion", "will", "faith", "disbelief"),

    /** The hardware side of the war: sentinels, harvesters, the city itself in motion. */
    MACHINE("power", "precision", "relentlessness"),

    /** Running platforms — the Matrix ITSELF gets a sheet; reload becomes a tired system's character decision. */
    SYSTEM("stability", "tolerance", "authority", "versionFatigue"),

    /** Software with a purpose: agents, exiles, oracles — and one licensed anomaly (D-014). */
    PROGRAM("purposeIntegrity", "privilege", "replication");

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
