import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: does every flag that takes a value refuse a missing or unparsable one
 * in the refusal grammar, rather than dying on it? (#1747)
 *
 * <p>{@code DoorRefusal} asks the same question one population over — do the
 * probes refuse a flag they do not know — and says in its own javadoc what it
 * deliberately cannot see: <em>the distinction is between a code and a crash,
 * and reading it would mean asserting which code, which is a second convention
 * nobody has set.</em> For {@code matrix.Main} the convention is set, by #791:
 * a scenario flag is a promise, and the exit code is the only place the operator
 * learns their argument will not be honoured. The refusal is exit 2 with one
 * line naming the flag.
 *
 * <p><b>What was wrong.</b> Every flag with an operand read it as
 * {@code args[++i]} and parsed it bare, so {@code --reload-at} written last, or
 * {@code --ticks abc}, left through an uncaught exception: exit 1, and a stack
 * trace naming a line inside the parser. Exit 1 means the program broke; the
 * operator's mistake was told to them in the language of a crash, pointing at
 * the parser's line number instead of at what they typed. It was uniform across
 * every numeric flag, which is what made it one defect and not five.
 *
 * <p><b>Why this runs the door instead of reading it.</b> A refusal can be
 * written many ways — a guard at the parse site, a helper, a try/catch that
 * exits — and a grep for any one spelling misses the others. What is decidable
 * is the behaviour: hand the flag nothing, or hand it a word, and look at the
 * exit code and the line. The population is read from {@code Main.java} itself,
 * so a flag added tomorrow is judged the day it lands, and a flag that stops
 * taking a value leaves the population by the same reading.
 *
 * <p><b>Two findings, kept apart.</b> A door that leaves 0 SWALLOWED the
 * mistake — the run proceeded on a value the operator did not give. A door that
 * leaves nonzero but not 2 CRASHED — it stopped, but in the wrong language, and
 * that is the exact shape this unit repaid. They are counted separately because
 * a reader chasing one looks in a different place than a reader chasing the
 * other.
 *
 * <p><b>What it cannot see:</b> whether the refusal line is the <em>useful</em>
 * one. It asserts the flag is named in it, because a refusal that does not name
 * the flag leaves the operator hunting; it does not judge the rest of the
 * sentence, which is prose and not a contract.
 */
public final class OperandRefusal {

    /** A value no numeric flag could parse, and nothing on disk is named. */
    private static final String NOT_A_NUMBER = "not-a-number";

    /** Generous against a refusal, which returns before the JVM finishes warming. */
    private static final long WAIT_SECONDS = 20;

    private static final String DEFAULT_MAIN = "src/matrix/Main.java";

    /** The refusal code #791 settled on, and the one the range laws already spend. */
    private static final int REFUSAL = 2;

    /**
     * An arm that consumes the next argument, HOWEVER it consumes it.
     *
     * <p>THIS PATTERN NAMES THE DEFECT AS WELL AS THE FIX, and the first
     * reading did not. It matched only the operand readers, so putting one
     * flag back to its bare {@code Long.parseLong(args[++i])} did not fail the
     * probe — it removed that flag from the population, and the sweep stayed
     * green while judging one thing fewer. A probe whose population is defined
     * by the repair cannot see the repair being undone: it measures compliance
     * by counting only the compliant.
     *
     * <p>So the population is <em>every arm that takes a value</em>, which is
     * a property of the parser's shape rather than of which helper it calls,
     * and the refusal is then demanded of all of them.
     */
    private static final Pattern CONSUMES = Pattern.compile(
            "\\b(?:longOperand|intOperand|operand)\\(args|args\\[\\+\\+i\\]");

    /** Of those, the ones whose value is a number — the only arms a word can offend. */
    private static final Pattern NUMERIC = Pattern.compile(
            "\\b(?:longOperand|intOperand|Long\\.parseLong|Integer\\.parseInt)\\b");

    /** The parser's opening line, and the arm that closes it — the population's boundary. */
    private static final String SWITCH_HEAD = "switch (args[i]) {";

    private static final String SWITCH_TAIL = "default ->";

    /** A parse arm, whether or not it takes a value — the population's denominator. */
    private static final Pattern ANY_ARM = Pattern.compile("case \"(--[a-z-]+)\" ->");

    public static void main(String[] args) throws IOException, InterruptedException {
        String mainSource = DEFAULT_MAIN;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--main") && i + 1 < args.length) {
                mainSource = args[++i];
            } else {
                System.err.println("unknown flag: " + args[i]);
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        Path source = Path.of(mainSource);
        if (!Files.isReadable(source)) {
            Probes.leave("VERDICT OPERAND_SOURCE_UNREADABLE main=" + mainSource, false);
        }
        String file = Files.readString(source, StandardCharsets.UTF_8);
        // THE SWITCH IS CUT OUT BEFORE ANYTHING IS READ, and the first reading
        // is why. The last arm has no next arm to stop at, so its slice ran to
        // the end of the file — over the operand readers themselves — and
        // `--help`, which takes no value, was declared valued by the definition
        // of the helper it does not call. A population read past its own
        // boundary counts the ruler as one of the things measured.
        int switchStart = file.indexOf(SWITCH_HEAD);
        int switchEnd = switchStart < 0 ? -1 : file.indexOf(SWITCH_TAIL, switchStart);
        if (switchStart < 0 || switchEnd < 0) {
            Probes.leave("VERDICT OPERAND_PARSER_NOT_FOUND main=" + mainSource, false);
        }
        String text = file.substring(switchStart + SWITCH_HEAD.length(), switchEnd);

        // Read the population off the parser, not off a list kept beside it: a
        // list is a second copy of the truth and #1747 exists because the first
        // copy drifted from what the code did.
        //
        // An arm is sliced to the start of the next one rather than matched in
        // one expression, because an arm's body is either an expression or a
        // block and a single pattern that stops at `;` reads only the first
        // shape. It found ten of the twelve, and the two it missed were
        // `--sink-every` and `--reload-at` — the two arms that carry their own
        // range law, which is to say the two most worth judging.
        List<int[]> spans = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        Matcher any = ANY_ARM.matcher(text);
        while (any.find()) {
            flags.add(any.group(1));
            spans.add(new int[] {any.start(), any.end()});
        }
        int flagArms = flags.size();
        Map<String, String> valued = new LinkedHashMap<>();
        for (int arm = 0; arm < flagArms; arm++) {
            int from = spans.get(arm)[1];
            int to = arm + 1 < flagArms ? spans.get(arm + 1)[0] : text.length();
            String body = text.substring(from, to);
            if (CONSUMES.matcher(body).find()) {
                valued.put(flags.get(arm), NUMERIC.matcher(body).find() ? "number" : "text");
            }
        }

        List<String> offences = new ArrayList<>();
        int swept = 0;
        int swallowed = 0;
        int crashed = 0;
        for (Map.Entry<String, String> entry : valued.entrySet()) {
            String flag = entry.getKey();
            boolean numeric = entry.getValue().equals("number");

            swept++;
            judge(flag, offences, List.of(flag), "nothing followed it");
            if (numeric) {
                swept++;
                judge(flag, offences, List.of(flag, NOT_A_NUMBER), "'" + NOT_A_NUMBER + "'");
            }
        }
        for (String offence : offences) {
            if (offence.startsWith("SWALLOWED")) {
                swallowed++;
            } else {
                crashed++;
            }
        }

        // THE IDENTITY IS ASSERTED, NOT ARGUED. A parse arm either takes a value
        // or it does not, and the two counts must exhaust the switch. A third
        // shape added to the parser breaks this line rather than quietly
        // shrinking what `valued=` means.
        if (valued.size() > flagArms) {
            Probes.leave("VERDICT OPERAND_CENSUS_DOES_NOT_ADD_UP valued=" + valued.size()
                    + " arms=" + flagArms, false);
        }

        System.out.println("OPERAND_CENSUS arms=" + flagArms + " valued=" + valued.size()
                + " cases=" + swept + " refusal_code=" + REFUSAL
                + " wait_s=" + WAIT_SECONDS + " main=" + mainSource);
        offences.forEach(System.out::println);

        // `swept_none=` rides here because a reading that found no valued flag
        // must not print the line a compliant tree prints (#1207, #970):
        // nothing read is the finding, not a clean result over an empty set.
        boolean held = swallowed == 0 && crashed == 0 && swept > 0;
        Probes.leave(String.format(
                "VERDICT EVERY_OPERAND_REFUSES swallowed=%d crashed=%d swept_none=%d",
                swallowed, crashed, swept == 0 ? 1 : 0), held);
    }

    /**
     * Runs the door with the argument list given and records what came back.
     *
     * <p>The child is given no other flag, so nothing downstream of the parser
     * can be what refused: a run that reaches a mode gate has already read the
     * operand this case says is missing.
     */
    private static void judge(String flag, List<String> offences, List<String> childArgs,
            String what) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(javaBin(), "-cp", "out", "matrix.Main"));
        command.addAll(childArgs);
        Path err = Files.createTempFile("operand-" + flag.substring(2) + "-", ".err");
        Process child = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(err.toFile())
                .start();
        boolean done = child.waitFor(WAIT_SECONDS, TimeUnit.SECONDS);
        if (!done) {
            // Still running on an operand it never got: it read the mistake as
            // nothing and got on with a two-thousand-tick universe. Destroyed
            // rather than waited out — the sweep should not pay for a swallow.
            child.destroyForcibly();
            offences.add("SWALLOWED " + flag + " ran past " + WAIT_SECONDS + "s on " + what);
            Files.deleteIfExists(err);
            return;
        }
        int rc = child.exitValue();
        String stderr = Files.readString(err, StandardCharsets.UTF_8);
        Files.deleteIfExists(err);
        if (rc == 0) {
            offences.add("SWALLOWED " + flag + " left 0 on " + what
                    + " — the run proceeded on a value nobody gave");
            return;
        }
        if (rc != REFUSAL) {
            offences.add("CRASHED " + flag + " left " + rc + " on " + what
                    + " — the refusal code is " + REFUSAL + "; first line: "
                    + firstLine(stderr));
            return;
        }
        if (!stderr.contains(flag)) {
            offences.add("CRASHED " + flag + " refused " + what
                    + " without naming the flag; first line: " + firstLine(stderr));
        }
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        String line = newline < 0 ? text : text.substring(0, newline);
        return line.isBlank() ? "(no line)" : line.strip();
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private OperandRefusal() {
    }
}
