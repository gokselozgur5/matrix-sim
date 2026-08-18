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

    /** A row's verb and class: `  judge LedgerMirror 'LEDGER_ANOMALIES=0' 6000`. */
    private static final Pattern ROW =
            Pattern.compile("^\\s+(judge|known|run)\\s+(\\w+)\\b");

    /** The bench's own exemption grammar, read rather than re-listed. */
    private static final Pattern VARY = Pattern.compile("^\\s+vary\\b");

    /**
     * A long option in a COMPARING position, in the four spellings this directory writes.
     *
     * <p>{@code case "--x"}, {@code .equals("--x")}, {@code startsWith("--x")} and the
     * reversed {@code "--x".equals(…)}, which {@code NeutralDiff} uses and the first three
     * miss.
     */
    private static final Pattern DOOR = Pattern.compile(
            "(?:case\\s+|\\.equals\\(|\\.startsWith\\(|switch\\s*\\()\"(--[a-z][a-z0-9-]*)\""
                    + "|\"(--[a-z][a-z0-9-]*)\"\\.(?:equals|startsWith)\\(");

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].startsWith("--")) {
            System.err.println("FATAL unknown flag: " + args[0] + " (this probe takes [repo-root])");
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
        for (String line : Files.readAllLines(bench, StandardCharsets.UTF_8)) {
            if (VARY.matcher(line).find()) {
                continue;
            }
            Matcher m = ROW.matcher(line);
            if (m.find()) {
                probes.add(m.group(2));
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
                if (!row.contains(flag)) {
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

        // The populations ride their own line, unpinned (#1221). Every one of them moves
        // when a probe is added, and a census inside an exact-line row is a number people
        // learn to edit until the lane is quiet.
        System.out.println("CATALOG_FLAGS_CENSUS probes=" + probes.size()
                + " checked=" + checked
                + " no_flags=" + noFlags
                + " no_row=" + noRow
                + " no_source=" + noSource);

        // `checked_none=` IS the guard, and this probe would otherwise have been the
        // twenty-eighth row VacuousGuard reports as unable to tell a full population
        // from an empty one — a checker of catalogs joining the population of rows
        // that cannot say whether they read anything. A sweep that checked NO probe
        // must not print a clean count; that is #970's INSTRUMENTS_UNPROVEN and the
        // reason five siblings carry a `_none=` field.
        boolean held = noSource == 0 && noRow == 0 && checked > 0;
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

    /** Every long option this source COMPARES against, comments stripped (#1531). */
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
