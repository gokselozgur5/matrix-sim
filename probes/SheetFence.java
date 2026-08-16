import matrix.character.Family;
import matrix.character.Sheet;
import matrix.character.SheetDoor;
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

    /**
     * The domain, as a DENY-list: everything under {@code src/} except the
     * character layer, which is judged next door under different rules.
     *
     * <p>It was an allow-list of four named directories until #1258, and the
     * hole was not hypothetical. {@code src/matrix/core} was not among the
     * four — and {@code SheetDump}'s javadoc reaches for exactly that package
     * as its example of the defect: *"0 today because the domain imports
     * nothing from `matrix.character`, and 1 the moment a `Sheet` is parked
     * on `World`"*. The file the documentation names as THE case was the file
     * this probe did not read.
     *
     * <p>An allow-list is a claim about the shape of the tree, and nothing
     * checks the claim. {@code entities/eco} and {@code entities/behavior}
     * were inside the old sweep only because {@code Files.walk} recurses —
     * luck, not design — and the next package to be added would have been
     * swept by nobody. A deny-list cannot go stale that way: a new package is
     * covered on the day it is created, and the only thing that must be
     * maintained is the short list of what is deliberately NOT the domain.
     */
    /**
     * Something a wing adapter must not read: the world, the clock, the slot,
     * the wire. A sheet derives from birth-invariants and nothing else (#658),
     * and every one of these is a field sitting within arm's reach of the
     * method that must not touch it.
     */
    private static final Pattern WORLD_SHAPED = Pattern.compile(
            "\\b(world|tick|pod|link|avatar|alive|position|pill)\\b");

    private static final String DEFAULT_ROOT = "src";

    private static final String DEFAULT_LAYER = "src/matrix/character";

    /**
     * The crossings, pinned (#1255).
     *
     * <p>{@code SheetDoor.crossing} is the third clause of #656 — *the sheet
     * crosses the jack as a hash, never a reference* — and it landed with one
     * definition and zero call sites. Dead code with a javadoc reads like
     * shipped behaviour, and worse, an unfalsified fold is a wrong constant
     * or a values-order bug waiting for the first real crossing, which is the
     * worst possible moment to find one.
     *
     * <p>These numbers are the falsification. They are arithmetic over bytes
     * this package defines, so a stranger's implementation reproduces them
     * without owning a JVM — which is the only sense in which a fold is
     * "ours" rather than the platform's. One row per family whose sheet is
     * purely derived; SYSTEM is crossed through its own door, because its
     * fatigue axis is read rather than folded.
     *
     * <p>Changing a value here is changing what has already crossed. The
     * axis ORDER in {@link Family} is load-bearing for the same reason: a
     * reorder re-rolls the sheets AND remakes every crossing ever recorded.
     */
    private static final String[][] PINNED_CROSSINGS = {
            {"HUMAN/Trinity", "1618467648"},
            {"PROGRAM/Agent Smith", "-1204527709"},
            {"MACHINE/the Machine City", "1039264927"},
            {"SYSTEM/the Matrix@6", "-276689648"},
    };

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        if (args.length == 1 && "--crossings".equals(args[0])) {
            crossings();
            return;
        }
        String root = DEFAULT_ROOT;
        String layer = DEFAULT_LAYER;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--wings" -> {
                    if (++i == args.length) {
                        System.exit(Probes.Outcome.REFUSED.code());
                    }
                    root = args[i];
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
        int swept = 0;
        Path layerPath = Path.of(layer);
        for (Path file : javaFiles(Path.of(root))) {
            if (file.startsWith(layerPath)) {
                continue;   // judged next door, by cached=, under its own rules
            }
            swept++;
            for (String line : code(file)) {
                if (keepsASheet(line)) {
                    stored++;
                    offences.add("STORED " + file + " — " + line.trim());
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

        // The wing adapters (#658). A wing that answers for a sheet must reach
        // the door and read nothing else: no world, no tick, no pod, no link.
        // Every one of those is a temptation and none is an input — a mind's
        // sheet is what it always was, not what happened to it.
        //
        // The check is shallow on purpose. It reads the three lines after a
        // `Sheet sheet()` signature and asks two questions: does SheetDoor
        // appear, and does anything world-shaped. A deeper reading needs a
        // parser, which is a dependency (D-009), and a longer window would
        // start matching the next method. So the rule the adapters follow is
        // also a rule about their SHAPE: a wing's sheet() is one expression,
        // and this refuses one that is not.
        int adapters = 0;
        int impure = 0;
        for (Path file : javaFiles(Path.of(root))) {
            if (file.startsWith(layerPath)) {
                continue;
            }
            List<String> lines = code(file);
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).contains("Sheet sheet()")) {
                    continue;
                }
                adapters++;
                String window = String.join(" ", lines.subList(i, Math.min(i + 4, lines.size())));
                boolean reachesDoor = window.contains("SheetDoor");
                boolean readsWorld = WORLD_SHAPED.matcher(window).find();
                if (!reachesDoor || readsWorld) {
                    impure++;
                    offences.add("ADAPTER " + file + ":" + (i + 1)
                            + (reachesDoor ? "" : " does not reach SheetDoor")
                            + (readsWorld ? " reads something world-shaped" : ""));
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

        // The population, on its own line and NOT in the verdict (#1221). A
        // scan that silently read zero files prints the same green line as one
        // that read the tree — charset_checked=0 (#1207) and
        // INSTRUMENTS_UNPROVEN (#970) are that shape twice — so the fact that
        // something WAS swept has to ride the exit code. The COUNT must not:
        // an exact-line bench row carrying `swept=92` goes red on the day
        // somebody adds a domain class, which teaches the next reader that the
        // number in the lane is a thing you edit until the lane is quiet.
        // `LeaveContract` learned this the expensive way, twice in one
        // afternoon. So: the census reports, the verdict judges.
        System.out.println("FENCE_CENSUS swept=" + swept + " layer=" + javaFiles(layerPath).size()
                + " adapters=" + adapters);

        boolean held = swept > 0 && stored == 0 && foreign == 0 && cached == 0
                && doorMissing == 0 && impure == 0;
        Probes.leave(String.format(
                "VERDICT ONE_DOOR_NO_CACHE stored=%d foreign_imports=%d cached=%d door_missing=%d "
                        + "swept_none=%d impure_adapters=%d",
                stored, foreign, cached, doorMissing, swept == 0 ? 1 : 0, impure),
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

    /**
     * The pinned crossings, recomputed and compared. Gives
     * {@code SheetDoor.crossing} the reader it landed without.
     */
    private static void crossings() {
        int checked = 0;
        int drifted = 0;
        for (String[] row : PINNED_CROSSINGS) {
            String label = row[0];
            int want = Integer.parseInt(row[1]);
            int got = SheetDoor.crossing(sheetFor(label));
            checked++;
            System.out.println("CROSSING " + label + "=" + got
                    + (got == want ? "" : " WANTED " + want));
            if (got != want) {
                drifted++;
            }
        }
        Probes.leave("VERDICT CROSSINGS_STABLE checked=" + checked + " drifted=" + drifted,
                drifted == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /**
     * The sheet a pinned label names. {@code FAMILY/name} for a derived
     * sheet, {@code SYSTEM/name@versions} for the one family whose sheet is
     * read rather than folded — the label carries the version because the
     * crossing does, and a SYSTEM crossing with no version in its name would
     * be a number nobody could reproduce.
     */
    private static Sheet sheetFor(String label) {
        int slash = label.indexOf('/');
        Family family = Family.valueOf(label.substring(0, slash));
        String name = label.substring(slash + 1);
        if (family == Family.SYSTEM) {
            int at = name.lastIndexOf('@');
            return SheetDoor.system(name.substring(0, at), Integer.parseInt(name.substring(at + 1)));
        }
        return SheetDoor.at(name, family);
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
