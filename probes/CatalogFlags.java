import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: does a probe's catalog row name every flag the probe parses?
 *
 * <p>{@code probes/README.md} is read against {@code probes/} in exactly two directions
 * today, and both are about EXISTENCE: {@code roster_check} asks whether every probe has a
 * row and every row a probe (#1177), and {@code counters.sh} asks whether a row names the
 * counters its bench row pins (#1356). <b>Nothing asks whether the row describes the
 * probe.</b> One directory over, {@code advice.sh} asks that six different ways.
 *
 * <h2>One direction, and the other one is refused with a reason</h2>
 *
 * {@code advice.sh} checks both — {@code flags_undocumented} reads arms against the row,
 * {@code flags_phantom} reads the row against the arms — and the phantom direction is
 * decidable there only because a tool's row carries a bounded {@code Usage:} clause.
 * {@code probes/README.md}'s rows have no such clause. They are three columns of prose that
 * legitimately name other programs' flags — {@code bash probes/bench.sh --twice},
 * {@code git rev-parse --show-toplevel} — so a whole-row read would report every one of
 * them as a phantom. That direction needs a clause the rows do not have, and inventing one
 * is a change to the catalog rather than a check of it.
 *
 * <h2>The door is Java, so the reading is shaped rather than greedy</h2>
 *
 * A quoted long option counts only where it is COMPARED — a {@code case} arm,
 * {@code .equals("--x")}, {@code "--x".equals(…)}, {@code startsWith("--x")} — which is the
 * same discipline {@code advice.sh} applies to shell (a {@code case} arm and a
 * {@code = "--flag"} comparison, not every occurrence).
 *
 * <p>That is not hypothetical. A greedy read of {@code DocLint} collects
 * {@code --is-shallow-repository} and {@code --verify}, which are arguments it passes to
 * {@code git}, and reports both as undocumented flags of the probe — the exact false
 * accusation {@code advice.sh} spent #1341 removing from its own reading.
 *
 * <p>Comments are stripped first (#1531). A flag discussed in a javadoc is not a door, and
 * this file's neighbours have met the opposite mistake five times.
 *
 * <h2>Reported, not judged</h2>
 *
 * {@code undocumented=} rides the verdict as a number and the word is about the READ. That
 * is {@code unfalsifiable=}'s path — reported by #1095, judged by #1311 once the population
 * it named had been worked down — and the reason is in the shaped read above: a fifth
 * spelling of a door reads as no flag at all, so a clean count is evidence about four
 * spellings and not about the directory.
 *
 * <p>Usage: {@code java -cp out:probes/out CatalogFlags [repo-root]}
 */
public final class CatalogFlags {


    /**


    /**
     * A long option in a COMPARING position, in the four spellings this directory writes.
     *
     * <p>{@code case "--x"}, {@code .equals("--x")}, {@code startsWith("--x")} and the
     * reversed {@code "--x".equals(…)}, which {@code NeutralDiff} uses and the first three
     * miss.
     *
     * <p>A fourth alternative — {@code switch ("--x")} — was here and was UNREACHABLE
     * (#1572): a switch takes the variable, not the literal, so nothing in this
     * directory could ever have matched it. It cost nothing and it was a branch no
     * fixture drives, which is where a wrong one hides longest (#1358 found two verdict
     * chain branches in exactly that state, and one of them printed a defect report
     * naming the wrong defect).
     */
    private static final Pattern DOOR = Pattern.compile(
            "(?:case\\s+|\\.equals\\(|\\.startsWith\\()\"(--[a-z][a-z0-9-]*)\""
                    + "|\"(--[a-z][a-z0-9-]*)\"\\.(?:equals|startsWith)\\(");

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            System.exit(selfcheck(Files.createTempDirectory("catalogflags")));
        }
        if (args.length > 0 && args[0].startsWith("--")) {
            System.err.println("FATAL unknown flag: " + args[0] + " (this probe takes [repo-root] or --selfcheck)");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        Path bench = root.resolve("probes/bench.sh");
        Path catalog = root.resolve("probes/README.md");
        if (!Files.isReadable(bench) || !Files.isReadable(catalog)) {
            Probes.leave("VERDICT CATALOG_FLAGS_UNREADABLE bench=" + bench + " catalog=" + catalog, false);
        }
        String rows = Files.readString(catalog, StandardCharsets.UTF_8);

        Set<String> probes = new LinkedHashSet<>();
        // ONE READER (#1590). Both walks below take the same list, so the population
        // and the per-row question can no longer disagree about what a row is.
        List<Probes.BenchRow> table = Probes.benchRows(bench);
        for (Probes.BenchRow row : table) {
            probes.add(row.probe());
        }

        // THE TWO NO-COUNTER POPULATIONS, MOVED HERE FROM `counters.sh` (#1586).
        //
        // They were in a SHELL tool, which meant that tool read Java and carried the
        // tree's third comment strip — four `sed` rules in a fixed order, with five
        // cases of its own, forced by the language boundary rather than chosen (#1583).
        // It is only forced while the counter lives in a script. This class already
        // reads the bench table, the catalog and probe sources through
        // `Probes.uncommented`, which carries state across lines and has cases (#1580),
        // so the move deletes a parser rather than porting one.
        //
        // `row_no_counter=` is *the grep on this row cannot move*: a pinned verdict
        // reading `VERDICT X_HELD` with nothing after it cannot be told apart from a
        // row that always prints that line.
        //
        // `prints_no_counter=` is *this probe prints no number at all*, asked ONLY of
        // rows already in the first, so it is a subset by construction. Zero of the
        // twenty-seven: every one of those rows belongs to a probe that prints counters
        // and pins none of them, which is the measurement that separates the two
        // readings (#1579) — `SameTick` prints five and pins a word.
        int rowNoCounter = 0;
        int printsNoCounter = 0;
        for (Probes.BenchRow row : table) {
            if (!row.judged()) {
                continue;
            }
            String probe = row.probe();
            // A class with no catalog row is `roster_check`'s finding and is skipped
            // BEFORE this count, so it cannot inflate the subclass (#1170).
            if (rowFor(rows, probe) == null) {
                continue;
            }
            if (COUNTER.matcher(row.verdict()).find()) {
                continue;
            }
            rowNoCounter++;
            System.out.println("NO_COUNTER " + probe
                    + " — the verdict on this row carries no field that can move");
            Path psrc = root.resolve("probes/" + probe + ".java");
            if (Files.isReadable(psrc) && !printsACounter(psrc)) {
                printsNoCounter++;
                System.out.println("PRINTS_NO_COUNTER " + probe
                        + " — and its source prints no name=value anywhere");
            }
        }

        int checked = 0;
        int noRow = 0;
        int noSource = 0;
        int noFlags = 0;
        List<String> undocumented = new ArrayList<>();
        Map<String, Integer> perProbe = new LinkedHashMap<>();

        for (String probe : probes) {
            Path src = root.resolve("probes/" + probe + ".java");
            if (!Files.isReadable(src)) {
                // `roster_check`'s question, not this one — counted so a clean read
                // cannot mean "walked past it" (#1170).
                noSource++;
                continue;
            }
            String row = rowFor(rows, probe);
            if (row == null) {
                // Also `roster_check`'s. Reporting it here too would be one defect
                // counted twice.
                noRow++;
                continue;
            }
            Set<String> doors = doorsOf(src);
            if (doors.isEmpty()) {
                // A probe with no long-option door is not a defect — `advice.sh` prints
                // NO_FLAGS for the same shape one directory over (#1207) — and it is
                // counted, so a green line cannot mean nobody looked.
                noFlags++;
                continue;
            }
            checked++;
            int missing = 0;
            for (String flag : doors) {
                if (!namedBy(row, flag)) {
                    missing++;
                    undocumented.add(probe + " " + flag);
                }
            }
            if (missing > 0) {
                perProbe.put(probe, missing);
            }
        }

        for (String row : undocumented) {
            System.out.println("UNDOCUMENTED_FLAG " + row.replace(' ', ' ')
                    + " — the probe parses it and its catalog row never names it");
        }

        // THE MEMBERS, NOT ONLY THE COUNT (#1572). `undocumented=` is pinned, so a
        // flag documented while another arrives undocumented is a green row over a
        // different twenty — the swap #1550 closed for `LeaveContract` and
        // `VacuousGuard`, and left open here because this probe already prints one
        // UNDOCUMENTED_FLAG row per finding. Those rows are not enough: they come out
        // in bench-table order, so moving a row in `bench.sh` reorders them and a diff
        // of two sweeps reports a reordering as a change. Sorted and joined on ONE
        // line is what makes two sweeps of one tree byte-identical.
        System.out.println("CATALOG_FLAGS_MEMBERS undocumented=" + Probes.joined(undocumented));
        // The populations ride their own line, unpinned (#1221). Every one of them moves
        // when a probe is added, and a census inside an exact-line row is a number people
        // learn to edit until the lane is quiet.
        // `bench_rows=` is the CROSS-LANGUAGE number (#1590). `counters.sh` prints its own
        // `rows=` from a shell walk of the same table, and until #1588 the two disagreed by
        // three with nothing comparing them — which is how the loss survived. The lane
        // compares these two integers now, so the next divergence is a red build rather
        // than an accident somebody notices while writing a second reader.
        long judgedRows = table.stream().filter(Probes.BenchRow::judged).count();
        // ONE VERB LIST, PLUS THE MODIFIER (#1602). This was a `Set.of` with the reader's
        // three words copied into it, three units after #1598 collapsed two verb lists into
        // one — and in a unit whose subject was *the checks read the readers and nothing
        // reads the file*. Add a fifth verb to the reader and not here, and this reports
        // the reader's own verb as unread: a false accusation about the file.
        //
        // They are not the same list and merging them would be wrong. This one is the
        // reader's plus `vary`, because the MODIFIER is known to the file and not to the
        // reader — it decorates a row rather than being one — and that is exactly why
        // `verb_shaped=` and `bench_rows=` differ by seven.
        java.util.Set<String> known = new java.util.LinkedHashSet<>(Probes.BENCH_VERB_WORDS);
        known.add("vary");
        java.util.List<String> strangers = new ArrayList<>();
        for (String verb : Probes.verbShaped(bench)) {
            if (!known.contains(verb)) {
                strangers.add(verb);
            }
        }
        for (String verb : strangers) {
            System.out.println("UNREAD_VERB " + verb
                    + " — a line in the bench table opens with it and no reader knows it");
        }
        System.out.println("CATALOG_FLAGS_CENSUS probes=" + probes.size()
                + " bench_rows=" + judgedRows
                + " verb_shaped=" + Probes.verbShaped(bench).size()
                + " unread_verbs=" + strangers.size()
                + " checked=" + checked
                + " no_flags=" + noFlags
                + " no_row=" + noRow
                + " no_source=" + noSource
                + " row_no_counter=" + rowNoCounter
                + " prints_no_counter=" + printsNoCounter);

        // `checked_none=` IS the guard, and this probe would otherwise have been the
        // twenty-eighth row VacuousGuard reports as unable to tell a full population
        // from an empty one — a checker of catalogs joining the population of rows
        // that cannot say whether they read anything. A sweep that checked NO probe
        // must not print a clean count; that is #970's INSTRUMENTS_UNPROVEN and the
        // reason five siblings carry a `_none=` field.
        // `unread_verbs` IS the break, unlike the two no-counter populations beside it:
        // a row nothing reads is not a style preference, it is a row nothing reads.
        boolean held = noSource == 0 && noRow == 0 && checked > 0 && strangers.isEmpty();
        Probes.leave("VERDICT " + (held ? "CATALOG_FLAGS_COUNTED" : "CATALOG_FLAGS_UNREAD")
                + " undocumented=" + undocumented.size()
                + " checked_none=" + (checked == 0 ? 1 : 0), held);
    }

    /**
     * The catalog row opening with this probe's name, or null.
     *
     * <p>A row is one line — a markdown cell cannot hold a newline — which is the property
     * #1370 gave `.gitattributes` a merge driver for, and the property that makes this a
     * substring search rather than a parse.
     */

    private static String rowFor(String catalog, String probe) {
        for (String line : catalog.split("\n")) {
            if (line.startsWith("| `" + probe + "`")) {
                return line;
            }
        }
        return null;
    }


    /**
     * Does this row NAME this flag? (#1576)
     *
     * <p>A plain {@code contains} is wrong and the case that found it is in the suite:
     * a row saying {@code --prefix} contains {@code --pr}, so a flag the row never
     * mentions reads as documented. That is the substring trap {@code advice.sh} solved
     * by padding both sides with spaces and matching with {@code case} — <em>the padding
     * is what keeps `--for` from matching inside `--format`</em> (#1033) — and this file
     * had it in the direction that hides a finding rather than inventing one.
     *
     * <p>The boundary is "not a flag character": a long option is followed by a space, a
     * backtick, a comma, a full stop or the end of the row, and never by a letter, a digit
     * or a hyphen.
     */
    private static boolean namedBy(String row, String flag) {
        int at = row.indexOf(flag);
        while (at >= 0) {
            int after = at + flag.length();
            if (after >= row.length()) {
                return true;
            }
            char c = row.charAt(after);
            if (!Character.isLetterOrDigit(c) && c != '-') {
                return true;
            }
            at = row.indexOf(flag, at + 1);
        }
        return false;
    }


    /**
     * A `name=value` field on a pinned verdict, or in a printed string (#1372, #1579).
     *
     * <p>One pattern for both readings, because they are the same question asked of two
     * texts: <em>is there a number here that can move.</em>
     */
    private static final Pattern COUNTER = Pattern.compile("[a-z_]+=");

    /**
     * Does this source print a {@code name=value} anywhere? (#1586)
     *
     * <p>Through {@code Probes.uncommented}, which is the point of the move: the shell
     * version carried four `sed` rules in a fixed order and got two of them wrong before a
     * case caught it (#1583), because a line-by-line filter cannot carry state across a
     * block comment. This one can, and has cases (#1580).
     *
     * <p>The read is still generous — any {@code name=} inside a string literal — so it
     * over-counts a probe that prints {@code x=} in prose it emits. That UNDERSTATES the
     * population and can never invent a member of it, which is the safe direction for a
     * census (#1207).
     */
    private static boolean printsACounter(Path src) throws IOException {
        for (String line : Probes.uncommented(src).split("\n")) {
            int quote = line.indexOf('"');
            if (quote >= 0 && COUNTER.matcher(line.substring(quote)).find()) {
                return true;
            }
        }
        return false;
    }

    /** Every long option this source COMPARES against, comments stripped (#1531). */

    /**
     * The reading's own cases, over fixtures written to a temp directory (#1576).
     *
     * <p>This probe reads fifty-five sources with a textual rule that has a KNOWN false
     * positive — a greedy read of {@code DocLint} collects {@code --is-shallow-repository}
     * and {@code --verify}, which it hands to {@code git} — and until now that correction
     * was asserted in a javadoc and demonstrated nowhere. The same shape has been re-broken
     * twice in this tree: {@code advice.sh} was bitten by self-matching test data five
     * times, and #1531 found {@code LeaveContract} reading {@code System.exit} inside a
     * comment. Both were repaired by a reader that had cases.
     *
     * <p>Four spellings means four cases, because a four-branch alternation has four ways
     * to be wrong — and the reversed {@code "--x".equals(…)} is the one the first three
     * miss, which is why {@code NeutralDiff} is the probe that found it.
     */
    private static int selfcheck(Path tmp) throws IOException {
        int pass = 0;
        int fail = 0;
        String dash = "--";

        // name, source, want (comma-joined doors, or "" for none)
        String[][] cases = {
            {"door-case-arm", "class A { void m(String s) { switch (s) { case \"" + dash + "pr\": break; } } }", dash + "pr"},
            {"door-equals", "class A { void m(String s) { if (s.equals(\"" + dash + "pr\")) { } } }", dash + "pr"},
            {"door-starts-with", "class A { void m(String s) { if (s.startsWith(\"" + dash + "pr\")) { } } }", dash + "pr"},
            // THE ONE THE OTHER THREE MISS. NeutralDiff writes it, and a reader without
            // this alternation reports that probe as parsing no flags at all.
            {"door-reversed-equals", "class A { void m(String s) { if (\"" + dash + "pr\".equals(s)) { } } }", dash + "pr"},
            // THE FALSE POSITIVE THAT COST advice.sh A UNIT (#1341), here as a case rather
            // than as a sentence: an argument handed to another program is not this
            // probe's door.
            {"another-programs-flag",
                "class A { void m() { new ProcessBuilder(\"git\", \"rev-parse\", \"" + dash + "verify\", \"HEAD\"); } }", ""},
            // A flag DISCUSSED is not a flag PARSED (#1531). The strip is Probes'; this
            // asserts the reading goes through it.
            {"flag-in-a-comment",
                "class A {\n  /** takes " + dash + "pr, one day. */\n  void m() { }\n}", ""},
            // Two doors in one file, and the set is a SET: a probe writing the same arm
            // twice must not read as two flags.
            {"two-doors-and-a-repeat",
                "class A { void m(String s) { if (s.equals(\"" + dash + "pr\")) { } if (s.equals(\"" + dash + "sha\")) { }"
                        + " if (s.equals(\"" + dash + "pr\")) { } } }", dash + "pr," + dash + "sha"},
        };

        for (String[] c : cases) {
            Path f = tmp.resolve(c[0] + ".java");
            Files.writeString(f, c[1], StandardCharsets.UTF_8);
            String got = String.join(",", doorsOf(f));
            boolean ok = got.equals(c[2]);
            System.out.printf("CATALOG case=%-24s want=%-14s got=%-14s %s%n",
                    c[0], c[2].isEmpty() ? "<none>" : c[2], got.isEmpty() ? "<none>" : got,
                    ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // The join with the ROW, which is the other half of the reading and has its own
        // way to be wrong: a row naming a LONGER flag must not satisfy a shorter one.
        // `--base` is not documented by a row that only says `--baseline`.
        // THE JOIN WITH THE ROW, which has its own way to be wrong and did: a plain
        // `contains` reads `--prefix` as naming `--pr`, so a flag the row never
        // mentions counts as documented. That is the substring trap `advice.sh`
        // solved by padding both sides with spaces (#1033), in the direction that
        // HIDES a finding rather than inventing one — which is why nothing noticed.
        String[][] rowCases = {
            {"row-names-it", "| `Fix` | q | takes `" + dash + "pr` and nothing else. |", dash + "pr", "true"},
            {"row-silent", "| `Fix` | q | takes nothing worth saying. |", dash + "pr", "false"},
            {"row-names-a-longer-flag", "| `Fix` | q | takes `" + dash + "prefix`. |", dash + "pr", "false"},
            {"row-names-both", "| `Fix` | q | takes `" + dash + "prefix` and `" + dash + "pr`. |", dash + "pr", "true"},
            {"row-ends-with-it", "| `Fix` | q | takes " + dash + "pr", dash + "pr", "true"},
        };
        for (String[] c : rowCases) {
            boolean named = namedBy(c[1], c[2]);
            boolean ok = String.valueOf(named).equals(c[3]);
            System.out.printf("CATALOG case=%-24s want=%-14s got=%-14s %s%n",
                    c[0], c[3], named, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // THE TWO NO-COUNTER READINGS (#1586), moved here with their populations. The
        // shell versions had five cases of their own; these replace them, and the
        // `strip:*` family is gone entirely because `Probes.uncommented` is the strip now.
        String[][] counterCases = {
            {"counter-a-field", "VERDICT X a=0", "true"},
            {"counter-a-word", "VERDICT X_HELD", "false"},
            // The pattern must not be satisfied by an `=` with nothing in front of it.
            {"counter-bare-equals", "VERDICT X =0", "false"},
            {"counter-underscored", "VERDICT X late_ticks=0", "true"},
        };
        for (String[] c : counterCases) {
            boolean got = COUNTER.matcher(c[1]).find();
            boolean ok = String.valueOf(got).equals(c[2]);
            System.out.printf("CATALOG case=%-24s want=%-14s got=%-14s %s%n",
                    c[0], c[2], got, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }


        // `printsACounter` over sources, through the strip. The shell version got two of
        // its four `sed` rules wrong and a case caught each (#1583); these are the same
        // shapes, answered by a reader that carries state across lines and has cases of
        // its own (#1580).
        String[][] printCases = {
            {"prints-a-counter", "class A { void m() { System.out.println(\"A census=1\"); } }", "true"},
            {"prints-a-word", "class A { void m() { System.out.println(\"VERDICT HELD\"); } }", "false"},
            {"prints-trailing-comment", "class A { void m() { int n = 1; // note x=1\n"
                    + "System.out.println(\"VERDICT HELD\"); } }", "false"},
            {"prints-javadoc-counter", "class A {\n  /**\n   * prints census=N one day.\n   */\n"
                    + "  void m() { System.out.println(\"VERDICT HELD\"); } }", "false"},
            // THE SHAPE THE SHELL STRIP GOT WRONG: a leading `*/` closes a block comment
            // and is followed by code on the same line. A line-by-line filter that drops
            // any line opening with `*` eats the code after the close; this reader does not.
            {"prints-after-block-close", "class A { void m() { int n = 1; /* note\n"
                    + "   */ System.out.println(\"A census=1\"); } }", "true"},
        };
        for (String[] c : printCases) {
            Path f = tmp.resolve(c[0] + ".java");
            Files.writeString(f, c[1], StandardCharsets.UTF_8);
            boolean got = printsACounter(f);
            boolean ok = String.valueOf(got).equals(c[2]);
            System.out.printf("CATALOG case=%-24s want=%-14s got=%-14s %s%n",
                    c[0], c[2], got, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }
        Probes.leave("CATALOG SELFCHECK VERDICT " + (fail == 0 ? "READER_HOLDS" : "READER_BROKEN")
                + " cases=" + (pass + fail) + " failed=" + fail,
                fail == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
        return fail;
    }

    private static Set<String> doorsOf(Path src) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = DOOR.matcher(Probes.uncommented(src));
        while (m.find()) {
            found.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return found;
    }

    private CatalogFlags() {}
}
