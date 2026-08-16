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
    };

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();

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

        boolean held = stale == 0 && refused == 0 && checked > 0;
        Probes.leave("VERDICT FIGURES_AGREE stale=" + stale + " refused=" + refused
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
