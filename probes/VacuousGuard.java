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


    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            System.exit(selfcheck(Files.createTempDirectory("vacuousguard")));
        }
        if (args.length > 0 && args[0].startsWith("--")) {
            System.err.println("FATAL unknown flag: " + args[0] + " (this probe takes [repo-root] or --selfcheck)");
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
        // ONE READER (#1590), and this probe is where the divergence was found: it read
        // the table in Java, disagreed with `counters.sh` about how many rows there are,
        // and the shell one turned out to be missing three (#1588).
        java.util.Map<String, Boolean> fielded = new java.util.LinkedHashMap<>();
        for (Probes.BenchRow row : Probes.benchRows(bench)) {
            if (!row.judged()) {
                continue;
            }
            String probe = row.probe();
            // The first guard, and the one #1373 counted: a field on the pinned line
            // whose value is the size of what was read.
            boolean has = row.verdict().matches(".*\\b\\w+_none=\\d+.*");
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
            // ASSEMBLED, BECAUSE A SEARCH FOR X CANNOT BE WRITTEN AS X (#1607). The search
            // was `contains("Outcome.NEVER_AROSE")` — which is the string on this very line,
            // so this probe counted ITSELF as guarded, by the string it uses to count
            // guards. A checker exempting itself is the sixth instance of one shape here:
            // `advice.sh` five times (#1033, #1157, #1222, #1265, #1276), `LeaveContract`
            // reading `System.exit` inside a comment (#1531), and `LEAVE_BY_HAND` satisfied
            // by prose about `LEAVE_BY_HAND` (#1605).
            //
            // Qualifying the name does NOT fix it — the qualified literal is still the
            // literal. Neither does the strip: the string is CODE. Only assembling it does,
            // which is the idiom `advice.sh` reaches for when it builds `--pr` from `$dash`
            // for exactly this reason. It hides what is searched for, which is the price,
            // and the alternative is a checker that cannot see itself.
            //
            // The probe is NOT excluded from its own population instead. `LeaveContract`
            // states why one directory over: a check that excluded itself would report a
            // number nobody could reproduce by counting the file.
            if (guardsTheEmptyPath(src)) {
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
        // THE MEMBERS, NOT ONLY THE COUNT (#1550). `unguarded=` is pinned, so a probe
        // gaining a guard while another arrives needing one is a green row over a
        // different population. `LeaveContract`'s `by_hand=5` did exactly that across
        // #1531 — one probe out, one in, the number unmoved. The set rides its own line,
        // sorted and joined, UNPINNED: a member list in an exact-line grep is a list every
        // unit edits (#1192, #884).
        System.out.println("VACUOUS_MEMBERS unguarded=" + Probes.joined(unguarded));
        System.out.println("VACUOUS_CENSUS judged=" + rows.size()
                + " unguarded=" + unguarded.size()
                + " by_field=" + byField.size()
                + " by_word=" + byWord.size()
                + " no_source=" + missing.size());
        // `no_source` IS judged — a judged row naming a class with no file means
        // this read was over a population it could not see, which is the one
        // condition under which the count means nothing.
        // AND THE PROBE THAT MEASURES VACUOUS GUARDS SHOULD NOT BE ONE (#1607). Breaking
        // the self-match put this probe into its own `unguarded=` population, correctly:
        // its pinned verdict carried `unguarded=` and nothing else, and `unguarded=0` is
        // not the same statement as "the read opened a table". `judged_none=` is that
        // statement — the guard five siblings carry, and the one #970's
        // INSTRUMENTS_UNPROVEN is about.
        // THE VERDICT CARRIES A CEILING, NOT THE COUNT, SINCE #1649. `unguarded=` was
        // pinned exact and it is a BACKLOG under active repair: five consecutive units
        // walked it 29 -> 24, and three of them collided at the rebase — two branches
        // guarding a row each, both pinning the same number, the second red on a line
        // with nothing to do with its subject. That is #1453's stacked-baseline failure
        // arriving through a different door, and it charged a unit for doing the work
        // this probe exists to encourage.
        //
        // #1615's argument for an exact pin over a floor is about a SUITE, where an
        // exchange is a change of subject. This is not a suite; it is a queue, and
        // #1372's rule is that a gate installs at ZERO and not while the population is
        // being worked down.
        //
        // So the asymmetry is the whole design: SHRINKING IS FREE and GROWING COSTS A
        // UNIT. `over_ceiling=` is 1 when the backlog grew past a number somebody wrote
        // down, which is a real regression — a new judged row that cannot tell a full
        // population from an empty one — and 0 for every repair.
        //
        // The swap #1615 cares about is not lost, it MOVED: `VACUOUS_MEMBERS` has
        // carried the sorted set since #1550, so a probe gaining a guard while another
        // arrives needing one is visible in the sweep's own diff. It is unpinned there
        // deliberately — a member list in an exact-line grep is a list every unit edits
        // (#1192, #884) — which is the same argument as this one, made about the same
        // number, one direction earlier.
        //
        // The ceiling is DELIBERATELY SLACK and lowering it is a claim, not a bump: the
        // point is to catch growth, not to ratchet. Whoever lowers it says so.
        int ceiling = CEILING;
        boolean overCeiling = unguarded.size() > ceiling;
        boolean held = missing.isEmpty() && !rows.isEmpty();
        Probes.leave("VERDICT " + (held && !overCeiling ? "VACUOUS_GUARD_COUNTED" : "VACUOUS_GUARD_UNREAD")
                + " over_ceiling=" + (overCeiling ? 1 : 0)
                + " ceiling=" + ceiling
                + " judged_none=" + (rows.isEmpty() ? 1 : 0),
                held && !overCeiling);
    }




    /**
     * Is this source's {@code NEVER_AROSE} reached because the population was EMPTY? (#1609)
     *
     * <p>The word guard asked whether the constant is anywhere in the file, and
     * {@code VacuousGuard}'s crown (#1541) named that as generous from the day it landed:
     * <em>it asks whether the constant is reachable in the file, not whether it is reachable
     * on the EMPTY path.</em> Six probes were counted as guarded by a rule that cannot tell
     * an empty-population guard from any other use of the constant.
     *
     * <p>The tighter question is decidable from the same text. The constant sits inside a
     * {@code Probes.leave(...)} call and the call sits under a branch, so this looks BACK
     * from the constant for a condition mentioning emptiness — {@code == 0},
     * {@code isEmpty()}, {@code < 1}. That separates <em>the population was empty</em> from
     * <em>something else happened</em>.
     *
     * <p>The window is {@value #EMPTY_WINDOW} lines, which is a number and therefore a
     * weakness: it is wide enough for every real guard here — the widest is
     * {@code BondScenario}, five lines from its {@code fired > 0} arms to the {@code else}
     * that follows them — and narrow enough that an unrelated condition elsewhere in the
     * method does not reach. Stated rather than tuned silently: it was four, and four
     * called {@code BondScenario} unguarded.
     *
     * <p><b>What it cannot do</b>: decide whether the branch is REACHABLE, or whether the
     * condition is the RIGHT one — {@code if (census == 1)} reads as a guard. The direction
     * of that error is unchanged and generous: it over-counts guards and under-counts
     * {@code unguarded=}, which is the safe side for a census (#1207) and the wrong side
     * for a gate.
     */
    private static boolean guardsTheEmptyPath(Path src) throws IOException {
        List<String> lines = List.of(Probes.uncommented(src).split("\n", -1));
        String constant = "Probes.Outcome." + "NEVER_" + "AROSE";
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains(constant)) {
                continue;
            }
            for (int back = Math.max(0, i - EMPTY_WINDOW); back <= i; back++) {
                String line = lines.get(back);
                // `> 0` counts because an `else` after it IS the empty branch for a count.
                // `BondScenario` writes exactly that — two `fired > 0` arms and an else
                // whose comment says *the run produced no firing* — and a rule reading only
                // `== 0` called a legitimate guard unguarded. `AllocMeter`'s `ranAt != 1` is
                // NOT in the list and must not be: its NEVER_AROSE is a refused
                // configuration, not an empty population, which is the case #1609 predicted
                // this rule was always going to get wrong.
                if (line.contains("== 0") || line.contains("isEmpty()")
                        || line.contains("< 1") || line.contains("> 0")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The most unguarded judged rows this tree will tolerate before the sweep goes red
     * (#1649).
     *
     * <p>A CEILING and not a count. The population is a backlog under active repair —
     * five units walked it 29 to 24 in one afternoon — so an exact pin charged every
     * one of them an unrelated edit, and charged two of them a red lane for arithmetic
     * about a number a sibling branch had already moved. Shrinking is free; growing
     * costs a unit, which is the asymmetry a queue wants and a suite does not.
     *
     * <p>Set to the measured population at the time it was written, so it installs at
     * ZERO tolerance for growth (#1311: a gate installs at zero, never at one). Raising
     * it admits a new blind row and is a claim; lowering it is a ratchet nobody asked
     * for and is also a claim. Both belong in a pull request that says which.
     */
    private static final int CEILING = 25;
    /** How far back a guard's condition may sit from the constant it protects (#1609). */
    private static final int EMPTY_WINDOW = 6;


    /**
     * The empty-path reader's own cases (#1611).
     *
     * <p>{@link #guardsTheEmptyPath} shipped with a FITTED number and no cases: the window
     * was set to four, run, found {@code BondScenario} reported unguarded, measured its gap
     * at five, and set to six. The javadoc says so, which does not make it principled —
     * and without cases, changing it is a guess against a live sweep.
     *
     * <p>With them the width is a thing somebody can move and see the effect of, which is
     * what {@code window-just-outside} is for: it sits one line beyond the window and must
     * read as unguarded, so widening the number breaks a case rather than quietly changing
     * a census.
     */
    private static int selfcheck(Path tmp) throws IOException {
        int pass = 0;
        int fail = 0;
        String never = "Probes.Outcome." + "NEVER_" + "AROSE";

        String[][] cases = {
            // SameTick's shape: the simplest guard there is.
            {"guard-equals-zero", "if (census == 0) {\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // BondScenario's: two `> 0` arms and an else whose comment says the run produced
            // nothing. Five lines from condition to constant, which is what set the window.
            {"guard-else-after-positive",
                "} else if (tally.fired > 0 && tally.spent > 0) {\n  Probes.leave(\"H\", true);\n"
                        + "} else {\n  // nothing arose\n  int n = 0;\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // Accepted and written by nobody today.
            {"guard-is-empty", "if (rows.isEmpty()) {\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // AllocMeter's: a refused configuration, not an empty population.
            {"guard-refused-config", "if (ranAt != 1) {\n  Probes.leave(\"V\", " + never + ");\n}", "false"},
            {"guard-nothing-near", "Probes.leave(\"V\", " + never + ");", "false"},
            // THE FALSE POSITIVE THE LOOSE `> 0` INVITES, written down as a case rather
            // than as a sentence in a review: an unrelated condition inside the window.
            // Had AllocMeter's guard been `ranAt > 0`, this rule would call it guarded and
            // the finding #1609 exists for would have vanished.
            {"guard-unrelated-positive",
                "if (retries > 0) {\n  log();\n}\nif (ranAt != 1) {\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // AND THE WINDOW ITSELF. Seven lines from condition to constant — one beyond
            // the six — so this must read as unguarded. Widening the number breaks THIS
            // case rather than quietly moving a census.
            {"window-just-outside",
                "if (census == 0) {\n  a();\n  b();\n  c();\n  d();\n  e();\n  f();\n"
                        + "  Probes.leave(\"V\", " + never + ");\n}", "false"},
        };

        for (String[] c : cases) {
            Path f = tmp.resolve(c[0] + ".java");
            Files.writeString(f, c[1], StandardCharsets.UTF_8);
            boolean got = guardsTheEmptyPath(f);
            boolean ok = String.valueOf(got).equals(c[2]);
            System.out.printf("VACUOUS case=%-26s want=%-7s got=%-7s %s%n",
                    c[0], c[2], got, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        Probes.leave("VACUOUS SELFCHECK VERDICT " + (fail == 0 ? "READER_HOLDS" : "READER_BROKEN")
                + " cases=" + (pass + fail) + " failed=" + fail + " window=" + EMPTY_WINDOW,
                fail == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
        return fail;
    }

    private VacuousGuard() {}
}
