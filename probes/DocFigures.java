import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: does a figure written in a document still match the command written
 * beside it? (#1283)
 *
 * <p>Three documents in this repository have carried a number that was true
 * when it was typed and false when it was read — {@code LedgerMirror}'s
 * javadoc (#1130), this directory's build block twice over (#1192, #1279,
 * #1284), and the determinism sample (#1277). Every one was found by a person
 * reading, and #1082 named the gap: <i>nothing in the repository compares a
 * written figure against the thing it counts.</i>
 *
 * <h2>The document opts in</h2>
 *
 * A checker that guessed which fenced block produces which nearby number would
 * report phantoms, which is worse than reporting nothing. So the claim is
 * declared, in a comment the rendered page does not show:
 *
 * <pre>
 *   &lt;!-- figure: ls probes/*.java | wc -l == 50 --&gt;
 * </pre>
 *
 * The command is run, its last line trimmed, and compared to the expected
 * text. Nothing else in the document is executed and nothing is inferred — a
 * paragraph with no marker is prose this probe has no opinion about.
 *
 * <h2>What may be run</h2>
 *
 * A whitelist of first words, not a sandbox: {@code ls}, {@code find},
 * {@code grep}, {@code wc}, {@code git}, {@code cat}, {@code sort},
 * {@code head}, {@code tail}, {@code sed}, {@code awk}, {@code basename},
 * {@code java}. A marker naming anything else is REFUSED and counted rather
 * than skipped, because a silent skip is how a checker comes to cover nothing
 * while printing green — {@code charset_checked=0} (#1207) and
 * {@code INSTRUMENTS_UNPROVEN} (#970) are that shape twice.
 *
 * <p>The whitelist is a whitelist and not a parser: the text between the
 * marker's colon and its {@code ==} goes to {@code sh -c} in full, pipes and
 * all, because these figures are pipelines by nature. That is safe here for
 * one reason and it is worth stating: the only documents read are this
 * repository's own READMEs, which are as trusted as the probe itself. Pointing
 * {@code --docs} at a tree you did not write is running that tree's shell.
 *
 * <pre>
 *   probes/DocFigures                     judge the READMEs that carry markers
 *   probes/DocFigures --docs a.md,b.md    judge other documents — the falsifier
 * </pre>
 */
public final class DocFigures {

    /** {@code <!-- figure: COMMAND == EXPECTED -->} */
    private static final Pattern MARKER = Pattern.compile(
            "<!--\\s*figure:\\s*(.+?)\\s*==\\s*(.+?)\\s*-->");

    private static final List<String> ALLOWED = List.of(
            "ls", "find", "grep", "wc", "git", "cat", "sort", "head", "tail",
            "sed", "awk", "basename", "java");

    /**
     * The documents that carry markers. {@code README.md} is the front door —
     * the first thing a stranger reads and the only document most of them will
     * read — and it is where the drift was worst: two transcripts stale at
     * once, one of them stale in MEANING rather than in a stamp (#1081, #922).
     */
    private static final String[] DEFAULT_DOCS = {
            "README.md", "probes/README.md", "tools/README.md",
            // #1625: two more, and the reason they were missing is that a probe
            // reading only the documents that already have markers cannot notice a
            // document that has none. `docs/DECISIONS.md` carries the sharpest
            // countable pair in the tree — a table of decisions beside the records
            // on disk — and had never been checked. `ROADMAP.md` stays out on
            // purpose: #1342 drew the boundary, and a marker is for a number whose
            // PRODUCER is a command, not for every integer somebody wrote down.
            "docs/DECISIONS.md", "PRINCIPLES.md",
            // #798: `docs/ARCHITECTURE.md` joins them, and it joins them BY #1342's
            // own bar rather than against it. The census chapter's fourth law does
            // not merely state an integer — it writes the command that produces it
            // and instructs a contributor to run it. That is the exact shape a
            // marker is for. It was also the shape that rotted: the law said 6, the
            // command printed 9, and it had been wrong since #786 opened `docs/spec/`
            // as a sanctioned kind without teaching the fence. A fence nobody runs
            // goes stale silently; a fence that cries wolf is retired quietly by
            // everyone. This document is admitted for the one law in it whose
            // producer is a command, not for the integers around it.
            "docs/ARCHITECTURE.md",
    };

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long started = System.nanoTime();

        List<String> docs = new ArrayList<>(List.of(DEFAULT_DOCS));
        for (int i = 0; i < args.length; i++) {
            if ("--docs".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                docs = new ArrayList<>(List.of(args[i].split(",")));
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        int checked = 0;
        int stale = 0;
        int refused = 0;
        for (String doc : docs) {
            Path path = Path.of(doc);
            if (!Files.isRegularFile(path)) {
                System.out.println("FIGURE_DOC missing " + doc);
                refused++;
                continue;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int n = 0; n < lines.size(); n++) {
                Matcher m = MARKER.matcher(lines.get(n));
                if (!m.find()) {
                    continue;
                }
                String command = m.group(1);
                String want = m.group(2);
                String head = command.trim().split("\\s+")[0];
                if (!ALLOWED.contains(head)) {
                    refused++;
                    System.out.println("FIGURE_REFUSED " + doc + ":" + (n + 1)
                            + " '" + head + "' is not on the whitelist");
                    continue;
                }
                checked++;
                String got = run(command);
                boolean agrees = want.equals(got);
                System.out.println("FIGURE " + doc + ":" + (n + 1) + " '" + command
                        + "' says " + want + ", prints " + got + (agrees ? "" : "  STALE"));
                if (!agrees) {
                    stale++;
                }
            }
        }

        // The cost, where the population already is (#1302). Two of these
        // markers run the daemon — 4,500 ticks and 20,000 — which took this
        // probe from about a second to five, and the only place that showed
        // was the sweep's total, mixed with sixty-three other rows.
        //
        // This is the probe most likely to grow expensive markers, because a
        // slow figure is exactly the kind a reader most wants pinned. `secs=`
        // rides the CENSUS and never the verdict: a timing on an exact-line
        // row goes red on a slow box, which teaches people to edit the number
        // (#1221).
        //
        // Wall-clock is read here and nowhere else in this probe. It is not a
        // determinism hazard for the same reason AllocMeter's byte counts are
        // not: the census line is exempt from the --twice byte compare by
        // being off the verdict, and the bench row greps the verdict alone.
        System.out.println("FIGURE_CENSUS docs=" + docs.size()
                + " secs=" + ((System.nanoTime() - started) / 1_000_000_000L));

        // `checked=` IS ON THE VERDICT SINCE #1623, and the census keeps a copy of
        // nothing: deleting a marker used to print `checked=4` here and a green
        // verdict there, so a figure could stop being checked with no line saying
        // so. This probe is OPT-IN by design — an unmarked paragraph is prose it has
        // no opinion about — and the cost of opt-in is that opting OUT is free. It
        // was free and silent; now it is free and loud.
        //
        // #1302's argument for keeping `secs=` off the verdict does NOT transfer.
        // A timing moves on a slow box for a reason nobody chose, and pinning it
        // teaches people to edit the number (#1221, #884). A marker count moves only
        // when a person edits a figure comment, deliberately, in a diff. That is the
        // `LEAVE_BY_HAND` case: what the pin buys is not correctness but one more
        // line an author has to write, with their name on it.
        //
        // `docs=` stays on the census. `--docs` takes a list, so it is an argument of
        // the RUN rather than a property of the tree, and a caller pointing this at
        // two documents would go red for having asked a smaller question.
        boolean held = stale == 0 && refused == 0 && checked > 0;
        Probes.leave("VERDICT FIGURES_AGREE checked=" + checked + " stale=" + stale + " refused=" + refused
                        + " checked_none=" + (checked == 0 ? 1 : 0),
                held ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /**
     * The command's LAST non-blank line of stdout, trimmed. Last rather than
     * first because a pipeline ending in {@code wc -l} prints one line and a
     * pipeline ending in a summary prints its answer at the bottom; a figure
     * quoted in prose is nearly always that bottom line.
     */
    private static String run(String command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        String answer = "";
        for (String line : out.split("\n")) {
            if (!line.isBlank()) {
                answer = line.trim();
            }
        }
        return answer;
    }

    private DocFigures() {}
}
