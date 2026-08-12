import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
 * Exit codes: 0 a day rendered · 2 the record holds nobody by that name.
 */
public final class DreamReader {

    public static void main(String[] args) throws Exception {
        String pilot = null;
        long seed = 42;
        long ticks = 6_000;
        boolean captureOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pilot" -> pilot = value(args, ++i);
                case "--seed" -> seed = Long.parseLong(value(args, ++i));
                case "--ticks" -> ticks = Long.parseLong(value(args, ++i));
                case "--capture-only" -> captureOnly = true;
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

        // --capture-only pins the capture stage's own view: the three feeds
        // and their counts, quotable as evidence after the page is built on
        // top of them.
        String page = captureOnly ? capture.report() : Fold.plain(Fold.of(capture));

        PrintStream stdout = new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        stdout.print(page);
        stdout.flush();
        System.exit(capture.resolvedName == null ? 2 : 0);
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
                  --capture-only   report the three feeds and stop, one greppable line last
                observer-only: the tool runs its own quiet universe and mutates nothing.
                """);
    }

    private DreamReader() {}
}
