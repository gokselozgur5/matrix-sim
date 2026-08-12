import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: can a reader holding nothing but the recording state the birth
 * event the die was keyed to?
 *
 * <p>#847's finding was that it could not. The birth record carried tick,
 * name and family; the derivation the #212 ruling installed reads five facts
 * — seed, tick, rack unit, growth ordinal, name — so a reader with a complete
 * recording could not say why one mind woke and another did not. Two of the
 * five were nowhere on the file.
 *
 * <p>This probe is the leash on that. It walks a chronos JSONL and, for every
 * birth, prints the five inputs it can state <b>from the file alone</b>: the
 * seed off genesis, and tick, rack unit, ordinal and name-at-birth off the
 * birth line. A field that is not there is named, by line, and the verdict
 * says the record is short.
 *
 * <p>It reads the file with its own scanner rather than {@code ReplayHarness}'s
 * for the reason D-058 opened the shelf: a record whose only reader is the
 * implementation that wrote it is not a record, it is a cache. This is the
 * stranger — a hundred lines that have never seen a {@code Simulation} — and
 * what it can extract is what a foreign implementation can extract.
 *
 * <p>What it does <b>not</b> check: that the five facts mix into the right
 * long. They cannot be mixed on this build, because the mixer is {@code
 * AcceptanceLoop.birthKey} and that lands with #764/PR #787. This probe holds
 * the record side of the contract — the inputs are all present and readable —
 * and the day the mixer lands, re-deriving the key from these five lines is
 * arithmetic over an artifact this probe already proved sufficient.
 *
 * <pre>
 * java -cp out:probes/out BirthInputs [ticks] [seed]   own universe, recorded in memory
 * java -cp out:probes/out BirthInputs --file &lt;path&gt;    audit a recording on disk
 * </pre>
 *
 * The default form records its own universe rather than taking a file,
 * because a probe that needs an artifact somebody remembered to produce is a
 * probe the bench cannot judge. The {@code --file} form is the same reader
 * pointed at a recording that already exists — which is how a pre-#847 file
 * is shown to be short.
 *
 * Exit is always 0, per the bench contract: the verdict line is the judge.
 */
public final class BirthInputs {

    /** The five facts the die keys to, as this reader can state them off the file. */
    private record Inputs(long tick, String name, String family, String rack, long id, int line) {}

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        String source;
        List<String> lines;
        if (args.length > 0 && args[0].equals("--file")) {
            if (args.length < 2) {
                System.out.println("usage: BirthInputs --file <recording.jsonl>");
                System.out.println("VERDICT BIRTH_INPUTS_NONE no_file");
                return;
            }
            Path file = Path.of(args[1]);
            source = file.getFileName().toString();
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } else {
            long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
            long runSeed = args.length > 1 ? Long.parseLong(args[1]) : 42;
            source = "own";
            lines = record(runSeed, ticks);
        }

        String seed = null;
        List<Inputs> complete = new ArrayList<>();
        int shortRecords = 0;
        String firstMissing = null;

        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty()) {
                continue;
            }
            String kind = str(line, "chronos");
            if ("genesis".equals(kind)) {
                seed = num(line, "seed");
                continue;
            }
            if (!"birth".equals(kind)) {
                continue;
            }
            // The seed is a fact about the universe, not about the birth, so a
            // recording that never named one leaves every birth in it short of
            // the same field. That is reported per birth on purpose: the
            // question is what a reader can say about THIS birth.
            List<String> missing = new ArrayList<>();
            if (seed == null) {
                missing.add("seed");
            }
            String tick = num(line, "tick");
            if (tick == null) {
                missing.add("tick");
            }
            String name = str(line, "name");
            if (name == null || name.isEmpty()) {
                missing.add("name");
            }
            String family = str(line, "family");
            if (family == null || family.isEmpty()) {
                missing.add("family");
            }
            // An EMPTY rack is a complete answer and not a missing one: it is
            // what a mind grown with no slot puts into the derivation. Only an
            // absent key is short.
            String rack = str(line, "rack");
            if (rack == null) {
                missing.add("rack");
            }
            String id = num(line, "id");
            if (id == null) {
                missing.add("id");
            }
            if (!missing.isEmpty()) {
                shortRecords++;
                if (firstMissing == null) {
                    firstMissing = String.join(",", missing);
                }
                System.out.println("SHORT line=" + (n + 1) + " missing=" + String.join(",", missing));
                continue;
            }
            Inputs in = new Inputs(Long.parseLong(tick), name, family, rack, Long.parseLong(id), n + 1);
            complete.add(in);
            System.out.println("INPUT seed=" + seed
                    + " tick=" + in.tick()
                    + " rack=\"" + in.rack() + "\""
                    + " id=" + in.id()
                    + " name=\"" + in.name() + "\""
                    + " family=" + in.family());
        }

        int births = complete.size() + shortRecords;
        System.out.println("BIRTHINPUTS from=" + source
                + " seed=" + (seed == null ? "-" : seed)
                + " births=" + births
                + " complete=" + complete.size()
                + " short=" + shortRecords);
        if (births == 0) {
            // Silence is not testimony: a file with no births proves nothing
            // about whether a birth is fully recorded, and must not print the
            // line a passing run prints.
            System.out.println("VERDICT BIRTH_INPUTS_NONE");
            return;
        }
        System.out.println(shortRecords == 0
                ? "VERDICT BIRTH_INPUTS_COMPLETE"
                : "VERDICT BIRTH_INPUTS_SHORT missing=" + firstMissing);
    }

    /**
     * A universe, recorded into memory and handed back as its own JSONL lines.
     * The daemon's stdout goes nowhere: the question is what the RECORD holds,
     * so the run's own narration is not evidence here and is not collected.
     */
    private static List<String> record(long seed, long ticks) {
        ByteArrayOutputStream chronos = new ByteArrayOutputStream(1 << 20);
        OutputStream quiet = OutputStream.nullOutputStream();
        new Simulation(seed, quiet, null, chronos).run(ticks);
        return List.of(chronos.toString(StandardCharsets.UTF_8).split("\n"));
    }

    /**
     * The string value of {@code "key":"..."}, or null when the key is not on
     * the line. Deliberately the smallest thing that reads our own writer's
     * grammar — the escapes the recorder emits, and nothing else.
     */
    private static String str(String line, String key) {
        String needle = "\"" + key + "\":\"";
        int i = line.indexOf(needle);
        if (i < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + needle.length(); j < line.length(); j++) {
            char c = line.charAt(j);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && j + 1 < line.length()) {
                char e = line.charAt(++j);
                if (e == 'u' && j + 4 < line.length()) {
                    sb.append((char) Integer.parseInt(line.substring(j + 1, j + 5), 16));
                    j += 4;
                } else {
                    sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return null; // unterminated — the reader cannot state it, so it is missing
    }

    /** The numeric value of {@code "key":N}, or null when the key is absent or not a number. */
    private static String num(String line, String key) {
        String needle = "\"" + key + "\":";
        int i = line.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        int end = start;
        if (end < line.length() && line.charAt(end) == '-') {
            end++;
        }
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        return end == start ? null : line.substring(start, end);
    }

    private BirthInputs() {}
}
