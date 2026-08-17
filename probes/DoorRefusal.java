import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Probe: does every probe that parses a long option refuse one it does not know?
 * (#1479)
 *
 * <p>The convention was real and unwritten. Twenty-two of twenty-six flag-parsing
 * probes left with {@code Probes.Outcome.REFUSED} on an unknown flag; four did
 * not, in three different spellings, and nothing in the tree said which group a
 * new probe belonged in. That is {@code SheetFence}'s shape for the seventh time:
 * a rule that is kept by habit reads exactly like a rule that is enforced, right
 * up until the first file that is not.
 *
 * <p><b>Why it costs most where it was worst.</b> {@code CensusSampleSize} prices
 * how many seeds a claim needs, and every figure it prints is a function of its
 * flags — so {@code --trails 5000} priced the defaults and printed them under a
 * green verdict. A silent flag is not a missing feature; it is a confident answer
 * to a question nobody asked.
 *
 * <p><b>This runs its siblings rather than reading them, and that is forced.</b>
 * A refusal can be spelled three ways at least — a {@code default} arm, an
 * {@code else} after a positional collector, an {@code args[0].equals} guard that
 * ignores everything else — and a grep for any one of them misses the other two.
 * What is decidable is the behaviour: hand the probe a flag nothing could know and
 * look at the exit code. The population is read from the tree (a source file that
 * mentions a {@code "--}flag), so a probe added tomorrow is in the population the
 * day it lands.
 *
 * <p><b>The timeout is the whole apparatus.</b> A probe that swallows the flag does
 * not fail — it RUNS, and some of these build twenty-thousand-tick universes. So
 * each child gets a bounded wait, and a child still alive at the bound is a
 * SWALLOWED finding rather than a hung sweep. That is also why the bound is not a
 * timing assertion: it is generous by two orders of magnitude against a refusal,
 * which returns before the JVM has finished warming.
 *
 * <p><b>What it cannot see:</b> a probe that refuses the flag for the wrong reason
 * — one whose parser happens to throw on any unknown token and leaves with an
 * uncaught exception. Such a probe leaves nonzero and passes here. The distinction
 * is between a code and a crash, and reading it would mean asserting which code,
 * which is a second convention nobody has set (#1372's question, one population
 * over). The verdict says {@code refused} and not {@code refused_correctly}.
 */
public final class DoorRefusal {

    /** A flag nothing in this tree could plausibly know. */
    private static final String NONSENSE = "--zzz-no-probe-knows-this";

    /** Generous by two orders of magnitude against a refusal, and a bound on a swallow. */
    private static final long WAIT_SECONDS = 20;

    private static final String DEFAULT_ROOT = "probes";

    public static void main(String[] args) throws IOException, InterruptedException {
        matrix.Streams.utf8();
        String root = DEFAULT_ROOT;
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            if ("--root".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                root = args[i];
            } else if ("--list".equals(args[i])) {
                list = true;
            } else {
                // Clause 8, and this file is its own subject: a probe that judges
                // whether its siblings refuse an unknown flag and did not refuse one
                // would be the tenth instance of a checker exempting itself.
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        int swept = 0;
        int refused = 0;
        int crashed = 0;
        int swallowed = 0;
        List<String> offences = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Path file : sources(Path.of(root))) {
            String cls = file.getFileName().toString().replace(".java", "");
            if (!parsesAFlag(file)) {
                continue;
            }
            swept++;
            Path log = Files.createTempFile("door-" + cls + "-", ".log");
            Process p = new ProcessBuilder(
                    javaBin(), "-cp", "out:probes/out", cls, NONSENSE)
                    .redirectOutput(log.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean done = p.waitFor(WAIT_SECONDS, TimeUnit.SECONDS);
            if (!done) {
                // Still running on a flag it does not know: it read the flag as nothing
                // and got on with its work. Destroyed rather than waited out, because a
                // 20,000-tick universe is not a thing a keeper should pay for.
                p.destroyForcibly();
                swallowed++;
                offences.add("SWALLOWED " + cls + " ran past " + WAIT_SECONDS
                        + "s on '" + NONSENSE + "' — it read the flag as nothing");
                Files.deleteIfExists(log);
                continue;
            }
            int rc = p.exitValue();
            // A VERDICT LINE IS THE DISCRIMINATOR, and it has to be, because the exit
            // code alone cannot tell these apart. Ten of the twenty-six leave 1 on an
            // unknown flag by throwing NumberFormatException — they read the flag as
            // their positional tick count. That is nonzero and it is not a refusal, and
            // it is not a swallow either: nothing ran. A probe that reached its WORK
            // printed a verdict; a probe that died at its door printed none.
            boolean reachedItsWork = printedAVerdict(log);
            Files.deleteIfExists(log);
            // THE REFUSAL CODE IS ASKED BEFORE THE VERDICT LINE, and the order is a
            // correction: `NeutralDiff` refuses explicitly — `NEUTRALDIFF FAIL
            // bad_argument text="…"` then a FAIL verdict — and leaves 3. Read
            // verdict-first, that is a swallow, which is exactly backwards: it is the
            // most articulate refusal in the tree. A probe that spends the refusal code
            // has refused, whatever it printed on the way out.
            if (rc == 0) {
                swallowed++;
                offences.add("SWALLOWED " + cls + " left 0 on '" + NONSENSE
                        + "' — a green verdict for a question nobody asked");
            } else if (rc == Probes.Outcome.REFUSED.code()) {
                refused++;
                if (list) {
                    System.out.printf("DOOR %-22s refused rc=%d%n", cls, rc);
                }
            } else if (reachedItsWork) {
                swallowed++;
                offences.add("SWALLOWED " + cls + " left " + rc + " on '" + NONSENSE
                        + "' and printed a verdict — it read the flag as nothing and"
                        + " judged its contract anyway");
            } else {
                // REPORTED, NEVER JUDGED, and it is its own finding (#1481). A probe that
                // leaves 1 with a stack trace has refused by accident, and 1 in this tree
                // means *the claim does not hold* — so a typo'd argument reports a broken
                // contract. Wrong for the same reason #1170 is: a defect report that names
                // the wrong defect. Not folded into `swallowed`, because the door did stop
                // the run, and not into `refused`, because it stopped it by dying.
                crashed++;
                notes.add("CRASHED " + cls + " left " + rc + " with no verdict on '"
                        + NONSENSE + "' — a refusal by exception, not by code");
            }
        }

        // The populations ride the census (#1221): `swept=` moves whenever a probe gains
        // or loses a flag, and a count on an exact-line row is a number people edit until
        // the lane is quiet. `crashed=` rides here too, because it is reported and not
        // judged — a number on the verdict line reads as a thing the lane refuses.
        System.out.println("DOOR_CENSUS swept=" + swept + " refused=" + refused
                + " crashed=" + crashed + " wait_s=" + WAIT_SECONDS + " root=" + root);
        notes.forEach(System.out::println);
        offences.forEach(System.out::println);

        // `swept_none=` rides the VERDICT: a reading that found no flag-parsing probe
        // must not print the line a compliant tree prints (#1207, #970). Nothing read is
        // the finding, not a clean result over an empty set.
        boolean held = swallowed == 0 && swept > 0;
        Probes.leave(String.format(
                "VERDICT EVERY_DOOR_REFUSES swallowed=%d swept_none=%d",
                swallowed, swept == 0 ? 1 : 0), held);
    }

    /**
     * Did the child reach its work? A probe that gets past its door prints a verdict
     * line; one that dies at the door prints none. That is the only decidable
     * difference between *it ignored the flag and judged anyway* and *it refused by
     * throwing*, and both are nonzero.
     */
    private static boolean printedAVerdict(Path log) throws IOException {
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            if (line.startsWith("VERDICT ") || line.contains(" VERDICT ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does this source parse a long option at all? A quoted token opening with two
     * dashes. Deliberately generous: a probe that MENTIONS a flag in a comment is
     * swept and asked, and being asked costs one JVM start. The reverse mistake —
     * a probe with a door this reading misses — is the one that matters, and a
     * generous population is how it is avoided.
     */
    private static boolean parsesAFlag(Path file) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.contains("\"--")) {
                return true;
            }
        }
        return false;
    }

    /** Every probe source but the shared helper, which has no {@code main}. */
    private static List<Path> sources(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.list(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("Probes.java"))
                    .filter(p -> !p.getFileName().toString().equals("DoorRefusal.java"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The JVM running this probe, not whichever {@code java} is on PATH. A keeper
     * that launched a different runtime than the one judging it would be measuring
     * two trees at once — and the determinism lane runs this sweep on two JDKs
     * (#1477), where that difference is the whole point of the matrix.
     */
    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private DoorRefusal() {}
}
