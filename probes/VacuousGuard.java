import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: can a judged row pass over an EMPTY population?
 *
 * <p>A verdict with no denominator cannot tell <em>the contract held over
 * everything</em> from <em>the contract held over nothing</em>. This tree has
 * invented a counter for that five separate times — {@code checked_none=},
 * {@code swept_none=}, {@code scanned_none=}, {@code door_missing=},
 * {@code stale_none=} — each after a probe printed a passing line over an empty
 * set (#970's {@code INSTRUMENTS_UNPROVEN}, #1207's silent skip). #1373 counted
 * twenty-five judged rows with no such guard.
 *
 * <h2>Twenty-five was fourteen, and that is why this class exists</h2>
 *
 * #1373's count was true when it was taken. It missed a SECOND guard the tree
 * uses just as often: a probe that leaves with {@code Outcome.NEVER_AROSE} on an
 * empty population prints a DIFFERENT verdict word, so the pinned row goes red
 * without any number on the line at all.
 *
 * <pre>
 * SameTick        census == 0  ->  VERDICT NO_LIBERATIONS   (NEVER_AROSE)
 * BondScenario                 ->  VERDICT NOT_DEMONSTRATED (NEVER_AROSE)
 * ClauseAftermath              ->  VERDICT NO_FIRING        (NEVER_AROSE)
 * </pre>
 *
 * All three are on #1373's list of the dangerous group, and all three are
 * guarded. Five more already carry a {@code _none=} field. The population is
 * fourteen — and nothing in the tree could have said so, because the figure had
 * no producer to re-run (#1082). That is the finding this probe answers: not the
 * fourteen, but the fact that twenty-five rotted invisibly.
 *
 * <h2>The population is the table</h2>
 *
 * The bench's own {@code judge}/{@code known} rows are read as text, never
 * re-listed here — {@code LeaveContract}'s rule, for {@code LeaveContract}'s
 * reason: a list kept beside the table is a second copy of the bench's contract,
 * and the second copy is the one that goes stale (#1192).
 *
 * <h2>A census, not a gate</h2>
 *
 * {@code unguarded=} rides the verdict as a NUMBER and the verdict word is about
 * the READ — {@code VACUOUS_GUARD_COUNTED} — not about that number being zero.
 * Converting fourteen probes is fourteen judgements about what each one's
 * denominator IS, and a gate that demands them in the unit which lands the
 * reader is a gate that gets exempted in the unit after it. This is
 * {@code unfalsifiable=}'s path: reported by #1095, judged by #1311, once the
 * population it names had actually been worked down.
 *
 * <h2>What it cannot see</h2>
 *
 * The {@code NEVER_AROSE} test is textual and therefore generous: it asks
 * whether the constant is reachable in the file, not whether it is reachable on
 * the EMPTY path. A probe spending {@code NEVER_AROSE} for an unrelated reason
 * reads as guarded. That error's direction is the safe one for a census — it
 * UNDERSTATES the problem and can never invent one — and it is the wrong one for
 * a gate, which is the thing to fix before {@code unguarded=} becomes a break.
 *
 * <p>Comments are stripped before the constant is looked for (#1531), because a
 * {@code NEVER_AROSE} discussed in a javadoc is not a guard — the exact mistake
 * that let {@code CensusBeatDrift} print a failing verdict at exit 0 while
 * {@code LeaveContract} called it a style preference.
 *
 * <p>Usage: {@code java -cp out:probes/out VacuousGuard [repo-root]}
 */
public final class VacuousGuard {

    /** A row's verb, class and pinned verdict: `  judge SameTick 'VERDICT SAME_TICK_ABSORB' 6000`. */
    private static final Pattern ROW =
            Pattern.compile("^\\s+(judge|known)\\s+(\\w+)\\s+'([^']*)'");

    /** The bench's own exemption grammar, read rather than re-listed. */
    private static final Pattern VARY = Pattern.compile("^\\s+vary\\b");

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].startsWith("--")) {
            System.err.println("FATAL unknown flag: " + args[0] + " (this probe takes [repo-root])");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        Path bench = root.resolve("probes/bench.sh");
        if (!Files.isReadable(bench)) {
            Probes.leave("VERDICT VACUOUS_GUARD_NO_BENCH " + bench, false);
        }

        Set<String> rows = new LinkedHashSet<>();
        List<String> byField = new ArrayList<>();
        List<String> byWord = new ArrayList<>();
        List<String> unguarded = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // ONE ENTRY PER PROBE, AND EVERY ROW READ. A probe with two judged rows —
        // `SheetDump` has, and `LeaveContract` has since #1531 — is one subject,
        // and a first-row-wins rule reads whichever mode happens to sit higher in
        // the table: `NeutralDiff`'s selfcheck row is above its real one, so the
        // first draft of this loop called a guarded probe unguarded. A probe is
        // guarded if ANY of its rows carries the field.
        java.util.Map<String, Boolean> fielded = new java.util.LinkedHashMap<>();
        for (String line : Files.readAllLines(bench, StandardCharsets.UTF_8)) {
            if (VARY.matcher(line).find()) {
                continue;
            }
            Matcher m = ROW.matcher(line);
            if (!m.find()) {
                continue;
            }
            String probe = m.group(2);
            // The first guard, and the one #1373 counted: a field on the pinned
            // line whose value is the size of what was read. `_none=0` is the
            // spelling this tree settled on — the row cannot pass if the set was
            // empty, because the field would read 1.
            boolean has = m.group(3).matches(".*\\b\\w+_none=\\d+.*");
            fielded.merge(probe, has, (a, b) -> a || b);
        }

        for (java.util.Map.Entry<String, Boolean> e : fielded.entrySet()) {
            String probe = e.getKey();
            rows.add(probe);
            if (e.getValue()) {
                byField.add(probe);
                continue;
            }
            Path src = root.resolve("probes/" + probe + ".java");
            if (!Files.isReadable(src)) {
                // `roster_check`'s question, not this one — but a check that
                // walked past it would report a clean count over a probe it
                // never opened.
                missing.add(probe);
                continue;
            }
            // The second guard, which #1373 did not count: an empty population
            // that leaves with NEVER_AROSE prints a different word, so the
            // pinned row goes red with no number on the line at all.
            if (Probes.uncommented(src).contains("Outcome.NEVER_AROSE")) {
                byWord.add(probe);
            } else {
                unguarded.add(probe);
            }
        }
        for (String probe : unguarded) {
            System.out.println("VACUOUS " + probe
                    + " judged=yes none_field=no never_arose=no"
                    + " (its row cannot tell a full population from an empty one)");
        }
        for (String probe : missing) {
            System.out.println("NO_SOURCE " + probe + " judged=yes");
        }

        // The population rides its own line, unpinned. LeaveContract learned this
        // twice in one afternoon: a census inside an exact-line row goes red for
        // reasons that have nothing to do with the check, and then it is a number
        // people edit until the lane is quiet (#1221, #884).
        System.out.println("VACUOUS_CENSUS judged=" + rows.size()
                + " by_field=" + byField.size()
                + " by_word=" + byWord.size()
                + " no_source=" + missing.size());

        // `no_source` IS judged — a judged row naming a class with no file means
        // this read was over a population it could not see, which is the one
        // condition under which the count means nothing.
        boolean held = missing.isEmpty();
        Probes.leave("VERDICT " + (held ? "VACUOUS_GUARD_COUNTED" : "VACUOUS_GUARD_UNREAD")
                + " unguarded=" + unguarded.size(), held);
    }

    private VacuousGuard() {}
}
