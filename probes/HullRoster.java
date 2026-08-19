import matrix.zion.Zion;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Probe: is the roster rule total, and is it still the film? (#832)
 *
 * No universe at all. #806 crashed because a fixed-length name table was
 * indexed by a list with no ceiling, and #855 answered it with a rule —
 * through the roster the name IS the roster in boot order, past it the keel
 * is laid again under a greedy-Roman generation mark — stated in javadoc and
 * held by nothing. {@code Zion.hullName} was made public static so this row
 * could exist; this is that row. PirateSever holds the precedent for a probe
 * smaller than a Simulation: the contract's own-universe clause is about not
 * touching shared state, and a pure function has none to touch.
 *
 * Four facts, over ordinals 0..N-1:
 *
 *   BOOT   the first ROSTER.length ordinals are byte-identical to the film,
 *          pinned HERE as literals rather than read out of the table they
 *          check. The digest walk is world + realWorld + bonds and nothing
 *          in matrix.zion frames into it, so a renamed hull moves no lock in
 *          this repository — these three strings are the only thing holding
 *          the film in place.
 *   GEN    the fourth, fifth and sixth keels are the Nebuchadnezzar II, the
 *          Logos II and the Hammer II — #855's rule as strings, so "past the
 *          roster the name is re-issued with a mark" is a fact and not a
 *          paraphrase of a modulo.
 *   UNIQUE no two ordinals wear the same string. The mark is what makes a
 *          re-issued keel a different hull, so a collision is not a cosmetic
 *          clash — it is the rule failing, and a log line that no longer
 *          reads back to exactly one ordinal.
 *   SHAPE  every name is non-empty, printable ASCII, does not start or end
 *          in a space, and its mark parses as a descending run of the sign
 *          table. The mark is locale-free by construction; this keeps it so.
 *
 * The sweep is bounded, so UNIQUE is injectivity over the ordinals walked
 * and not a proof over int. 3,000 ordinals reach generation 1,000, which is
 * where every one of the thirteen signs is first emitted; MARKS reports that
 * coverage rather than judging it, because it is a fact about the range the
 * caller asked for. A range shorter than the six pinned keels is refused at
 * the door rather than verdicted over: a green line printed across an empty
 * observation set is the vacuous pass #898 is about.
 *
 * Usage: java -cp out:probes/out HullRoster [ordinals]
 */
public final class HullRoster {

    /** The film, as literals. A table cannot vouch for itself. */
    private static final String[] FILM = {"the Nebuchadnezzar", "the Logos", "the Hammer"};

    /** The first generation past the roster, ordinal by ordinal (#855's stated rule). */
    private static final String[] REISSUED = {
            "the Nebuchadnezzar II", "the Logos II", "the Hammer II"};

    private static int anomalies = 0;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        int ordinals = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        // A range too short to reach the pinned keels cannot judge them, and a
        // verdict printed over an empty observation set is the vacuous green
        // #898 is about. Refuse at the door instead: no VERDICT line, nonzero
        // exit, which is a FAIL in the bench rather than a silent pass.
        int pinned = FILM.length + REISSUED.length;
        if (ordinals < pinned) {
            System.out.println("HULLROSTER refused: ordinals=" + ordinals
                    + " does not reach the " + pinned + " pinned keels");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        String[] roster = table("ROSTER");
        String[] signs = table("MARK_SIGNS");

        // BOOT — the film must not move. A length change is its own fault
        // line: one more name in the table renumbers every generation past
        // it, so every hull from the fourth on is renamed by an edit that
        // looks purely additive.
        if (roster.length != FILM.length) {
            anomalies++;
            System.out.println("BOOT roster=" + roster.length + " pinned=" + FILM.length
                    + " fault=table_resized");
        }
        for (int i = 0; i < FILM.length; i++) {
            pin("BOOT", i, FILM[i]);
        }
        // GEN — the re-issue, at the ordinals where the array used to refuse.
        for (int i = 0; i < REISSUED.length; i++) {
            pin("GEN", FILM.length + i, REISSUED[i]);
        }

        // UNIQUE and SHAPE, in one walk. First-seen ordinal per name, so a
        // collision names both sides; the map is never iterated, so the
        // output is ordinal-ordered and byte-stable across runs.
        Map<String, Integer> seen = new HashMap<>();
        boolean[] used = new boolean[signs.length];
        int collisions = 0;
        int shapeFaults = 0;
        for (int i = 0; i < ordinals; i++) {
            String name = Zion.hullName(i);
            Integer first = seen.putIfAbsent(name, i);
            if (first != null) {
                collisions++;
                anomalies++;
                System.out.println("COLLISION ordinal=" + i + " first=" + first
                        + " name=\"" + name + "\"");
            }
            String fault = shape(name, roster[i % roster.length], signs, used);
            if (fault != null) {
                shapeFaults++;
                anomalies++;
                System.out.println("SHAPE ordinal=" + i + " name=\"" + name
                        + "\" fault=" + fault);
            }
        }

        int exercised = 0;
        StringBuilder unused = new StringBuilder();
        for (int i = 0; i < signs.length; i++) {
            if (used[i]) {
                exercised++;
            } else {
                unused.append(unused.length() > 0 ? "," : "").append(signs[i]);
            }
        }
        int topGeneration = 1 + (ordinals - 1) / roster.length;
        System.out.println("MARKS signs=" + signs.length + " exercised=" + exercised
                + " unused=" + (unused.length() > 0 ? unused : "-")
                + " generations=1.." + topGeneration);
        System.out.println("SWEEP ordinals=" + ordinals + " distinct=" + seen.size()
                + " collisions=" + collisions + " shape_faults=" + shapeFaults);
        System.out.println("HULLROSTER roster=" + roster.length + " ordinals=" + ordinals);
        // `anomalies=` AND `swept_none=` RIDE THE VERDICT SINCE #1645, and nothing
        // else does. The row greped one word over NINE printed fields.
        //
        // #1645 asked whether `collisions=` and `shape_faults=` belong beside
        // `anomalies=`, since both are findings rather than populations and both pass
        // the rule's first clause on their face. THE ANSWER IS NO, and it is decidable
        // by reading four lines rather than by taste: every `collisions++` and every
        // `shapeFaults++` is followed by `anomalies++`, so `anomalies=0` already says
        // `collisions=0 shape_faults=0` and two more. Pinning them is the RESTATEMENT
        // the rule's fourth clause forbids — a second copy of one claim is not a
        // second lock — and the pinned line stays one field wide because the
        // subsumption is real, not because narrower is tidier.
        //
        // `swept_none=` is the guard: `anomalies=` counts over a sweep of `ordinals`
        // hulls and nothing under it refused an empty one (#970, #1207).
        boolean clean = anomalies == 0;
        Probes.leave((clean ? "VERDICT ROSTER_TOTAL" : "VERDICT ROSTER_BROKEN")
                + " anomalies=" + anomalies
                + " swept_none=" + (seen.isEmpty() ? 1 : 0),
                clean && !seen.isEmpty());    }

    /** One pinned ordinal: the name the rule must produce, as a literal. */
    private static void pin(String kind, int ordinal, String want) {
        String got = Zion.hullName(ordinal);
        boolean held = want.equals(got);
        if (!held) {
            anomalies++;
        }
        System.out.println(kind + " " + ordinal + " name=\"" + got + "\" pinned=" + held);
    }

    /**
     * The shape of one name, or null when it holds. Past the roster the name
     * is the boot name, a space, and the mark; the mark is walked against the
     * sign table in table order, consuming repeats — which is exactly the
     * form the greedy emitter produces, so a mark that fails to parse is one
     * the emitter could not have written. Signs the walk consumes are marked
     * exercised, which is how the coverage line knows what the range reached.
     */
    private static String shape(String name, String boot, String[] signs, boolean[] used) {
        if (name.isEmpty()) {
            return "empty";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return "non_ascii";
            }
        }
        if (name.startsWith(" ") || name.endsWith(" ")) {
            return "edge_space";
        }
        if (name.equals(boot)) {
            return null;
        }
        if (!name.startsWith(boot + " ")) {
            return "boot_name_lost";
        }
        String mark = name.substring(boot.length() + 1);
        int at = 0;
        for (int s = 0; s < signs.length && at < mark.length(); s++) {
            while (mark.startsWith(signs[s], at)) {
                used[s] = true;
                at += signs[s].length();
            }
        }
        return at == mark.length() ? null : "mark_unparseable";
    }

    /** Zion's private name tables — the coroner's privilege, read-only. */
    private static String[] table(String field) throws Exception {
        Field f = Zion.class.getDeclaredField(field);
        f.setAccessible(true);
        return (String[]) f.get(null);
    }

    private HullRoster() {}
}
