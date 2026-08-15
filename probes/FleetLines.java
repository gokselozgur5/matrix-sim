import matrix.zion.Zion;

/**
 * Probe: does a laydown line tell the truth about the fleet? (#1056)
 *
 * No universe at all. {@link HullRoster} holds the precedent and the
 * division of labour: it pins what a hull is CALLED over three thousand
 * ordinals, and nothing pinned what the line announcing that hull CLAIMS.
 * So one defect reached main twice — #806 printed "a second hull" over the
 * third keel, #948 narrated a loss into a fleet that had lost nothing, and
 * #1056 was the same lie left in the ordinal-1 arm after #948 fixed the
 * general one. Every round was found by a human reading the log. This row
 * is the reader.
 *
 * <p>The name handed in is a placeholder, deliberately: the film lives in
 * {@code HullRoster} and a second copy of it here would mean one rename
 * turning two rows red for one fact. What is pinned here is the sentence
 * around the name. The three film lines are printed as the city composes
 * them, REPORTED and not judged, so the bench shows the sentences it is
 * guarding.
 *
 * <p>Five facts, over ordinals 0..N-1 and both values of {@code replacing}:
 *
 *   PIN     the five arms the shipped grammar can reach, as literals.
 *   ORDINAL the head names the laydown ordinal — #806's half, still
 *           unlocked by anything else. The ordinal counts every keel ever
 *           laid, so it stays right across a sinking.
 *   NAME    the line carries the name it was handed, whole.
 *   CLAUSE  what follows the em dash is one of four declared sentences. A
 *           fifth is not a passing line: an undeclared claim is exactly the
 *           thing that has gone wrong here three times, and adding an arm
 *           should cost the author a look at the rules below.
 *   LOSS    the clause says a loss was replaced if and only if
 *           {@code replacing}. This is the row #1056 turns red: at ordinal
 *           1 with a sunk hull behind it, the line narrated no loss.
 *   COUNT   a clause that names a board count is printed only where the
 *           fleet HAS that count — "two boards" only where two are manned,
 *           "another board" only where there is another, "learns to fly"
 *           only over the fleet that could not fly before. This is #1056
 *           from the other side: one board was manned and the line said two.
 *
 * <p>{@code replacing} is not free over the ordinals, so the walk does not
 * pretend it is. It is {@code laydown > afloat()}, {@code laydown} is
 * {@code fleet.size()}, and no hull still afloat is missing from that list —
 * so ordinal 0 is an empty fleet, an empty fleet has lost nothing, and
 * {@code (0, true)} is a sentence the city can never print. Pinning it would
 * be pinning fiction. Every other ordinal is walked both ways.
 *
 * <p>A range too short to reach all three arms is refused at the door rather
 * than verdicted over — the vacuous green of #898.
 *
 * Usage: java -cp out:probes/out FleetLines [ordinals]
 */
public final class FleetLines {

    /** Not a hull name: the film is HullRoster's row, the sentence is this one's. */
    private static final String HULL = "HULL";

    /** The spine every laydown line is built on, between the name and the claim. */
    private static final String JOIN = " joins the fleet — ";

    /** The declared claims. A clause outside this table is an undeclared claim. */
    private static final String LEARNS = "the census learns to fly";
    private static final String TWO_BOARDS = "the census can man two boards";
    private static final String ANOTHER_BOARD = "the census can man another board";
    private static final String REPLACES = "the census replaces what it lost";
    private static final String[] VOCABULARY = {LEARNS, TWO_BOARDS, ANOTHER_BOARD, REPLACES};

    private static int anomalies = 0;
    private static final boolean[] EXERCISED = new boolean[VOCABULARY.length];

