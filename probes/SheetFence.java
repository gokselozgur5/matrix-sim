import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Probe: is the sheets-cached-nowhere law still true, and is there still
 * exactly one door? (#656)
 *
 * <p>#350 wrote three sentences of law and left them for people to keep:
 * no wing stores a sheet, the character layer imports nothing of the
 * domain, and the door itself holds no state. All three were true the day
 * they were written and all three are true today — which is precisely the
 * problem this probe exists for. A law that is true by luck reads exactly
 * like a law that is enforced, right up until the first commit that breaks
 * it, and this tree has now found that shape five times in other corners
 * (#1203, #1212, #1210, #1233, #1243). This is the reader those three
 * sentences never had.
 *
 * <p>Comments are stripped before every count, because the checker would
 * otherwise find its own subject: {@code SheetDoor}'s javadoc says the
 * words {@code private final Sheet} out loud while explaining why no such
 * field may exist, and {@code advice.sh} has been bitten by that exact
 * shape (a tool matching its own explanation) four separate times.
 *
 * <pre>
 *   probes/SheetFence                       judge src/ as it stands
 *   probes/SheetFence --wings a,b --layer c judge other trees — the falsifier
 * </pre>
 *
 * The flags are how the probe is falsified without touching {@code src/}:
 * point it at a scratch tree carrying a stored sheet and it must go red.
 * A judge that has never been seen to fail is a judge nobody has checked.
 */
public final class SheetFence {

    /**
     * A field whose declared type mentions {@code Sheet} — the thing nobody
     * may hold, in a wing or in the layer itself.
     *
     * <p>The type is matched loosely on purpose. {@code Sheet sheet}, and
     * also {@code Map<String, Sheet> memo}, {@code Sheet[] roster},
     * {@code List<Sheet> cast}: the law is about a derived value being kept,
     * and a value kept inside a container is kept. A tight
     * {@code ^Sheet\s+\w+} — which is what the first draft carried — is the
     * pattern that reads a memo map as compliant, and a memo map is the
     * exact shape #1256 was opened for.
     */
    private static final Pattern HOLDS_SHEET = Pattern.compile("\\bSheet\\b");

    /**
     * The shape of a field declaration: modifiers, a type, a name, and a
     * semicolon — with or without an initializer.
     */
    private static final Pattern FIELD_SHAPE = Pattern.compile(
            "^\\s*(?:(?:private|protected|public|static|final|volatile|transient)\\s+)+"
                    + "[\\w<>,\\[\\]\\s?]+\\s+\\w+\\s*(?:=.*)?;\\s*$");

    /**
     * An import of some corner of the domain the layer may not see. D-013
     * allows {@code matrix.core} — the character layer is type-blind, not
     * blind — and forbids the rest: a {@code matrix.zion} import here is
     * the bridge growing a second lane.
     */
    private static final Pattern FOREIGN_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?(matrix\\.(?!core\\.|character\\.)[\\w.]+)\\s*;");

    private static final String[] DEFAULT_WINGS = {
            "src/matrix/realworld", "src/matrix/entities", "src/matrix/machine", "src/matrix/zion",
    };

    private static final String DEFAULT_LAYER = "src/matrix/character";

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        List<String> wings = new ArrayList<>(List.of(DEFAULT_WINGS));
        String layer = DEFAULT_LAYER;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--wings" -> {
                    if (++i == args.length) {
                        System.exit(Probes.Outcome.REFUSED.code());
                    }
                    wings = new ArrayList<>(List.of(args[i].split(",")));
                }
                case "--layer" -> {
                    if (++i == args.length) {
                        System.exit(Probes.Outcome.REFUSED.code());
                    }
                    layer = args[i];
                }
                default -> System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        List<String> offences = new ArrayList<>();
        int stored = 0;
        for (String wing : wings) {
            for (Path file : javaFiles(Path.of(wing))) {
                for (String line : code(file)) {
                    if (keepsASheet(line)) {
                        stored++;
                        offences.add("STORED " + file + " — " + line.trim());
                    }
                }
            }
        }

        int foreign = 0;
        int cached = 0;
        for (Path file : javaFiles(Path.of(layer))) {
            for (String line : code(file)) {
                Matcher m = FOREIGN_IMPORT.matcher(line);
                if (m.find()) {
                    foreign++;
                    offences.add("IMPORT " + file + " — " + m.group(1));
                }
                if (keepsASheet(line)) {
                    cached++;
                    offences.add("CACHED " + file + " — " + line.trim());
                }
            }
        }

        int doorMissing = 0;
        Path door = Path.of(layer, "SheetDoor.java");
        if (!Files.exists(door)) {
            offences.add("DOOR no door at " + door + " — the resolver this probe judges is gone");
            doorMissing++;
        }

        offences.forEach(System.out::println);
        boolean held = stored == 0 && foreign == 0 && cached == 0 && doorMissing == 0;
        Probes.leave(String.format(
                "VERDICT ONE_DOOR_NO_CACHE stored=%d foreign_imports=%d cached=%d door_missing=%d",
                stored, foreign, cached, doorMissing),
                held ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /**
     * Does this line declare a field that keeps a {@code Sheet}?
     *
     * <p>Two questions, in the order that makes the second cheap: is the
     * line shaped like a field at all, and does its DECLARATOR — everything
     * left of the {@code =} — name the type. The split matters. A method
     * body line reading {@code Sheet s = door.at(name, family);} is a local,
     * not a field, and is refused by the modifier requirement in
     * {@link #FIELD_SHAPE}; a field initialized in place with
     * {@code = new HashMap<Sheet>()} carries parentheses on the right-hand
     * side, which must not make it look like a method to the paren test.
     */
    private static boolean keepsASheet(String line) {
        if (!FIELD_SHAPE.matcher(line).matches()) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
            return false;   // where the file lives, not what it holds
        }
        int assign = line.indexOf('=');
        String declarator = assign < 0 ? line : line.substring(0, assign);
        if (declarator.contains("(")) {
            return false;   // a method signature, not a field
        }
        return HOLDS_SHEET.matcher(declarator).find();
    }

    /** Every .java under a root, sorted, so two runs read the same bytes in the same order. */
    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    /**
     * A file's lines with the comments taken out — block comments by state,
     * line comments by cut. Crude on purpose: a string literal containing
     * {@code //} would be trimmed too, and no file in this layer has one.
     * The alternative is a Java parser, which is a dependency (D-009).
     */
    private static List<String> code(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        boolean inBlock = false;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw;
            if (inBlock) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                line = line.substring(end + 2);
                inBlock = false;
            }
            int start = line.indexOf("/*");
            if (start >= 0) {
                int end = line.indexOf("*/", start + 2);
                if (end < 0) {
                    inBlock = true;
                    line = line.substring(0, start);
                } else {
                    line = line.substring(0, start) + line.substring(end + 2);
                }
            }
            int slashes = line.indexOf("//");
            if (slashes >= 0) {
                line = line.substring(0, slashes);
            }
            out.add(line);
        }
        return out;
    }

    private SheetFence() {}
}
