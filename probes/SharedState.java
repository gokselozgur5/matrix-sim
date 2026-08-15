import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Probe: what does one universe share with the next one in the same JVM?
 *
 * <p>#1147 found two — {@code FlockMovement} and {@code SwarmMovement} held
 * their neighbour scratch buffer as an instance field on a singleton, so every
 * {@code Simulation} in the process shared one list. The visible symptom was a
 * {@code ConcurrentModificationException}; the measured one was worse, and it
 * is the reason this probe exists: seed 42 produced a DIFFERENT CHAIN at link 1
 * with no exception at all.
 *
 * <p>Both were found because a sweep happened to run them in parallel. That is
 * a coincidence, not a search (#1148). {@link TwoWorlds} now guards the
 * BEHAVIOUR — two worlds, two threads, chains equal link for link — and it can
 * only see what four seeds reach in 2,000 ticks. This is the other half: read
 * the tree, and name every place where sharing is possible at all.
 *
 * <p>Three shapes, because they fail for different reasons:
 *
 * <ol>
 *   <li><b>A mutable static field.</b> {@code static int x} with no {@code
 *       final}: one value for the whole process. Three exist and all three are
 *       process-wide BY DESIGN — the {@code --neutral} flag and the eco dial —
 *       which is exactly why they need saying rather than finding.</li>
 *   <li><b>A final reference to a mutable object.</b> {@code static final int[]}
 *       and {@code static final StringBuilder} are constants in name only: the
 *       reference cannot move and the contents can. {@code List.of(...)} is
 *       genuinely immutable and is not flagged.</li>
 *   <li><b>An instance field on a singleton.</b> The shape that actually bit.
 *       {@code public static final X INSTANCE} plus {@code private final List}
 *       is one list per process wearing the syntax of one list per object.</li>
 * </ol>
 *
 * <p>None of these is automatically wrong — a dial is meant to be process-wide,
 * and a lookup table that is only ever read is safe however it is declared. So
 * the rule is the one {@code BoundsCensus} uses for a knob that binds nothing:
 * <b>say so where the next person reads it.</b> A comment above the declaration
 * carrying {@code read-only}, {@code process-wide} or {@code per-thread} is the
 * whole requirement, and it is the sentence that was missing from the two
 * buffers for as long as they were wrong.
 *
 * <pre>
 * java -cp out:probes/out SharedState          audit src/
 * java -cp out:probes/out SharedState --list   every candidate, declared or not
 * </pre>
 */
public final class SharedState {

    private record Site(String file, int line, String kind, String text, boolean declared) {}

    // A mutable static: `static` without `final`, holding something.
    private static final Pattern MUTABLE_STATIC = Pattern.compile(
            "^\\s*(?:private|public|protected)?\\s*static\\s+(?!final\\b)[\\w.<>,\\[\\] ]+\\s+"
                    + "([a-z]\\w*)\\s*[=;]");

    // A final reference to something whose contents can move. `List.of` and `Map.of` are
    // immutable by construction and are not this; an array literal always is.
    private static final Pattern MUTABLE_FINAL = Pattern.compile(
            "^\\s*(?:private|public|protected)?\\s*static\\s+final\\s+"
                    + "(?:\\w+\\[\\]|StringBuilder|ArrayList|HashMap|HashSet|AtomicInteger|AtomicLong)"
                    + "[\\w.<>,\\[\\] ]*\\s+([A-Z_a-z]\\w*)\\s*[=;]");

    private static final Pattern SINGLETON = Pattern.compile(
            "^\\s*public static final \\w+ INSTANCE\\s*=");

    private static final Pattern INSTANCE_FIELD = Pattern.compile(
            "^\\s*private\\s+(?:final\\s+)?(?!static)[\\w.<>,\\[\\] ]+\\s+([a-z]\\w*)\\s*[=;]");

    private static final List<String> DECLARATIONS =
            List.of("read-only", "read only", "process-wide", "per-thread", "immutable",
                    "one per process", "shared by design");

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        boolean list = args.length > 0 && args[0].equals("--list");

        List<Site> sites = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                sites.addAll(scan(p));
            }
        }

        int undeclared = 0;
        for (Site s : sites) {
            if (!s.declared()) {
                undeclared++;
            }
            if (list || !s.declared()) {
                System.out.println("SHARED " + s.file() + ":" + s.line()
                        + " kind=" + s.kind()
                        + " " + (s.declared() ? "declared" : "UNDECLARED")
                        + " — " + s.text().trim());
            }
        }

        System.out.println("SHARED_STATE files_scanned=" + count("src")
                + " sites=" + sites.size() + " undeclared=" + undeclared);
        Probes.leave("VERDICT " + (undeclared == 0 ? "EVERY_SHARE_IS_DECLARED" : "UNDECLARED_SHARING")
                + " undeclared=" + undeclared, undeclared == 0);
    }

    private static List<Site> scan(Path p) throws IOException {
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        String file = p.toString();
        List<Site> out = new ArrayList<>();
        boolean singleton = false;
        for (String line : lines) {
            if (SINGLETON.matcher(line).find()) {
                singleton = true;
                break;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String kind = null;
            if (MUTABLE_STATIC.matcher(line).find()) {
                kind = "mutable-static";
            } else if (MUTABLE_FINAL.matcher(line).find()) {
                kind = "final-ref-mutable";
            } else if (singleton && INSTANCE_FIELD.matcher(line).find()) {
                kind = "singleton-instance-field";
            }
            if (kind != null) {
                out.add(new Site(file, i + 1, kind, line, declaredAbove(lines, i)));
            }
        }
        return out;
    }

    /**
     * Does the comment paragraph above this declaration say how it is shared?
     *
     * <p>Read upward to the first blank line, so a field's own paragraph is what
     * is asked rather than the one belonging to the field above it — the same
     * reading {@code BoundsCensus} does for a knob that binds nothing.
     */
    private static boolean declaredAbove(List<String> lines, int at) {
        for (int i = at - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                return false;
            }
            if (!line.startsWith("//") && !line.startsWith("*") && !line.startsWith("/*")) {
                return false;
            }
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            for (String phrase : DECLARATIONS) {
                if (lower.contains(phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long count(String dir) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(dir))) {
            return files.filter(f -> f.toString().endsWith(".java")).count();
        }
    }

    private SharedState() {}
}
