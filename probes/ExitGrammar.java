import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Probe: does {@code $?} mean one thing per value, and where it does not, does
 * the tree say so? (#1219)
 *
 * <p>Three populations of program in this tree exit with codes a script
 * branches on: the probes, the twelve tools, and the teleprinter. #1241
 * measured them and made {@code 2} universal — <em>the invocation was
 * refused</em> — and {@code tools/README.md}'s table names all three
 * populations in its own opening sentence. The probes do not spend 2 that way.
 * They spend it for {@code NEVER_AROSE}, and they have since #1204.
 *
 * <h2>The claim this probe was written to falsify</h2>
 *
 * Both documents assert the collision is gone. {@code probes/README.md} says
 * <em>the two families of program in this tree agree where they overlap: a
 * script branching on {@code $?} across probes/ and tools/ reads one meaning
 * per value</em>, and {@code Probes.Outcome.REFUSED}'s javadoc says the same in
 * the same words. Both were written when {@code REFUSED} was given 3 to match
 * {@code DreamReader}'s {@code EXIT_USAGE}.
 *
 * <p>{@code DreamReader} is one program with a grammar of its own. The shell
 * tools are eleven that spend 2 for a refusal, which {@code tools/README.md}
 * calls the largest existing agreement in the tree. Aligning with the smaller
 * incumbent moved the collision without closing it: 2 is NEVER_AROSE here and
 * REFUSED there, 3 is REFUSED here and <em>the answer could not be read</em>
 * there. Two values, two meanings each, and two documents saying otherwise.
 *
 * <h2>What it judges, and what it refuses to decide</h2>
 *
 * Unifying the grammars is a decision about the bench's contract — whether
 * NEVER_AROSE gives up 2 — and D-037 puts that with the Architect. This probe
 * makes the state visible and keeps it honest instead:
 *
 * <ol>
 *   <li>the grammar block in {@code probes/README.md} matches
 *       {@code Probes.Outcome} exactly, both directions, so the documented
 *       grammar cannot drift from the one the probes spend;</li>
 *   <li>every code where the two families disagree is DECLARED, in a marker
 *       the rendered page does not show:
 *       {@code <!-- grammar-boundary: 2 3 -->}. A collision nobody wrote down
 *       is red. A collision written down is a boundary, which is what #1219
 *       asked for when unification is not yet decided.</li>
 * </ol>
 *
 * <p>The marker is the whole mechanism. Change either grammar and the declared
 * set stops matching the measured one, which is the only way a boundary stays
 * true after the unit that drew it — #1337's finding, one file over.
 *
 * <h2>Why the census counts literal exits</h2>
 *
 * {@code Outcome.code()} exists so the grammar has one home, and eight probes
 * still write the digit at the call site. {@code CensusBlocks} alone spends a
 * literal 2 and two literal 3s. That is reported and not judged: some of those
 * literals are genuinely local codes the enum does not own, telling them apart
 * means reading each call site, and a checker that guesses is what this
 * directory refuses. The number is here so the next reader knows the enum is
 * the home of the grammar and not yet the home of every code.
 *
 * <pre>
 *   probes/ExitGrammar                    judge the tree's own documents
 *   probes/ExitGrammar --probes-doc F     read another grammar page — the falsifier
 * </pre>
 */
public final class ExitGrammar {

    private static final String PROBES_DOC = "probes/README.md";
    private static final String TOOLS_DOC = "tools/README.md";
    private static final String ENUM_SRC = "probes/Probes.java";

    /** {@code HELD(0),} — the enum's own spelling, which is the grammar's home. */
    private static final Pattern ENUM_ENTRY = Pattern.compile("^\\s{8}([A-Z][A-Z_]*)\\((\\d+)\\)[,;]");

    /** {@code     NEVER_AROSE  2} — the block the grammar page prints. */
    private static final Pattern DOC_ENTRY = Pattern.compile("^\\s{4}([A-Z][A-Z_]*)\\s+(\\d+)\\s*$");

    /** {@code | **2** | **the invocation was refused** — …} in the universal table. */
    private static final Pattern TOOL_ROW = Pattern.compile("^\\|\\s*\\**(\\d+)\\**\\s*\\|\\s*(.*?)\\s*\\|");

    /** {@code <!-- grammar-boundary: 2 3 -->} — the codes the tree declares as split. */
    private static final Pattern BOUNDARY = Pattern.compile("<!--\\s*grammar-boundary:\\s*([0-9 ]*)-->");

    /** A digit handed straight to {@code System.exit}, rather than through {@code Outcome}. */
    private static final Pattern LITERAL_EXIT = Pattern.compile("System\\.exit\\((\\d)\\)");

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();

        String probesDoc = PROBES_DOC;
        for (int i = 0; i < args.length; i++) {
            if ("--probes-doc".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                probesDoc = args[i];
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        Map<Integer, String> enumCodes = pairs(read(ENUM_SRC), ENUM_ENTRY);
        Map<Integer, String> docCodes = pairs(read(probesDoc), DOC_ENTRY);
        Map<Integer, String> toolCodes = universalTable(read(TOOLS_DOC));

        // 1. The page and the enum. Both directions, because a page that lost a
        //    row and a page that grew one are the same defect wearing different
        //    signs, and only one of them is visible from a count.
        int drift = 0;
        for (Map.Entry<Integer, String> e : enumCodes.entrySet()) {
            if (!e.getValue().equals(docCodes.get(e.getKey()))) {
                drift++;
                System.out.println("GRAMMAR_DRIFT " + ENUM_SRC + " spends " + e.getKey() + " as "
                        + e.getValue() + " and " + probesDoc + " says "
                        + docCodes.getOrDefault(e.getKey(), "nothing"));
            }
        }
        for (Map.Entry<Integer, String> e : docCodes.entrySet()) {
            if (!enumCodes.containsKey(e.getKey())) {
                drift++;
                System.out.println("GRAMMAR_DRIFT " + probesDoc + " documents " + e.getKey() + " as "
                        + e.getValue() + " and the enum has no such code");
            }
        }

        // 2. The two families, where the universal table says a value belongs to
        //    everyone. The probe's word for a code is its enum NAME; the tool's
        //    is the cell's prose. `refused` inside "the invocation was refused"
        //    is the match — a whole-word read, so REFUSED at 3 does not quietly
        //    satisfy a row about reading answers.
        List<Integer> collisions = new ArrayList<>();
        for (Map.Entry<Integer, String> e : enumCodes.entrySet()) {
            String tool = toolCodes.get(e.getKey());
            if (tool == null) {
                continue;       // a code the universal table does not claim is local, and free
            }
            if (!mentions(tool, e.getValue())) {
                collisions.add(e.getKey());
            }
        }

        List<Integer> declared = declaredBoundary(read(probesDoc));
        int undeclared = 0;
        for (int code : collisions) {
            if (!declared.contains(code)) {
                undeclared++;
                System.out.println("GRAMMAR_COLLISION " + code + " is " + enumCodes.get(code)
                        + " in probes/ and \"" + toolCodes.get(code) + "\" in tools/, and no boundary declares it");
            }
        }
        // The mirror: a boundary that declares a code the families agree on is a
        // rule protecting nothing, and it is how a declaration outlives the
        // collision it was written for.
        for (int code : declared) {
            if (!collisions.contains(code)) {
                undeclared++;
                System.out.println("GRAMMAR_PHANTOM " + code + " is declared split and the two families agree on it");
            }
        }

        int literals = 0;
        int files = 0;
        for (Path probe : javaFiles()) {
            files++;
            Matcher m = LITERAL_EXIT.matcher(Files.readString(probe, StandardCharsets.UTF_8));
            while (m.find()) {
                literals++;
            }
        }

        // Descriptions, not claims (#1221): every one of these moves when
        // somebody writes an ordinary probe, and none of those moves is a
        // finding.
        System.out.println("GRAMMAR_CENSUS codes=" + enumCodes.size() + " doc_rows=" + docCodes.size()
                + " universal=" + toolCodes.size() + " declared=" + declared.size()
                + " literal_exits=" + literals + " files=" + files);

        boolean read = !enumCodes.isEmpty() && !toolCodes.isEmpty();
        boolean held = drift == 0 && undeclared == 0 && read;
        Probes.leave("VERDICT THE_GRAMMAR_HAS_ONE_HOME doc_drift=" + drift
                        + " undeclared=" + undeclared
                        + " checked_none=" + (read ? 0 : 1),
                held ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /**
     * Whole-word, case-insensitive. A substring read would let {@code REFUSED}
     * satisfy a cell containing "unrefused", and more usefully it would let the
     * word appear anywhere in a long cell — the universal table's rows carry a
     * sentence of examples after the meaning, and the meaning is the part that
     * has to agree.
     */
    private static boolean mentions(String cell, String name) {
        return Pattern.compile("\\b" + Pattern.quote(name.toLowerCase()) + "\\b")
                .matcher(cell.toLowerCase()).find();
    }

    /** Every {@code NAME(n)} pair a pattern finds, in the order the file states them. */
    private static Map<Integer, String> pairs(List<String> lines, Pattern p) {
        Map<Integer, String> found = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                found.put(Integer.parseInt(m.group(2)), m.group(1));
            }
        }
        return found;
    }

    /**
     * The rows of the universal table, and only those. The table's own sentence
     * draws the line — the first four codes are universal and everything above
     * is local — so a row for 4 or 5 is not a claim about the probes and is not
     * read here. Reading them would report a collision on every local code in
     * the tree, which is the false-positive flood that gets a checker deleted.
     */
    private static Map<Integer, String> universalTable(List<String> lines) {
        Map<Integer, String> found = new LinkedHashMap<>();
        boolean inTable = false;
        for (String line : lines) {
            if (line.startsWith("| code | meaning |")) {
                inTable = true;
                continue;
            }
            if (inTable && !line.startsWith("|")) {
                break;
            }
            if (!inTable) {
                continue;
            }
            Matcher m = TOOL_ROW.matcher(line);
            if (m.find()) {
                int code = Integer.parseInt(m.group(1));
                if (code <= 3) {
                    found.put(code, m.group(2));
                }
            }
        }
        return found;
    }

    private static List<Integer> declaredBoundary(List<String> lines) {
        List<Integer> declared = new ArrayList<>();
        for (String line : lines) {
            Matcher m = BOUNDARY.matcher(line);
            if (m.find()) {
                for (String tok : m.group(1).trim().split("\\s+")) {
                    if (!tok.isEmpty()) {
                        declared.add(Integer.parseInt(tok));
                    }
                }
            }
        }
        return declared;
    }

    private static List<String> read(String path) throws IOException {
        Path p = Path.of(path);
        return Files.isRegularFile(p) ? Files.readAllLines(p, StandardCharsets.UTF_8) : List.of();
    }

    /** Sorted, so two runs read the same files in the same order. */
    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.list(Path.of("probes"))) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    private ExitGrammar() {}
}
