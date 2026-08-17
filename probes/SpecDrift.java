import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: does the shelf's spec still describe the world it specifies? (#260)
 *
 * <p>{@code docs/spec/README.md} carried this probe's name in the present tense
 * before it existed, and #1243 corrected the sentence rather than the absence —
 * *a spec nobody can falsify is not a spec, it is a wish with a filename*. The
 * shelf's rule since then: <b>a spec lands with the probe that reads it, in the
 * same pull request, or it does not land</b>. This is that probe, arriving with
 * the shelf's first inhabitant.
 *
 * <p>What it checks, for {@code docs/spec/instrument-lines.md} v1: the seven
 * families that document names are exactly the families a full run prints. A
 * family added to the daemon and not to the table is {@code unpredicted}; a
 * family the table names and the daemon stopped printing is {@code unseen}.
 *
 * <p><b>The table IS the data.</b> The roster is parsed out of the spec's own
 * markdown table rather than restated here, because a second copy of a list is
 * the defect this tree has found five times (#789's sweep, #880's workflow,
 * #1162's bench table). If the document is wrong, this probe is wrong with it —
 * and that is the point: the document is the thing under test.
 *
 * <p><b>The event log is not a family</b>, and the boundary has to be told
 * rather than inferred. D-020 names three instruments and only two of them are
 * line families; an event line opens with a bracketed tick and carries prose. At
 * seed 42 over 6,000 ticks a run prints 695 of them, so a naive first-token scan
 * reports several hundred families. The spec states the boundary in prose and
 * this probe applies it in one predicate — one rule, two readers.
 *
 * <p>Usage: {@code java -cp out:probes/out SpecDrift [ticks] [seed]} ·
 * {@code --spec FILE} points the reading at another document.
 */
public final class SpecDrift {

    private static final String DEFAULT_SPEC = "docs/spec/instrument-lines.md";

    /** Marks a table row the spec calls optional, so a shorter arity is not drift. */
    private static final String OPTIONAL = "?";

    /**
     * A roster row: the family in backticks, then the condition cell. The second
     * column is read rather than skipped, and that is the whole correctness of
     * this probe: two of the eight families are CONDITIONAL — `BIRTH` needs a
     * chronos recorder and a mind grown, `PERF` is emitted by the runner around
     * the world rather than by the world — so a checker demanding all eight from
     * one universe is red on a correct implementation, and one demanding only
     * what it saw would bless a roster with a family missing.
     */
    private static final Pattern ROSTER_ROW =
            Pattern.compile("^\\|\\s*`([A-Z][A-Z0-9_]*)`\\s*\\|\\s*([^|]*)\\|");

    /** A field table opens with a heading naming its family in backticks. */
    private static final Pattern TABLE_HEAD = Pattern.compile("^### `([A-Z][A-Z0-9_]*)`");

    /** A field row leads with its position, then the field name in backticks. */
    private static final Pattern FIELD_ROW =
            Pattern.compile("^\\|\\s*[0-9]+\\s*\\|\\s*`([a-z_]+)`\\s*\\|");

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        long ticks = 6_000;
        long seed = 42;
        String spec = DEFAULT_SPEC;

        int positional = 0;
        for (int i = 0; i < args.length; i++) {
            if ("--spec".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                spec = args[i];
            } else if (args[i].startsWith("--")) {
                System.exit(Probes.Outcome.REFUSED.code());
            } else if (positional++ == 0) {
                ticks = Long.parseLong(args[i]);
            } else {
                seed = Long.parseLong(args[i]);
            }
        }

        Set<String> declared = new TreeSet<>();
        Set<String> always = new TreeSet<>();
        roster(Path.of(spec), declared, always);
        Set<String> printed = families(ticks, seed);

        Set<String> unpredicted = new TreeSet<>(printed);
        unpredicted.removeAll(declared);
        // Only the UNCONDITIONAL families are demanded. A conditional one absent
        // from this universe is the spec working, not the world drifting.
        Set<String> unseen = new TreeSet<>(always);
        unseen.removeAll(printed);

        for (String f : unpredicted) {
            System.out.println("SPEC unpredicted " + f + " — the run prints it and the spec does not name it");
        }
        for (String f : unseen) {
            System.out.println("SPEC unseen " + f + " — the spec names it and the run did not print it");
        }

        // The population rides the census and the emptiness rides the verdict
        // (#1221): the number of families moves whenever the daemon grows one,
        // and a count on an exact-line row is a number people edit until the lane
        // is quiet — while a reading that parsed no roster at all must not print
        // the line that means the spec held.
        // THE FIELD TABLES (#604). Law 5 is additive-only: field order is the
        // contract, a reader keys on position, and an append is the one legal
        // change. So a conforming line's fields must BEGIN with the table's
        // fields in the table's order — a prefix check, not an equality one,
        // because a later version's append is legal and a v1 parser ignores it.
        //
        // Only families the spec has written a table for are checked. A family
        // with no table yet is a node of #255 that has not landed, and demanding
        // one would make this probe red for work nobody has done.
        int fieldDrift = 0;
        int tablesRead = 0;
        for (Map.Entry<String, List<String>> table : tables(Path.of(spec)).entrySet()) {
            tablesRead++;
            List<String> seen = fieldsOf(table.getKey(), ticks, seed);
            if (seen.isEmpty()) {
                continue;   // conditional family this universe did not print
            }
            for (int i = 0; i < table.getValue().size(); i++) {
                String want = table.getValue().get(i);
                boolean optional = want.startsWith(OPTIONAL);
                if (optional) {
                    want = want.substring(OPTIONAL.length());
                }
                String got = i < seen.size() ? seen.get(i) : "(absent)";
                if (optional && i >= seen.size()) {
                    continue;   // a legal shorter arity, not a drift
                }
                if (!want.equals(got)) {
                    fieldDrift++;
                    System.out.println("SPEC field " + table.getKey() + " position " + (i + 1)
                            + " — the table says " + want + " and the line says " + got);
                }
            }
        }

        System.out.println("SPEC_CENSUS spec=" + spec + " declared=" + declared.size()
                + " always=" + always.size() + " conditional=" + (declared.size() - always.size())
                + " printed=" + printed.size() + " tables=" + tablesRead
                + " ticks=" + ticks + " seed=" + seed);

        boolean held = unpredicted.isEmpty() && unseen.isEmpty() && !declared.isEmpty()
                && fieldDrift == 0;
        Probes.leave(String.format(
                "VERDICT SPEC_HOLDS unpredicted=%d unseen=%d field_drift=%d read_none=%d",
                unpredicted.size(), unseen.size(), fieldDrift, declared.isEmpty() ? 1 : 0), held);
    }

    /**
     * The families the spec's own roster table names, split by whether the table
     * says they are always printed.
     */
    private static void roster(Path spec, Set<String> declared, Set<String> always) throws IOException {
        if (!Files.isReadable(spec)) {
            return;
        }
        for (String line : Files.readAllLines(spec, StandardCharsets.UTF_8)) {
            Matcher m = ROSTER_ROW.matcher(line);
            if (m.find()) {
                declared.add(m.group(1));
                if (m.group(2).trim().startsWith("yes")) {
                    always.add(m.group(1));
                }
            }
        }
    }

    /**
     * The field tables the spec has written, family to field names in order.
     *
     * <p>A table is recognised by its heading — {@code ### `METRIC` — …} — and
     * its rows by a leading position number, which is what distinguishes a field
     * row from the roster's. Reading the heading rather than a marker keeps the
     * document the authority: a table nobody put under a heading is a table this
     * probe does not know about, which is visible as {@code tables=} rather than
     * silent.
     */
    private static Map<String, List<String>> tables(Path spec) throws IOException {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!Files.isReadable(spec)) {
            return out;
        }
        String family = null;
        for (String line : Files.readAllLines(spec, StandardCharsets.UTF_8)) {
            Matcher head = TABLE_HEAD.matcher(line);
            if (head.find()) {
                family = head.group(1);
                out.put(family, new ArrayList<>());
                continue;
            }
            if (family == null) {
                continue;
            }
            Matcher row = FIELD_ROW.matcher(line);
            if (row.find()) {
                // A field the table marks OPTIONAL is a legal arity's rider, not
                // a demand (#605). ZION's trace pair is the case: it prints
                // exactly when open pirate links exist AND both populations are
                // measurable, so a short line is a legal prefix and not drift.
                // The marker is a word in the domain cell rather than a column,
                // because the alternative is a sixth column on every table to
                // carry a fact four rows in the document need.
                out.get(family).add(line.contains("optional") ? OPTIONAL + row.group(1) : row.group(1));
            } else if (line.startsWith("## ")) {
                family = null;
            }
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }

    /** The field names of the first line of a family, in the order printed. */
    private static List<String> fieldsOf(String family, long ticks, long seed) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
        new Simulation(seed, buf, null).run(ticks);
        for (String line : buf.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.startsWith(family + " ")) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (String token : line.substring(family.length() + 1).trim().split(" +")) {
                int eq = token.indexOf('=');
                if (eq > 0) {
                    names.add(token.substring(0, eq));
                }
            }
            return names;
        }
        return List.of();
    }

    /**
     * The families a run actually prints. Its own private universe (clause 2),
     * quiet sink, explicit seed.
     */
    private static Set<String> families(long ticks, long seed) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
        new Simulation(seed, buf, null).run(ticks);
        Set<String> out = new TreeSet<>();
        for (String line : List.of(buf.toString(StandardCharsets.UTF_8).split("\n"))) {
            if (line.isBlank() || isEventLine(line)) {
                continue;
            }
            int space = line.indexOf(' ');
            String first = space < 0 ? line : line.substring(0, space);
            if (first.matches("[A-Z][A-Z0-9_]*")) {
                out.add(first);
            }
        }
        return out;
    }

    /**
     * The spec's boundary, applied. An event line opens with a bracketed tick:
     * it is narrative addressed to a reader, it carries prose, and it is not a
     * family. Told rather than inferred, because both kinds share one stream.
     */
    private static boolean isEventLine(String line) {
        return line.startsWith("[");
    }

    private SpecDrift() {}
}