    public static void main(String[] args) {
        matrix.Streams.utf8();
        int ordinals = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        if (ordinals < 3) {
            System.out.println("FLEETLINES refused: ordinals=" + ordinals
                    + " does not reach all three laydown arms");
            System.exit(Probes.Outcome.REFUSED.code());
        }

        // PIN — the five reachable arms, as literals. The heads are written
        // here rather than derived, because "a second hull" is the ordinal
        // and the ordinal is what #806 got wrong.
        pin(0, false, "the first hull: " + HULL + JOIN + LEARNS);
        pin(1, false, "a second hull: " + HULL + JOIN + TWO_BOARDS);
        pin(1, true, "a second hull: " + HULL + JOIN + REPLACES);
        pin(2, false, "hull number 3: " + HULL + JOIN + ANOTHER_BOARD);
        pin(2, true, "hull number 3: " + HULL + JOIN + REPLACES);

        int lines = 0;
        for (int laydown = 0; laydown < ordinals; laydown++) {
            int arms = laydown == 0 ? 1 : 2;
            for (int arm = 0; arm < arms; arm++) {
                check(laydown, arm == 1);
                lines++;
            }
        }

        int exercised = 0;
        StringBuilder unused = new StringBuilder();
        for (int i = 0; i < VOCABULARY.length; i++) {
            if (EXERCISED[i]) {
                exercised++;
            } else {
                unused.append(unused.length() > 0 ? "," : "").append('"').append(VOCABULARY[i]).append('"');
            }
        }
        System.out.println("CLAUSES declared=" + VOCABULARY.length + " exercised=" + exercised
                + " unused=" + (unused.length() > 0 ? unused : "-"));
        // The sentences a city at the SHIPPED FLEET_MAX = 2 can actually
        // print, under the names it prints them with. Reported, never judged:
        // the names are HullRoster's row. A third keel there only ever
        // follows a loss, so its growth arm needs a tuned floor and is not
        // film — the arms below are the four a canonical box can witness.
        film(0, false);
        film(1, false);
        film(1, true);
        film(2, true);
        System.out.println("SWEEP ordinals=" + ordinals + " lines=" + lines
                + " anomalies=" + anomalies);
        System.out.println(anomalies == 0 ? "VERDICT FLEET_LINES_TRUE" : "VERDICT FLEET_LINES_LIE");
    }

    /** One arm of the film, composed the way the city composes it. */
    private static void film(int laydown, boolean replacing) {
        System.out.println("FILM " + laydown + " replacing=" + replacing + " \""
                + Zion.laydownLine(laydown, Zion.hullName(laydown), replacing) + "\"");
    }

    /** One pinned arm: the sentence the city must compose, as a literal. */
    private static void pin(int laydown, boolean replacing, String want) {
        String got = Zion.laydownLine(laydown, HULL, replacing);
        boolean held = want.equals(got);
        if (!held) {
            anomalies++;
        }
        System.out.println("PIN " + laydown + " replacing=" + replacing
                + " line=\"" + got + "\" pinned=" + held);
    }

    /** One walked arm, against the rules rather than against a literal. */
    private static void check(int laydown, boolean replacing) {
        String line = Zion.laydownLine(laydown, HULL, replacing);
        int colon = line.indexOf(": ");
        int join = line.indexOf(JOIN);
        if (colon < 0 || join < colon) {
            fault("SHAPE", laydown, replacing, line, "not_a_laydown_line");
            return;
        }
        String head = line.substring(0, colon);
        String named = line.substring(colon + 2, join);
        String clause = line.substring(join + JOIN.length());

        String wantHead = laydown == 0 ? "the first hull"
                : laydown == 1 ? "a second hull"
                : "hull number " + (laydown + 1);
        if (!head.equals(wantHead)) {
            fault("ORDINAL", laydown, replacing, line, "head=\"" + head + "\" want=\"" + wantHead + "\"");
        }
        if (!named.equals(HULL)) {
            fault("NAME", laydown, replacing, line, "named=\"" + named + "\"");
        }
        int declared = -1;
        for (int i = 0; i < VOCABULARY.length; i++) {
            if (VOCABULARY[i].equals(clause)) {
                declared = i;
                EXERCISED[i] = true;
            }
        }
        if (declared < 0) {
            // The remaining rules judge WHICH declared clause was chosen, and
            // an undeclared one has no entry in either of them. Reporting it
            // twice more would say nothing the first line did not.
            fault("CLAUSE", laydown, replacing, line, "undeclared");
            return;
        }
        if (clause.equals(REPLACES) != replacing) {
            fault("LOSS", laydown, replacing, line,
                    replacing ? "a loss went unnarrated" : "a loss was invented");
        }
        String miscount = miscount(clause, laydown, replacing);
        if (miscount != null) {
            fault("COUNT", laydown, replacing, line, miscount);
        }
    }

    /**
     * Where a counting clause is allowed to appear, or null when it holds.
     * Every board a clause names has to be a board the census can man at that
     * laydown: the fleet is {@code laydown} keels with the losses still on
     * the list, and {@code replacing} says one of them is gone.
     */
    private static String miscount(String clause, int laydown, boolean replacing) {
        if (clause.equals(LEARNS)) {
            return laydown == 0 ? null : "the fleet was already flying";
        }
        if (clause.equals(TWO_BOARDS)) {
            return laydown == 1 && !replacing ? null : "two boards claimed, one manned";
        }
        if (clause.equals(ANOTHER_BOARD)) {
            return laydown >= 2 && !replacing ? null : "another board claimed, none added";
        }
        return null;
    }

    private static void fault(String rule, int laydown, boolean replacing, String line, String why) {
        anomalies++;
        System.out.println(rule + " ordinal=" + laydown + " replacing=" + replacing
                + " line=\"" + line + "\" fault=" + why);
    }

    private FleetLines() {}
}
