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
 *       2 the record holds nobody by that name, 1 the golden day drifted, 3
 *       the invocation was refused, 4 there is no golden day at that path. A
 *       typo in a pilot's name must be a refusal, not an empty page that
 *       reads like a quiet life — and it must not be the same number as a
 *       typo in a FLAG, or a sweep that skips the names this seed did not
 *       grow skips a misspelled flag with it and renders nobody.</li>
 *   <li><b>{@code --check-golden FILE}</b> pins the SENTENCES. Determinism
 *       only promises that one build renders one day the same way twice; it
 *       says nothing about the next edit to the voice, which would change
 *       every rendered day in silence. The golden day is the guard, and it
 *       is a plain file so the drift is readable as a diff. It answers the
 *       golden question only — the page still goes to {@code --out} when both
 *       are given, because the bytes exist either way and a caller who asked
 *       for both used to get one.</li>
 * </ul>
 *
 * Exit codes: 0 a day rendered · 1 the golden day drifted · 2 nobody by that
 * name · 3 the invocation was refused · 4 no golden day at that path. The
 * grammar is checked, not merely published: tools/dreamreader/exitgrammar.sh
 * runs one invocation per code and prints a verdict line.
 */
public final class DreamReader {

    /** A day rendered, or the golden day held. */
    private static final int EXIT_OK = 0;
    /** The sentences moved: this build no longer writes the blessed page. */
    private static final int EXIT_GOLDEN_DRIFT = 1;
    /** The record holds nobody by that name. */
    private static final int EXIT_NO_SUCH_PILOT = 2;
    /** A flag, a value or a voice the tool has no reading for. */
    private static final int EXIT_USAGE = 3;
    /** There is no golden day at that path — a typo in an argument, not a drift. */
    private static final int EXIT_NO_GOLDEN_FILE = 4;

    public static void main(String[] args) throws Exception {
        // The page owns its charset (it is encoded once, below); the verdicts
        // and refusals did not, and a drift report that reads differently on
        // the box that has to read it is the #836 defect one file wide.
        matrix.Streams.utf8();

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
                case "--seed" -> seed = number(args, ++i);
                case "--ticks" -> ticks = positive(args, ++i);
                case "--out" -> outPath = value(args, ++i);
                case "--check-golden" -> goldenPath = value(args, ++i);
                case "--capture-only" -> captureOnly = true;
                case "--facts" -> factsOnly = true;
                case "--voice" -> {
                    String name = value(args, ++i);
                    voice = Voice.named(name);
                    if (voice == null) {
                        System.err.println("unknown voice: " + name + " (cold, none)");
                        System.exit(EXIT_USAGE);
                    }
                }
                case "--help" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("unknown flag: " + args[i]);
                    usage();
                    System.exit(EXIT_USAGE);
                }
            }
        }
        if (pilot == null || pilot.isBlank()) {
            System.err.println("--pilot NAME is required — a teleprinter prints somebody");
            usage();
            System.exit(EXIT_USAGE);
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
        if (goldenPath == null) {
            PrintStream stdout = new PrintStream(
                    new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
            stdout.write(bytes, 0, bytes.length);
            stdout.flush();
        }
        if (outPath != null) {
            Files.write(Path.of(outPath), bytes);
        }
        // The record's answer is read before the golden day's, and it outranks
        // it: a name nobody carries used to reach checkGolden first and report
        // as a DRIFT in somebody else's page, so the lane's verdict line blamed
        // the voice for a typo in --pilot. It also says so out loud now — this
        // was the one refusal the tool made in silence.
        if (capture.resolvedName == null) {
            System.err.println("the record holds nobody by that name: " + pilot);
            System.exit(EXIT_NO_SUCH_PILOT);
        }
        if (goldenPath != null) {
            System.exit(checkGolden(goldenPath, bytes));
        }
        System.exit(EXIT_OK);
    }

    /**
     * The golden day: does this build still write the sentences it was blessed
     * writing? Determinism guards one build against itself; this guards the
     * repo against the next edit to the voice. Prints the first line that
     * moved, because "something changed" is not a finding.
     *
     * A path with no file at it is not a drift and no longer returns the drift
     * code: a mistyped argument that exits like the condition the flag exists
     * to detect is a red build nobody can read.
     */
    private static int checkGolden(String path, byte[] rendered) throws Exception {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            System.err.println("FATAL no golden day at " + path);
            return EXIT_NO_GOLDEN_FILE;
        }
        byte[] blessed = Files.readAllBytes(file);
        if (java.util.Arrays.equals(blessed, rendered)) {
            System.out.println("GOLDEN OK " + path + " (" + blessed.length + " bytes)");
            return EXIT_OK;
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
        return EXIT_GOLDEN_DRIFT;
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            System.err.println("flag " + args[i - 1] + " wants a value");
            System.exit(EXIT_USAGE);
        }
        return args[i];
    }

    /**
     * A count, or a refusal. An unreadable one used to leave by
     * {@code NumberFormatException}, and an uncaught throw exits 1 — the
     * grammar's word for "the golden day drifted", printed as a stack trace.
     */
    private static long number(String[] args, int i) {
        String raw = value(args, i);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            System.err.println("flag " + args[i - 1] + " wants a number: " + raw);
            System.exit(EXIT_USAGE);
            return 0;
        }
    }

    /**
     * A budget, or a refusal.
     *
     * <p>Separate from {@link #number} because the two flags mean different
     * things by a number: any {@code long} is a legitimate {@code --seed},
     * including 0 and negatives, while a non-positive {@code --ticks} is a day
     * that never happened. The reader used to render one — "folded from 0
     * frames, 0 lines naming them" — and exit 0, which is the grammar's word
     * for "a day rendered" (#1112). A caller sweeping a roster and branching on
     * the exit code handled four failures correctly and silently collected the
     * fifth as prose, because it IS prose; it just says nothing happened.
     */
    private static long positive(String[] args, int i) {
        String flag = args[i - 1];
        long n = number(args, i);
        if (n <= 0) {
            System.err.println("flag " + flag + " wants a positive count: " + n);
            System.exit(EXIT_USAGE);
        }
        return n;
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
                  --out FILE       also write the page to FILE — the same bytes stdout got,
                                   and written under --check-golden too, where stdout has
                                   only the verdict
                  --check-golden F verdict only: does this build still write F's sentences?
                exit: 0 a day rendered · 1 the golden day drifted · 2 the record holds
                      nobody by that name · 3 the invocation was refused · 4 no golden
                      day at that path.  (tools/dreamreader/exitgrammar.sh checks these)
                observer-only: the tool runs its own quiet universe and mutates nothing.
                """);
    }

    private DreamReader() {}
}
