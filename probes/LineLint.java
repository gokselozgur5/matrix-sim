import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: the instrument lines are the system's face — this is the mirror.
 *
 * D-020 made the lines the contract and enforced it with nobody. The eight
 * families live as format strings in their emitters; the laws that keep
 * them parseable forever — one line one fact, fixed field order, additive
 * evolution only — have been honoured by discipline. Discipline is a
 * person remembering. #200 appended {@code selfsub=N} at the end of METRIC
 * by hand, correctly, because someone knew the law; the next append will be
 * made by someone who does not.
 *
 * <p>LineLint reads a run's stream and verdicts it against
 * {@link LineGrammar}: zero unknown families, zero unparsed instrument
 * lines, zero fields the registry did not predict, no reordered field, no
 * retyped field. A column appended at the END passes and is reported —
 * that is how the grammar is allowed to grow. A column renamed or moved
 * fails and names the family and the field.
 *
 * <p>Cadence is measured and reported, never judged: emission cadence is a
 * property of the run (a replay, an era sweep or a scaled run may sample
 * differently), while field order is a property of the grammar. Only the
 * grammar is a contract.
 *
 * <p>This unit is the validator and its data, deliberately not the spec
 * document (#255) and not the drift check (#260, which consumes this
 * registry instead of re-implementing the parse).
 *
 * <pre>
 * java -cp out:probes/out LineLint [ticks] [seed]   own universe, quiet sink
 * java -cp out:probes/out LineLint --stdin          lint a captured stream
 * java -cp out:probes/out LineLint --registry       dump the registry as rows
 * </pre>
 *
 * The {@code --stdin} form is how the falsifiers run without touching
 * {@code src/}: pipe a real stream through {@code sed} and watch the
 * verdict move.
 */
