import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * D-047's teleprinter (gate #217, accepted): the dream, rendered as prose.
 *
 * The perception feed was always the system's true output (D-021) and its only
 * reader was a pipeline. This tool is the missing reader. It boots its own
 * quiet universe, follows one mind with the daemon's own --follow tap, and
 * folds what it catches into one page.
 *
 * Laws it lives under:
 * <ul>
 *   <li>D-019 stands untouched — observer-only: the reader consumes
 *       instruments, never entities, and not one domain byte moves.</li>
 *   <li>Deterministic prose: same args, same day, byte for byte. No wall
 *       clock, no rng of its own, everything in capture order.</li>
 *   <li>No invented facts. Every sentence derives from a captured line; where
 *       the feed is silent, the silence is written down.</li>
 * </ul>
 *
 * Build and run:
 * <pre>
 *   javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')
 *   javac -encoding UTF-8 --release 17 -cp out -d tools/dreamreader/out \
 *       tools/dreamreader/*.java
 *   java -cp out:tools/dreamreader/out DreamReader --pilot NAME [--seed N] [--ticks N]
 * </pre>
 *
 * <h2>The output contract</h2>
 *
 * What makes a page evidence rather than output:
 *
 * <ul>
 *   <li><b>No wall clock, no rng of the reader's own, iteration strictly in
 *       capture order.</b> The same arguments render the same day, byte for
 *       byte, forever — on any machine, in any locale, in any time zone.</li>
 *   <li><b>{@code --out FILE} writes exactly the bytes stdout gets.</b> Not a
 *       re-render: the same {@code String}, encoded once. A tool with two
 *       output paths has two truths.</li>
 *   <li><b>Byte ownership (D-010):</b> UTF-8 end to end, {@code Locale.ROOT}
 *       for every case change, explicit {@code \n} — never
 *       {@code System.lineSeparator()}, never a default charset.</li>
 *   <li><b>The exit grammar is part of the contract:</b> 0 a day rendered,
 *       2 the record holds nobody by that name, 1 the golden day drifted. A
 *       typo in a pilot's name must be a refusal, not an empty page that
 *       reads like a quiet life.</li>
 *   <li><b>{@code --check-golden FILE}</b> pins the SENTENCES. Determinism
 *       only promises that one build renders one day the same way twice; it
 *       says nothing about the next edit to the voice, which would change
 *       every rendered day in silence. The golden day is the guard, and it
 *       is a plain file so the drift is readable as a diff.</li>
 * </ul>
 *
 * Exit codes: 0 a day rendered · 1 the golden day drifted · 2 nobody by that
 * name.
 */
public final class DreamReader {

    public static void main(String[] args) throws Exception {
        String pilot = null;
        long seed = 42;
        long ticks = 6_000;
        String outPath = null;
        String goldenPath = null;
        boolean captureOnly = false;
        boolean factsOnly = false;
        Voice voice = Voice.COLD;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pilot" -> pilot = value(args, ++i);
                case "--seed" -> seed = Long.parseLong(value(args, ++i));
                case "--ticks" -> ticks = Long.parseLong(value(args, ++i));
                case "--out" -> outPath = value(args, ++i);
                case "--check-golden" -> goldenPath = value(args, ++i);
                case "--capture-only" -> captureOnly = true;
                case "--facts" -> factsOnly = true;
                case "--voice" -> {
                    String name = value(args, ++i);
                    voice = Voice.named(name);
                    if (voice == null) {
                        System.err.println("unknown voice: " + name + " (cold, none)");
                        System.exit(2);
                    }
                }
                case "--help" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("unknown flag: " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }
        if (pilot == null || pilot.isBlank()) {
            System.err.println("--pilot NAME is required — a teleprinter prints somebody");
            usage();
            System.exit(2);
        }

        Capture capture = Capture.of(pilot, seed, ticks);

        // Three views of one day, in the order the reader builds them: the
        // capture's own counts, the fold's facts, and a voice over the facts.
        // The first two exist so the third can be checked rather than trusted.
        String page;
        if (captureOnly) {
            page = capture.report();
        } else {
            List<Fold.Fact> facts = Fold.of(capture);
            if (factsOnly) {
                StringBuilder sb = new StringBuilder(1 << 14);
                for (Fold.Fact f : facts) {
                    sb.append(f.line()).append('\n');
                }
                page = sb.toString();
            } else {
                page = voice.render(facts);
            }
        }

        // One string, encoded once: stdout and --out cannot disagree because
        // there is nothing for them to disagree about.
        byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
        if (goldenPath != null) {
            System.exit(checkGolden(goldenPath, bytes));
        }
        PrintStream stdout = new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        stdout.write(bytes, 0, bytes.length);
        stdout.flush();
        if (outPath != null) {
            Files.write(Path.of(outPath), bytes);
        }
        System.exit(capture.resolvedName == null ? 2 : 0);
    }

    /**
     * The golden day: does this build still write the sentences it was blessed
     * writing? Determinism guards one build against itself; this guards the
     * repo against the next edit to the voice. Prints the first line that
     * moved, because "something changed" is not a finding.
     */
    private static int checkGolden(String path, byte[] rendered) throws Exception {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            System.err.println("FATAL no golden day at " + path);
            return 1;
        }
        byte[] blessed = Files.readAllBytes(file);
        if (java.util.Arrays.equals(blessed, rendered)) {
            System.out.println("GOLDEN OK " + path + " (" + blessed.length + " bytes)");
            return 0;
        }
        String[] want = new String(blessed, StandardCharsets.UTF_8).split("\n", -1);
        String[] got = new String(rendered, StandardCharsets.UTF_8).split("\n", -1);
        System.out.println("GOLDEN DRIFT " + path + " — blessed " + blessed.length
                + " bytes, rendered " + rendered.length + " bytes");
        for (int i = 0; i < Math.max(want.length, got.length); i++) {
            String w = i < want.length ? want[i] : "(page ends)";
            String g = i < got.length ? got[i] : "(page ends)";
            if (!w.equals(g)) {
                System.out.println("  line " + (i + 1) + " blessed: " + w);
                System.out.println("  line " + (i + 1) + " now:     " + g);
                break;
            }
        }
        return 1;
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            System.err.println("flag " + args[i - 1] + " wants a value");
            System.exit(2);
        }
        return args[i];
    }

    private static void usage() {
        System.err.print("""
                DreamReader — D-047's teleprinter: one mind's day as deterministic prose
                  --pilot NAME     whose dream to fold (the tap's semantics: first live match)
                  --seed N         the fate of the universe (default 42)
                  --ticks N        how long the day runs (default 6000)
                  --voice V        cold (default) or none — the fold, stated flatly
                  --facts          print the fact stream itself; no voice ever touches it
                  --capture-only   report the three feeds and stop, one greppable line last
                  --out FILE       also write the page to FILE — the same bytes stdout got
                  --check-golden F verdict only: does this build still write F's sentences?
                exit: 0 a day rendered · 1 the golden day drifted · 2 nobody by that name.
                observer-only: the tool runs its own quiet universe and mutates nothing.
                """);
    }

    private DreamReader() {}
}
