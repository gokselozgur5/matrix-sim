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
 * Probe: does every judged probe reach its exit through {@code Probes.leave}?
 *
 * <p>{@code probes/README.md} states the contract plainly — a judged probe owes
 * a greppable verdict line AND an honest exit code, and {@code Probes.leave} is
 * the one place that owes both. #1093 established it after #1091 found
 * {@code DistrictNeutral} printing {@code DISTRICTS_TOUCHED_THE_STREAM} and
 * exiting 0. Nothing has ever checked that a probe obeys it.
 *
 * <p>Measured the day this landed: <b>eight judged probes had no
 * {@code System.exit} anywhere</b> — {@code BondScenario}, {@code CapSentinel},
 * {@code CensusSampleSize}, {@code DocLint}, {@code DoorPressure},
 * {@code LedgerMirror}, {@code PodOptional}, {@code SealHygiene}. Each printed
 * its failing verdict and fell off the end of {@code main}, which is exit 0.
 * `SealHygiene` would have printed {@code SEAL_HYGIENE_BROKEN} and left with
 * the code that means "the seal is clean". The bench still went red on the
 * verdict grep — but every hand-run invocation, every {@code $?} in a script,
 * and every future caller reading the code was told the contract held. That is
 * #1091 exactly, eight times, four months after it was supposedly fixed.
 *
 * <h2>The join</h2>
 *
 * The question is NOT "does every probe call the helper". A <b>reporting</b>
 * probe must not: a {@code run} row fails on a nonzero exit, so adopting an
 * exit code there changes what the row means. The bench table already knows
 * which rows are judged, so the rule is:
 *
 * <pre>a probe with a `judge` or `known` row in bench.sh must call Probes.leave</pre>
 *
 * and the table is read rather than a list being kept here. A list would be a
 * second copy of the bench's own contract, which is the shape #1192 spent a
 * unit deleting.
 *
 * <h2>What it cannot see</h2>
 *
 * A probe that calls {@code Probes.leave} on one branch and returns on another
 * satisfies this check and still lies on the second branch. This finds probes
 * that never route through the helper at all — the population that produced
 * every one of the eight — and says so in its verdict rather than implying more.
 *
 * <h2>The census is not the verdict</h2>
 *
 * {@code LEAVE_CENSUS} carries {@code judged=}, {@code reporting=} and
 * {@code no_source=}; the verdict line carries only {@code no_code=} and
 * {@code by_hand=}. The first draft put the population in the verdict, the
 * bench row pinned it by exact-line grep, and the row went red TWICE in one
 * afternoon for reasons unrelated to this check — once when its own row was
 * added, once when {@code SheetDump --catalog} landed. A pinned census in a
 * lane is a number people learn to edit, which is #884's lesson about the seal.
 *
 * <p>{@code judged=} does include this probe: the table is read as the table
 * and the row for this probe is a row. A check that excluded itself would
 * report a number nobody could reproduce by counting the file.
 *
 * <p>Usage: {@code java -cp out:probes/out LeaveContract [repo-root]}
 */
public final class LeaveContract {



    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        // AN ARGUMENT DOOR OWES A REFUSAL (DoorRefusal, #1531). Before --selfcheck this
        // probe had no flags: every argument was a repo root, so an unknown flag was
        // read as a directory that does not exist and the check judged its contract
        // anyway. The door arrives with the refusal, in the same unit, because the
        // sweep is what would otherwise find it — and did, on the first run.
        if (args.length > 0 && args[0].startsWith("--") && !args[0].equals("--selfcheck")) {
            System.err.println("FATAL unknown flag: " + args[0]
                    + " (this probe takes [repo-root] or --selfcheck)");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            selfcheck(Files.createTempDirectory("leavecontract"));
            return;
        }
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        Path bench = root.resolve("probes/bench.sh");
        if (!Files.isReadable(bench)) {
            Probes.leave("VERDICT LEAVE_CONTRACT_NO_BENCH " + bench, false);
        }

        Set<String> judged = new LinkedHashSet<>();
        Set<String> reporting = new LinkedHashSet<>();
        // ONE READER (#1590). Three probes and a shell tool each parsed this table;
        // the shell one lost three rows for the file's whole life (#1588) and the three
        // Java copies agreed by descent rather than independently.
        for (Probes.BenchRow row : Probes.benchRows(bench)) {
            (row.verb().equals("run") ? reporting : judged).add(row.probe());
        }

