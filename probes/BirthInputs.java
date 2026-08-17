import matrix.Simulation;
import matrix.core.ChronosLine;

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
 * <p>It holds nothing but the file: no {@code Simulation}, no fold, no
 * replay. That is the reason D-058 opened the shelf — a record whose only
 * reader is the implementation that wrote it is not a record, it is a cache
 * — and what this probe can extract off the bytes is what a foreign reader
 * can extract.
 *
 * <p>Until #1053 it did that with a private copy of the fold's field
 * helpers, and the copy had gone permissive: #976 taught {@code
 * ReplayHarness} to refuse a line carrying a field twice, and this probe went
 * on reading the first occurrence and calling the record whole. It now reads
 * through {@link ChronosLine}, the grammar's one statement, so the two cannot
 * disagree about what a line says. The trade is deliberate and worth naming:
 * a second, independently written scanner no longer corroborates the first.
 * What survives is the question this probe exists to ask — are the five facts
 * ON the file, readable by someone holding only the file — and the drift that
 * cost is the drift that made the answer wrong.
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
                // A REFUSED invocation, not an unexercised one (#1199). The two ways this
                // probe prints NONE are different facts and were one exit code: `--file`
                // with no path is the operator typing the flag wrong, which DreamReader
                // spends 3 on (#1011), and a run with no births is a scenario that never
                // arose, which is 2. Collapsing them would tell a sweep that a typo is a
                // world without births.
                Probes.leave("VERDICT BIRTH_INPUTS_NONE no_file", Probes.Outcome.REFUSED);
            }
            Path file = Path.of(args[1]);
            source = file.getFileName().toString();
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } else {
            long ticks = args.length > 0 ? Probes.number(args[0], "ticks") : 6_000;
            long runSeed = args.length > 1 ? Probes.number(args[1], "seed") : 42;
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
            String kind = ChronosLine.string(line, "chronos");
            if (!"genesis".equals(kind) && !"birth".equals(kind)) {
                // Every other kind is somebody else's question; --audit is the
                // whole-file gate. These two are the ones this probe READS, so
                // these two are the ones it must not misread.
                continue;
            }
            String offGrammar = ChronosLine.offGrammar(line, kind);
            if (offGrammar != null) {
                // Refused, not counted short: a line the reader cannot trust
                // to say one thing says nothing, and a probe that scored it
                // would be publishing a number it read off a coin toss.
                System.out.println("OFFGRAMMAR line=" + (n + 1) + " " + offGrammar);
                System.out.println("VERDICT BIRTH_INPUTS_OFFGRAMMAR");
                return;
            }
            if ("genesis".equals(kind)) {
                seed = ChronosLine.number(line, "seed");
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
            String tick = ChronosLine.number(line, "tick");
            if (tick == null) {
                missing.add("tick");
            }
            String name = ChronosLine.string(line, "name");
            if (name == null || name.isEmpty()) {
                missing.add("name");
            }
            String family = ChronosLine.string(line, "family");
            if (family == null || family.isEmpty()) {
                missing.add("family");
            }
            // An EMPTY rack is a complete answer and not a missing one: it is
            // what a mind grown with no slot puts into the derivation. Only an
            // absent key is short.
            String rack = ChronosLine.string(line, "rack");
            if (rack == null) {
                missing.add("rack");
            }
            String id = ChronosLine.number(line, "id");
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
            Probes.leave("VERDICT BIRTH_INPUTS_NONE", Probes.Outcome.NEVER_AROSE);
        }
        Probes.leave(shortRecords == 0
                ? "VERDICT BIRTH_INPUTS_COMPLETE"
                : "VERDICT BIRTH_INPUTS_SHORT missing=" + firstMissing,
                shortRecords == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
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

    private BirthInputs() {}
}
