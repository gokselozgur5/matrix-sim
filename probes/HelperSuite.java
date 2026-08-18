import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: do the shared helpers in {@code Probes} behave as their javadocs say?
 *
 * <p>{@code Probes} is the one class every probe on the classpath can reach —
 * the comment strip, {@code joined}, {@code benchRows}, {@code verbShaped}, the
 * verb list. It has no {@code main} and no bench row, because a shared helper
 * class is not a probe, and so its cases went where a harness already existed:
 * {@code LeaveContract --selfcheck}, which had a temp directory and a counter.
 *
 * <h2>Why they moved</h2>
 *
 * #1580 called that a compromise rather than a preference and #1592 doubled
 * down on it. Three units later {@code LEAVE SELFCHECK} carried forty-odd cases
 * across <b>six</b> subjects under one verdict word about exit codes, and its
 * lock floor guarded their SUM: forty cases could vanish from one subject and be
 * replaced by forty in another with no gate moving. That is #1443's shape —
 * a number that cannot distinguish the population it is supposed to be about.
 *
 * <p>The other option was to give {@code Probes} a {@code main}. That changes
 * what a shared helper class IS: every probe on the classpath would gain an
 * entry point it must not use. A probe whose SUBJECT is the helpers costs one
 * row on a bench of seventy-eight and leaves {@code Probes} a helper.
 *
 * <h2>What it does not touch</h2>
 *
 * No world, no tick, no seed. Every case is a function of text this probe
 * writes into a temp directory itself; nothing here reads {@code probes/bench.sh}
 * or the catalog, because a case that reads the live tree fails for reasons that
 * are not about the helper.
 *
 * <p>Usage: {@code java -cp out:probes/out HelperSuite [--selfcheck]}
 */
public final class HelperSuite {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        // AN ARGUMENT DOOR OWES A REFUSAL (DoorRefusal, #1531).
        if (args.length > 0 && !args[0].equals("--selfcheck")) {
            System.err.println("FATAL unknown argument: " + args[0]
                    + " (this probe takes --selfcheck, or nothing, which means the same)");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        selfcheck(Files.createTempDirectory("helpersuite"));
    }

    private static void selfcheck(Path tmp) throws IOException {
        int pass = 0;
        int fail = 0;

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
            System.out.printf("HELPER strip=%-26s lines=%d/%d %s%n",
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
            System.out.printf("HELPER case=%-24s want=%-10s got=%-10s %s%n",
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
            // A VERDICT WHOSE OWN TEXT CONTAINS A VERB (#1598), and the one place the new
            // reading is strictly BETTER than the two it replaced. The greedy skip is
            // allowed only when the line opens with `vary`, so a plain row anchors at its
            // own verb and a `run Beta` inside its verdict is just text. The old stripper
            // had no such guard: it found the last verb anywhere on any line given to it.
            {"bench-verdict-says-run", "  judge Alpha 'VERDICT X run Beta'",
                "judge/Alpha/VERDICT X run Beta"},
            // The decorated form of the same text, where the skip IS allowed: the reason
            // may say anything and the row is still read from the verb that follows it.
            {"bench-vary-reason-and-verdict-say-run",
                "  vary  'markers run the daemon' \\\\\n  judge Alpha 'VERDICT X run Beta'",
                "judge/Alpha/VERDICT X run Beta"},
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
            System.out.printf("HELPER case=%-24s %s%n     want=[%s]%n     got =[%s]%n",
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
            System.out.printf("HELPER case=%-24s want=%-10s got=%-10s %s%n",
                    "bench-judged-predicate", "true/false",
                    rs.size() == 2 ? rs.get(0).judged() + "/" + rs.get(1).judged() : "size=" + rs.size(),
                    ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // `verbShaped` (#1600), the mirror reader. It is deliberately blind to WHICH word,
        // so its cases are about SHAPE: what counts as a row-shaped line and what does not.
        String[][] shapes = {
            {"shape-judge", "  judge Alpha 'VERDICT X'", "judge"},
            {"shape-vary", "  vary  'why' \\\\", ""},
            // A fourth verb is exactly what this exists for: it must come through NAMED,
            // because a count could not tell it from `vary`.
            {"shape-unknown-verb", "  measure Alpha 'VERDICT X'", "measure"},
            // A comment is not a row, whatever it says. `bench.sh` is prose-heavy and every
            // one of its comments opens with `#`.
            {"shape-comment", "  # judge Alpha is discussed here", ""},
            // Shell inside the file: a lowercase word followed by something that is not a
            // CamelCase class.
            {"shape-shell-line", "  local cls line", ""},
            // And the class shape is what makes the rule tight: a lowercase second word is
            // not a probe.
            {"shape-lowercase-second", "  judge alpha 'VERDICT X'", ""},
        };
        for (String[] c : shapes) {
            Path f = tmp.resolve(c[0] + ".sh");
            Files.writeString(f, c[1] + "\n", StandardCharsets.UTF_8);
            String got = String.join(",", Probes.verbShaped(f));
            boolean ok = got.equals(c[2]);
            System.out.printf("HELPER case=%-24s want=%-10s got=%-10s %s%n",
                    c[0], c[2].isEmpty() ? "<none>" : c[2], got.isEmpty() ? "<none>" : got,
                    ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // THE LIST AND THE PATTERN ARE ONE THING (#1602), asserted rather than assumed:
        // every word in `BENCH_VERB_WORDS` must be a verb `benchRows` actually reads. The
        // pattern is BUILT from the list, so this can only fail if somebody rebuilds one
        // and not the other — which is the state the tree was in for three units, with a
        // `Set.of` in `CatalogFlags` copying the same three words.
        for (String verb : Probes.BENCH_VERB_WORDS) {
            Path f = tmp.resolve("verb-" + verb + ".sh");
            Files.writeString(f, "  " + verb + " Alpha 'VERDICT X'\n", StandardCharsets.UTF_8);
            List<Probes.BenchRow> rs = Probes.benchRows(f);
            boolean ok = rs.size() == 1 && rs.get(0).verb().equals(verb);
            System.out.printf("HELPER case=%-24s want=%-10s got=%-10s %s%n",
                    "verb-list:" + verb, verb,
                    rs.isEmpty() ? "<none>" : rs.get(0).verb(), ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }
        Probes.leave("HELPER SELFCHECK VERDICT " + (fail == 0 ? "HELPERS_HOLD" : "HELPERS_BROKEN")
                + " cases=" + (pass + fail) + " failed=" + fail,
                fail == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    private HelperSuite() {}
}