        List<String> noCode = new ArrayList<>();
        List<String> byHand = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String probe : judged) {
            Path src = root.resolve("probes/" + probe + ".java");
            if (!Files.isReadable(src)) {
                // A judged row for a class with no source is `roster_check`'s
                // question, not this one — but a check that walked past it
                // would report a clean join over a probe it never opened.
                missing.add(probe);
                continue;
            }
            // READ THROUGH THE COMMENT STRIP (#1531). This read the raw file, and a
            // javadoc sentence containing the words `System.exit` was enough to make a
            // probe with no verdict code at all look like one that spends its own:
            // CensusBeatDrift printed DRIFT_FLAGGED and left with 0 while being counted
            // as a style preference. The same blindness sits on the `Probes.leave` test
            // one line below — a probe that only MENTIONS the helper in a comment would
            // be skipped entirely, which is the quieter half of the same mistake.
            //
            // #1512 landed the strip for exactly this class of matcher, and #1516 said
            // the next one written without it should be found by a red build. This is
            // that matcher; it predates the strip.
            String body = Probes.uncommented(src);
            if (body.contains("Probes.leave")) {
                continue;
            }
            // Two different facts, and only one of them is a defect.
            //
            // A probe with NO exit call anywhere falls off the end of `main`,
            // which is exit 0 — so its failing verdict leaves with the code
            // that means "held". That is #1091's defect and it is red.
            //
            // A probe that calls `System.exit` itself is HONEST; it just does
            // not route through the shared helper. That is a shape worth
            // counting — one contract, one place to change it — but calling it
            // a defect would be calling a style a lie, and this probe's whole
            // subject is the difference.
            //
            // A REFUSAL EXIT IS NOT A VERDICT CODE (#1502), and reading it as one blinded
            // this check to the defect it exists for. `Outcome.code()`'s javadoc REQUIRES
            // a refusal to sit outside `leave` — *a refused invocation has nothing to print
            // in the bench's grammar, so it reaches System.exit directly* — so every probe
            // with an argument door carries one, and `contains("System.exit")` was satisfied
            // by it. Five probes printed a FAILING verdict and fell off the end of main at
            // exit 0 while being counted as a style preference: HullRoster's ROSTER_BROKEN,
            // FleetLines' FLEET_LINES_LIE, OrderTable's VACUOUS, AllocMeter's OVER_BUDGET,
            // UnparkStorm's UNBOUNDED.
            //
            // So the exits are counted with the refusal removed. An exit that is ONLY a
            // refusal leaves the verdict path with nothing, which is `no_code` — the red
            // case, and the one #1091 was opened for.
            (spendsBeyondRefusal(body) ? byHand : noCode).add(probe);
        }

        for (String probe : noCode) {
            System.out.println("NO_EXIT_CODE " + probe
                    + " judged=yes leave=no exit=no (a failing verdict leaves with 0)");
        }
        for (String probe : byHand) {
            System.out.println("EXITS_BY_HAND " + probe + " judged=yes leave=no exit=yes");
        }
        for (String probe : missing) {
            System.out.println("NO_SOURCE " + probe + " judged=yes");
        }

        // The population is its own line, and that is not cosmetic. The first
        // draft put `judged=` in the verdict, the bench row pinned it by
        // exact-line grep, and the row went red TWICE in one afternoon for
        // reasons that had nothing to do with this check: once when the row for
        // this probe was added, once when `SheetDump --catalog` landed. A pinned
        // census in a lane is a number people learn to edit, which is #884's
        // lesson about the seal. What the row must pin is the CONTRACT —
        // `no_code=0`, and `by_hand=` because a probe leaving the helper is a
        // decision — and the counts belong beside it, greppable and unpinned.
        // THE MEMBERS, NOT ONLY THE COUNT (#1550). `by_hand=5` was 5 before #1531 and 5
        // after, and the population was not the same five: `CensusBeatDrift` left it — it
        // had no verdict exit code at all, the red case — and `KnownFixture` entered it,
        // having been skipped entirely while the raw read matched `Probes.leave` inside a
        // comment. One probe out, one in, and the pinned row matched throughout.
        //
        // The row is right to match: it pins the CONTRACT and not the census. But every
        // argument for pinning a number is that a change in it is a finding, and a SWAP is
        // a change that produces no signal. So the set rides its own line, sorted and
        // joined, UNPINNED — a member list in an exact-line grep is a list every unit
        // edits, which is what #1192 spent a unit deleting and #884's lesson about the seal.
        System.out.println("LEAVE_MEMBERS by_hand=" + Probes.joined(byHand)
                + " no_code=" + Probes.joined(noCode));
        System.out.println("LEAVE_CENSUS judged=" + judged.size()
                + " reporting=" + reporting.size()
                + " no_source=" + missing.size());

        boolean held = noCode.isEmpty() && missing.isEmpty();
        Probes.leave("VERDICT " + (held ? "EVERY_JUDGED_PROBE_HAS_A_CODE" : "A_JUDGED_PROBE_HAS_NO_CODE")
                + " no_code=" + noCode.size()
                + " by_hand=" + byHand.size(), held);
    }

    /**
     * Does this source leave with a code its VERDICT path can reach? (#1502)
     *
     * <p>Every `System.exit` in the file except the argument-refusal door. The refusal is
     * excluded because the grammar puts it there for an unrelated reason and it says
     * nothing about what a failing verdict leaves with — reading it as a verdict code is
     * what let five probes print `ROSTER_BROKEN` and friends at exit 0 while this check
     * called them stylistically different.
     *
     * <p>Textual, and it is honest about that: a probe that reached its refusal code
     * through a variable, or spelling the refusal as a bare digit, would be read as a
     * verdict exit. Neither exists here — {@code ExitGrammar} pins the codes and every
     * refusal in {@code probes/} is written `Probes.Outcome.REFUSED.code()` — and the
     * direction of the mistake is the safe one: it would count a refusal as a verdict code
     * and leave a probe in `by_hand` rather than reporting a defect that is not there.
     */


    private static boolean spendsBeyondRefusal(String body) {
        return body.replace("System.exit(Probes.Outcome.REFUSED.code())", "")
                .contains("System.exit");
    }


    /**
     * The reader's own cases, over fixtures written to a temp directory (#1531).
     *
     * <p>This check spent its whole life reading probe sources with no way to be watched
     * misreading one, which is the state {@code advice.sh} prints UNFALSIFIABLE about in
     * the other family. The three cases that matter are all about what a COMMENT can do
     * to a textual matcher, and the middle one is a live defect until the strip lands:
     * a javadoc sentence containing the words {@code System.exit} made a probe with no
     * verdict code look like one that spends its own.
     */
    private static void selfcheck(Path tmp) throws IOException {
        int pass = 0;
        int fail = 0;

        String refusal = "System.exit(Probes.Outcome.REFUSED.code());";
        String broke = "System.exit(Probes.Outcome.BROKE.code());";

        // name, source, want-spends-verdict-code, want-uses-helper
        String[][] cases = {
            {"refusal-only", "class A { void m() { " + refusal + " } }", "false", "false"},
            {"refusal-and-comment", "class A {\n  /** " + "System" + ".exit has already left. */\n"
                    + "  void m() { " + refusal + " }\n}", "false", "false"},
            {"a-real-code", "class A { void m() { " + broke + " } }", "true", "false"},
            {"helper-in-code", "class A { void m() { Probes.leave(\"V\", true); } }", "false", "true"},
            {"helper-in-comment", "class A {\n  // Not Probes.leave: this one is a note.\n"
                    + "  void m() { " + broke + " }\n}", "true", "false"},
        };

        for (String[] c : cases) {
            Path f = tmp.resolve(c[0] + ".java");
            Files.writeString(f, c[1], StandardCharsets.UTF_8);
            String body = Probes.uncommented(f);
            boolean spends = spendsBeyondRefusal(body);
            boolean helper = body.contains("Probes.leave");
            boolean ok = spends == Boolean.parseBoolean(c[2]) && helper == Boolean.parseBoolean(c[3]);
            System.out.printf("LEAVE case=%-20s want=%s/%s got=%s/%s %s%n",
                    c[0], c[2], c[3], spends, helper, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }


        // THE STRIP'S OWN CASES (#1580). `Probes.uncommented` makes four checkers correct
        // — this one, `DoorRefusal`, `CatalogFlags`, `SheetFence` — and had none. Its
        // evidence was four callers whose suites exercise it INCIDENTALLY: the
        // `refusal-and-comment` case above asserts this probe's classification, not the
        // strip, and a strip that removed one line too many would fail it for a reason it
        // does not name.
        //
        // THE PROPERTY THAT MATTERS IS THE LINE COUNT, and it was asserted nowhere. #1512's
        // whole subject was that a regex deleting `(?s)/\*.*?\*/` deletes the newlines
        // inside a block comment too, joining the line before to the line after — and
        // `LatticeFence` reads field declarations ONE LINE AT A TIME, so the joined result
        // is a member declaration nobody wrote. The line-preserving reading is the body for
        // that reason, and a future tidy returning a collapsed string would have satisfied
        // every case that existed.
        String[][] strips = {
            {"strip-line-comment", "int a; // gone\nint b;", "int a; \nint b;"},
            {"strip-block-one-line", "int /* gone */ a;", "int  a;"},
            // The count is the assertion: three lines in, three lines out, whatever the
            // comment did to their contents.
            {"strip-block-spans-lines", "int a;\n/* gone\n   also gone */ int b;", "int a;\n\n int b;"},
            {"strip-block-opens-and-runs", "int a; /* gone\ngone */ int b;", "int a; \n int b;"},
            // A HEURISTIC, NOT A LEXER, and this is where that is written down. A `//`
            // inside a string literal is truncated, because the strip reads characters and
            // not tokens. No probe writes one today; the case exists so the next reader
            // meets the limitation as a fact rather than discovering it, and so a lexer —
            // if one is ever wanted — arrives with a case that changes rather than a
            // surprise.
            {"strip-slashes-in-a-string", "String s = \"a//b\";", "String s = \"a"},
        };
        for (String[] c : strips) {
            Path f = tmp.resolve(c[0] + ".txt");
            Files.writeString(f, c[1], StandardCharsets.UTF_8);
            String got = Probes.uncommented(f);
            int wantLines = c[2].split("\n", -1).length;
            int gotLines = got.split("\n", -1).length;
            boolean ok = got.equals(c[2]) && gotLines == wantLines;
            System.out.printf("LEAVE strip=%-26s lines=%d/%d %s%n",
                    c[0], gotLines, wantLines, ok ? "OK" : "BROKEN");
            if (!ok) {
                System.out.printf("     want=[%s]%n     got =[%s]%n",
                        c[2].replace("\n", "\\n"), got.replace("\n", "\\n"));
            }
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // THE SHARED HELPERS' OWN CASES (#1592). `Probes` carries four things every probe
        // can reach and two of them had none: `joined` (#1574) and `benchRows` (#1590).
        // Their evidence was four numbers across three probes that did not move — real
        // regression evidence, and neither pins a RULE. #1580 made this argument about the
        // comment strip in the same suite: *a strip that removed one line too many would
        // fail those cases for a reason neither names*, which is exactly how the shell
        // reader's three-row loss stayed invisible for a year (#1588).
        //
        // They live here rather than on `Probes`, which has no `main` and no bench row —
        // the compromise #1580 stated rather than preferred.
        String[][] joins = {
            {"joined-sorts", "c,a,b", "a,b,c"},
            // `none`, not an empty field: a trailing `=` followed by nothing reads as a
            // truncated line rather than as an empty set (#1550).
            {"joined-empty-is-none", "", "none"},
            {"joined-one", "only", "only"},
            // Sorting is what makes two sweeps byte-identical, so a list already in order
            // must come back unchanged and one out of order must not.
            {"joined-already-sorted", "a,b,c", "a,b,c"},
        };
        for (String[] c : joins) {
            List<String> in = c[1].isEmpty() ? new ArrayList<>()
                    : new ArrayList<>(java.util.Arrays.asList(c[1].split(",")));
            String got = Probes.joined(in);
            boolean ok = got.equals(c[2]);
            System.out.printf("LEAVE helper=%-24s want=%-10s got=%-10s %s%n",
                    c[0], c[2], got, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // `benchRows`, over fixture tables. The `vary`-then-plain shape is the one that has
        // actually gone wrong — in shell, where the block joined into one line and the row
        // after it was lost (#1588). The Java reader takes the row and drops the modifier,
        // which is the OPPOSITE mistake and is why it is pinned here.
        String[][] tables = {
            {"bench-judge", "  judge Alpha 'VERDICT X a=0'", "judge/Alpha/VERDICT X a=0"},
            {"bench-run-has-no-verdict", "  run   Alpha   6000", "run/Alpha/"},
            {"bench-known", "  known Alpha 'VERDICT Y'", "known/Alpha/VERDICT Y"},
            // The modifier line is skipped and the row it decorates is not.
            // A ROW SPLIT BEFORE ITS QUOTE (#1594): the shape nobody has written, which
            // read as a `judge` with no verdict — and `judged()` calls that a `run` row.
            // THE CASE THAT WOULD HAVE CAUGHT #1594'S OWN MISTAKE (#1596). That unit's
            // prefix strip was LAZY and stopped at the word `run` inside a `vary` reason —
            // "two of its markers run the daemon" — producing a row whose class was the
            // word `the`. It was found by a census line, not by a case: the fixture reason
            // above is the word `why`, which contains no verb, so both spellings pass it.
            // Three of the four real `vary` reasons are prose long enough to contain a verb
            // by accident.
            {"bench-vary-reason-says-run", "  vary  'two of its markers run the daemon' \\\\\n"
                    + "  judge Alpha 'VERDICT X a=0'", "judge/Alpha/VERDICT X a=0"},
            {"bench-vary-reason-says-judge", "  vary  'the row this judge decorates' \\\\\n"
                    + "  judge Alpha 'VERDICT X a=0'", "judge/Alpha/VERDICT X a=0"},
            // A TRAILING BACKSLASH ON THE LAST LINE. Joining is unconditional, so the
            // fragment is held and never flushed — a row disappears silently, which is the
            // class of defect this whole thread is about (#1588, #1590, #1594). Pinned as
            // DROPPED rather than repaired: the file is malformed, the reader cannot invent
            // the missing continuation, and a case that says so is better than a
            // StringBuilder deciding by accident.
            {"bench-dangling-backslash", "  judge Alpha 'VERDICT X a=0'\n  judge Beta \\\\",
                "judge/Alpha/VERDICT X a=0"},
            {"bench-split-before-quote", "  judge Alpha \\\\\n        'VERDICT SPLIT a=0'",
                "judge/Alpha/VERDICT SPLIT a=0"},
            // A row continued AFTER its quote, which is the harmless half and must stay so.
            {"bench-split-after-quote", "  judge Alpha 'VERDICT X a=0' \\\\\n        --flag v",
                "judge/Alpha/VERDICT X a=0"},
            {"bench-vary-then-row", "  vary  'why' \\\n        --lines '^N ' --cut 1 \\\n"
                    + "  judge Alpha 'VERDICT X a=0'", "judge/Alpha/VERDICT X a=0"},
            // And the shape the shell reader lost: a plain row FOLLOWING a vary block.
            {"bench-vary-then-two", "  vary  'why' \\\n  judge Alpha 'VERDICT X a=0'\n"
                    + "  judge Alpha 'SELFCHECK VERDICT Y b=1'",
                "judge/Alpha/VERDICT X a=0|judge/Alpha/SELFCHECK VERDICT Y b=1"},
        };
        for (String[] c : tables) {
            Path f = tmp.resolve(c[0] + ".sh");
            Files.writeString(f, c[1] + "\n", StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (Probes.BenchRow r : Probes.benchRows(f)) {
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(r.verb()).append('/').append(r.probe()).append('/').append(r.verdict());
            }
            boolean ok = sb.toString().equals(c[2]);
            System.out.printf("LEAVE helper=%-24s %s%n     want=[%s]%n     got =[%s]%n",
                    c[0], ok ? "OK" : "BROKEN", c[2], sb);
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }
        // `judged()` is *has a quoted line*, not *the verb is judge* — the distinction two
        // of the three replaced regexes did not have to make, because they required a
        // verdict and never saw a `run` row at all.
        {
            Path f = tmp.resolve("judged.sh");
            Files.writeString(f, "  judge Alpha 'VERDICT X'\n  run   Beta 6000\n", StandardCharsets.UTF_8);
            List<Probes.BenchRow> rs = Probes.benchRows(f);
            boolean ok = rs.size() == 2 && rs.get(0).judged() && !rs.get(1).judged();
            System.out.printf("LEAVE helper=%-24s want=%-10s got=%-10s %s%n",
                    "bench-judged-predicate", "true/false",
                    rs.size() == 2 ? rs.get(0).judged() + "/" + rs.get(1).judged() : "size=" + rs.size(),
                    ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }
        Probes.leave("LEAVE SELFCHECK VERDICT " + (fail == 0 ? "READER_HOLDS" : "READER_BROKEN")
                + " cases=" + (pass + fail) + " failed=" + fail,
                fail == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }
    private LeaveContract() {}
}