public final class LineLint {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--registry")) {
            dumpRegistry();
            return;
        }
        List<String> lines;
        if (args.length > 0 && args[0].equals("--stdin")) {
            lines = readStdin();
        } else {
            long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
            long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
            ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
            new Simulation(seed, buf, null).run(ticks);
            lines = List.of(buf.toString(StandardCharsets.UTF_8).split("\n"));
        }
        lint(lines);
    }

    /** The registry as greppable rows — the artifact #255 and #260 stop guessing from. */
    private static void dumpRegistry() {
        for (var family : LineGrammar.FAMILIES) {
            System.out.println("GRAMMAR family=" + family.name()
                    + " fields=" + family.fields().size()
                    + " arities=" + join(family.arities())
                    + " cadence=" + family.cadence());
            int pos = 0;
            for (var field : family.fields()) {
                System.out.println("FIELD family=" + family.name()
                        + " pos=" + pos++
                        + " name=" + field.name()
                        + " type=" + field.type()
                        + " unit=" + field.unit()
                        + " domain=" + field.domain());
            }
        }
        System.out.println("GRAMMAR families=" + LineGrammar.FAMILIES.size());
    }

    private static void lint(List<String> lines) {
        long instrument = 0, unknown = 0, unparsed = 0, reordered = 0;
        long retyped = 0, domain = 0, cadenceBreaks = 0;
        List<String> seen = new ArrayList<>();
        List<String> appended = new ArrayList<>();
        String brokenFamily = null, brokenField = null;
        // Last tick seen per family, parallel to FAMILIES — an array, not a
        // map: the print order of an instrument must not ride a hash.
        long[] lastTick = new long[LineGrammar.FAMILIES.size()];
        java.util.Arrays.fill(lastTick, -1);

        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            String name = familyToken(line);
            if (name == null) {
                continue; // framed narrative, JSONL, blank — not an instrument line
            }
            instrument++;
            LineGrammar.Family family = LineGrammar.family(name);
            if (family == null) {
                unknown++;
                System.out.println("UNKNOWN family=" + name + " line=" + (n + 1));
                if (brokenFamily == null) {
                    brokenFamily = name;
                    brokenField = "-";
                }
                continue;
            }
            if (!seen.contains(name)) {
                seen.add(name);
            }
            List<String[]> fields = split(line);
            if (fields == null) {
                unparsed++;
                System.out.println("UNPARSED family=" + name + " line=" + (n + 1)
                        + " reason=field_shape");
                if (brokenFamily == null) {
                    brokenFamily = name;
                    brokenField = "-";
                }
                continue;
            }
            // Arity: a declared one, or past the largest — the appended tail.
            if (!family.arities().contains(fields.size()) && fields.size() < family.maxArity()) {
                unparsed++;
                System.out.println("UNPARSED family=" + name + " line=" + (n + 1)
                        + " arity=" + fields.size() + " legal=" + join(family.arities()));
                if (brokenFamily == null) {
                    brokenFamily = name;
                    brokenField = "-";
                }
                continue;
            }
            for (int i = 0; i < fields.size(); i++) {
                String key = fields.get(i)[0];
                String value = fields.get(i)[1];
                if (i >= family.fields().size()) {
                    String note = name + "." + key;
                    if (!appended.contains(note)) {
                        appended.add(note);
                        System.out.println("APPENDED family=" + name + " pos=" + i + " field=" + key);
                    }
                    continue;
                }
                LineGrammar.Field spec = family.fields().get(i);
                if (!spec.name().equals(key)) {
                    reordered++;
                    System.out.println("FIELD family=" + name + " line=" + (n + 1)
                            + " pos=" + i + " expected=" + spec.name() + " saw=" + key);
                    if (brokenFamily == null) {
                        brokenFamily = name;
                        brokenField = spec.name();
                    }
                    continue;
                }
                if (!typed(spec.type(), value)) {
                    retyped++;
                    System.out.println("TYPE family=" + name + " line=" + (n + 1)
                            + " field=" + key + " expected=" + spec.type() + " saw=" + value);
                    if (brokenFamily == null) {
                        brokenFamily = name;
                        brokenField = key;
                    }
                    continue;
                }
                if (!inDomain(spec, value)) {
                    domain++;
                    System.out.println("DOMAIN family=" + name + " line=" + (n + 1)
                            + " field=" + key + " domain=" + spec.domain() + " saw=" + value);
                    if (brokenFamily == null) {
                        brokenFamily = name;
                        brokenField = key;
                    }
                }
            }
            // Cadence: measured against the registry, reported, never judged.
            int slot = LineGrammar.FAMILIES.indexOf(family);
            if (family.cadence() > 0 && !fields.isEmpty() && fields.get(0)[0].equals("tick")) {
                long tick = Long.parseLong(fields.get(0)[1]);
                if (lastTick[slot] >= 0 && tick - lastTick[slot] != family.cadence()) {
                    cadenceBreaks++;
                    System.out.println("CADENCE family=" + name + " line=" + (n + 1)
                            + " expected=" + family.cadence() + " saw=" + (tick - lastTick[slot]));
                }
                lastTick[slot] = tick;
            }
        }

        boolean held = unknown == 0 && unparsed == 0 && reordered == 0
                && retyped == 0 && domain == 0;
        System.out.println("LINELINT lines=" + instrument
                + " families=" + seen.size()
                + " unknown=" + unknown
                + " unparsed=" + unparsed
                + " registry=" + LineGrammar.FAMILIES.size()
                + " appended=" + appended.size()
                + " reordered=" + reordered
                + " retyped=" + retyped
                + " domain=" + domain
                + " cadence_breaks=" + cadenceBreaks);
        Probes.leave(held ? "VERDICT GRAMMAR_HELD"
                : "VERDICT GRAMMAR_BROKEN family=" + brokenFamily + " field=" + brokenField, held);
    }

    /**
     * The family prefix of an instrument line, or null for anything else.
     * An instrument line opens with an ALL-CAPS token and carries at least
     * one {@code key=value} — the shape D-020 fixed. Framed narrative
     * ({@code [000042] SYS …}) and the follow stream's JSONL are not
     * instrument lines and are not linted.
     */
    private static String familyToken(String line) {
        int space = line.indexOf(' ');
        if (space <= 0 || line.indexOf('=') < space) {
            return null;
        }
        String head = line.substring(0, space);
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if ((c < 'A' || c > 'Z') && c != '_') {
                return null;
            }
        }
        return head;
    }

    /**
     * The line's fields in order as {key, value} pairs, or null if the shape
     * itself is wrong. Quoted values may hold spaces and commas (ATTN's
     * {@code top="financial district:49,old city:39"}), so the scan honours
     * quotes rather than splitting on whitespace.
     */
    private static List<String[]> split(String line) {
        List<String[]> out = new ArrayList<>();
        int i = line.indexOf(' ') + 1;
        while (i < line.length()) {
            int eq = line.indexOf('=', i);
            if (eq < 0) {
                return null;
            }
            String key = line.substring(i, eq);
            if (key.isEmpty() || key.indexOf(' ') >= 0) {
                return null;
            }
            int end;
            if (eq + 1 < line.length() && line.charAt(eq + 1) == '"') {
                end = line.indexOf('"', eq + 2);
                if (end < 0) {
                    return null;
                }
                end++;
            } else {
                end = line.indexOf(' ', eq + 1);
                if (end < 0) {
                    end = line.length();
                }
            }
            out.add(new String[] {key, line.substring(eq + 1, end)});
            i = end + 1;
        }
        return out.isEmpty() ? null : out;
    }

    /** Does the value have the shape the registry declared? A retype is a breaking change. */
    private static boolean typed(LineGrammar.Type type, String value) {
        switch (type) {
            case INT:
                return isInt(value);
            case RATIO:
                return decimals(value) == 3;
            case REAL1:
                return decimals(value) == 1;
            case PAIR:
                int slash = value.indexOf('/');
                return slash > 0 && isInt(value.substring(0, slash))
                        && isInt(value.substring(slash + 1));
            case TEXT:
                return value.length() >= 2 && value.charAt(0) == '"'
                        && value.charAt(value.length() - 1) == '"';
            case WORD:
                if (value.isEmpty()) {
                    return false;
                }
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if ((c < 'A' || c > 'Z') && c != '_') {
                        return false;
                    }
                }
                return true;
            case SHA:
                if (value.length() != 64) {
                    return false;
                }
                for (int i = 0; i < 64; i++) {
                    char c = value.charAt(i);
                    if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    /** The value's range, as the registry predicted it. Shape is grammar; range is the claim. */
    private static boolean inDomain(LineGrammar.Field spec, String value) {
        switch (spec.domain()) {
            case ">=0":
                return !value.startsWith("-");
            case "0..1":
                double d = Double.parseDouble(value);
                return d >= 0.0 && d <= 1.0;
            case "n/n":
                return !value.startsWith("-") && value.indexOf("/-") < 0;
            case "finite":
                return Double.isFinite(Double.parseDouble(value));
            default:
                return true;
        }
    }

    private static boolean isInt(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int i = s.charAt(0) == '-' ? 1 : 0;
        if (i == s.length()) {
            return false;
        }
        for (; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /** Decimal places after the point, or -1 if this is not a plain decimal. */
    private static int decimals(String s) {
        int dot = s.indexOf('.');
        if (dot < 0 || !isInt(s.substring(0, dot))) {
            return -1;
        }
        String frac = s.substring(dot + 1);
        return frac.isEmpty() || !isInt(frac) || frac.startsWith("-") ? -1 : frac.length();
    }

    private static String join(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private static List<String> readStdin() throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                out.add(line);
            }
        }
        return out;
    }

    private LineLint() {}
}
