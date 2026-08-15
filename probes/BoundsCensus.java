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
 * Probe: which constants in {@link matrix.core.Config} actually bind?
 *
 * <p>Three units found the same shape in one night and none of them was looking
 * for it (#1113). {@code BOND_MAX_EDGES} bounds what nothing may evict — once
 * the book fills, {@code evictable=0/64}, so the ceiling exists and can never
 * be reached against. A one-off species carries {@code populationCap = 1} that
 * no seeding loop reads and no ceiling consults (#1111). {@code
 * BITFLIP_TOLERANCE} has exactly one reader and nothing that would notice a
 * wrong value (#1092).
 *
 * <p>Each is a constant that LOOKS like a bound, is DOCUMENTED as a bound, and
 * cannot cause anything to happen. They are not dead code — they are read,
 * printed and reasoned about — which is what makes them harder to see than dead
 * code. D-006 puts arc tuning constants in {@code Config} and tunes them by
 * METRIC feel, which works because a tuned constant DOES something a METRIC
 * line shows. A constant that binds nothing is a knob wired to nothing, and
 * "tuned by feel" quietly becomes "set once and believed forever".
 *
 * <p>The census is deliberately crude about the hard half. #1113 asks two
 * questions per constant: how many readers, and can any of them produce a
 * different outcome for a different value. The first is decidable by reading
 * the tree. The second is not — it is the halting problem wearing a smaller
 * hat — so this probe answers the first exactly and approximates the second by
 * asking WHERE each reader is:
 *
 * <ul>
 *   <li><b>binds</b> — read somewhere that is not a print. A comparison, an
 *       arithmetic term, a loop bound: something whose value can change what
 *       the run does.</li>
 *   <li><b>reports</b> — every reader is inside a log, an instrument line or a
 *       string concatenation. The constant is published and never consulted;
 *       changing it changes what a line SAYS and not what the world DOES.</li>
 *   <li><b>unread</b> — nothing outside {@code Config} names it at all.</li>
 * </ul>
 *
 * <p>An {@code unread} or {@code reports}-only constant is not automatically
 * wrong: some exist to be published, and D-020's instrument lines are a
 * contract that things be printed. What is wrong is doing it SILENTLY. So the
 * verdict asks for one sentence: a constant that binds nothing must carry the
 * word {@code documentation} (or {@code not load-bearing}) in the comment above
 * it, exactly as {@code Bestiary.ONE_OFFS} now does for its cap. The lock is
 * not "every constant must bind" — that would be false and would get switched
 * off. It is "a knob wired to nothing says so where the next person will read
 * it".
 *
 * <pre>
 * java -cp out:probes/out BoundsCensus            census this tree's Config
 * java -cp out:probes/out BoundsCensus --list     every constant with its class
 * </pre>
 */
public final class BoundsCensus {

    /** One constant: its name, how many readers of each kind, and whether it says so. */
    private record Row(String name, int binding, int reporting, boolean declared) {

        String kind() {
            return binding > 0 ? "binds" : reporting > 0 ? "reports" : "unread";
        }
    }

    private static final Pattern DECL =
            Pattern.compile("^\\s*public static final\\s+[\\w.<>,\\[\\] ]+?\\s+([A-Z][A-Z0-9_]*)\\s*=");

    // A reader is "reporting" when the line it sits on is building text rather than deciding
    // anything. Crude on purpose and stated as such: a constant inside `"x=" + FOO` is being
    // published, and one inside `if (n > FOO)` is being consulted. The two do co-occur — a
    // line can both log and compare — and this counts such a line as BINDING, because the
    // false direction that matters is calling a live bound documentation.
    private static final Pattern REPORTING =
            Pattern.compile("(println|printf|\\.log\\(|emit\\(|String\\.format|\\+\\s*\"|\"\\s*\\+)");

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        boolean list = args.length > 0 && args[0].equals("--list");
        Path root = Path.of(".");
        Path config = root.resolve("src/matrix/core/Config.java");

        List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
        Map<String, Boolean> declared = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = DECL.matcher(lines.get(i));
            if (m.find()) {
                declared.put(m.group(1), saysItIsDocumentation(lines, i));
            }
        }

        // Every source, read once. Grepping per constant would be 81 walks of the tree; one
        // walk and 81 searches per line is the same answer for a fraction.
        //
        // Config reads ITSELF, and the first draft of this probe excluded it — which called
        // ECO_SCALE_MIN and ECO_SCALE_MAX unread while `scaleRefusal` compares against both,
        // eleven lines below the declarations. A constant consulted by its own file is
        // consulted. What is skipped is the DECLARATION line, because a name appearing in
        // `public static final int X = 1;` is not a reader of X.
        List<String> corpus = new ArrayList<>();
        for (Path dir : List.of(root.resolve("src"), root.resolve("probes"))) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        if (!DECL.matcher(line).find()) {
                            corpus.add(line);
                        }
                    }
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : declared.entrySet()) {
            String name = e.getKey();
            Pattern use = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            int binding = 0;
            int reporting = 0;
            for (String line : corpus) {
                if (!use.matcher(line).find()) {
                    continue;
                }
                if (REPORTING.matcher(line).find() && !decides(line, name)) {
                    reporting++;
                } else {
                    binding++;
                }
            }
            rows.add(new Row(name, binding, reporting, e.getValue()));
        }

        List<String> silent = new ArrayList<>();
        int binds = 0;
        int reports = 0;
        int unread = 0;
        for (Row r : rows) {
            switch (r.kind()) {
                case "binds" -> binds++;
                case "reports" -> reports++;
                default -> unread++;
            }
            if (!r.kind().equals("binds") && !r.declared()) {
                silent.add(r.name());
            }
            if (list || !r.kind().equals("binds")) {
                System.out.printf("BOUND %-28s %-8s binding=%d reporting=%d declared=%s%n",
                        r.name(), r.kind(), r.binding(), r.reporting(), r.declared() ? "yes" : "NO");
            }
        }

        System.out.println("BOUNDS constants=" + rows.size()
                + " binds=" + binds + " reports=" + reports + " unread=" + unread
                + " silent=" + silent.size());
        Probes.leave("VERDICT " + (silent.isEmpty() ? "EVERY_KNOB_IS_WIRED_OR_SAYS_SO" : "SILENT_KNOBS")
                + " silent=" + silent.size(), silent.isEmpty());
    }

    /**
     * Does this line consult the constant as well as print it?
     *
     * <p>A line can log and compare at once, and calling such a line reporting
     * would be the expensive error: a live bound filed as documentation. So a
     * comparison or an arithmetic operator anywhere beside the name wins.
     */
    private static boolean decides(String line, String name) {
        int at = line.indexOf(name);
        String around = line.substring(Math.max(0, at - 12),
                Math.min(line.length(), at + name.length() + 12));
        return around.matches(".*([<>]=?|==|!=|[*/%]|\\+\\+|--|\\bfor\\b|\\bif\\b|\\bwhile\\b).*");
    }

    /**
     * Does the comment above the declaration admit the constant binds nothing?
     *
     * <p>Read upward from the declaration to the first blank line, so a knob's
     * own paragraph is what is asked and not the one belonging to the constant
     * above it.
     */
    private static boolean saysItIsDocumentation(List<String> lines, int declAt) {
        for (int i = declAt - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                return false;
            }
            if (!line.startsWith("//") && !line.startsWith("*") && !line.startsWith("/*")) {
                return false;
            }
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("documentation") || lower.contains("not load-bearing")
                    || lower.contains("binds nothing")) {
                return true;
            }
        }
        return false;
    }

    private BoundsCensus() {}
}
