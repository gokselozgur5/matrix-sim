import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        System.out.println("SPEC_CENSUS spec=" + spec + " declared=" + declared.size()
                + " always=" + always.size() + " conditional=" + (declared.size() - always.size())
                + " printed=" + printed.size() + " ticks=" + ticks + " seed=" + seed);

        boolean held = unpredicted.isEmpty() && unseen.isEmpty() && !declared.isEmpty();
        Probes.leave(String.format(
                "VERDICT SPEC_HOLDS unpredicted=%d unseen=%d read_none=%d",
                unpredicted.size(), unseen.size(), declared.isEmpty() ? 1 : 0), held);
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
