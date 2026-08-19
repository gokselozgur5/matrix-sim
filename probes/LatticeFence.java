import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Probe: is the dependency direction still the direction, and does anything
 * check? (#1417)
 *
 * <p>{@code docs/ARCHITECTURE.md} first stated three structural laws and named
 * the instrument that keeps them:
 *
 * <blockquote>Dependency direction is law, <b>verified by grep</b>:
 * {@code entities} imports nothing from {@code realworld} (the only bridge is
 * {@code NeuralLink}, which lives on the real-world side and reaches in);
 * {@code World} holds no real-world objects; nothing depends on
 * {@code Main}.</blockquote>
 *
 * <p>There was no grep. Nothing in {@code probes/}, {@code tools/} or the
 * workflows read any of the three, and all three were true — which is the
 * whole reason for this file. {@code SheetFence} names the shape: a law that
 * is true by luck reads exactly like a law that is enforced, right up until
 * the first commit that breaks it, and this tree has found it five times
 * elsewhere (#1203, #1212, #1210, #1233, #1243).
 *
 * <p>D-066 and #1689 extend that old lattice into a causal fire-door without
 * deleting D-013's jack. Entity and core code still cannot reach real-side
 * types; {@code World} still cannot hold them. In addition, the neutral
 * {@code matrix.causal} package cannot reach any runtime package, a real-side
 * class that consumes percept receipts or produces intents cannot also query
 * omniscient {@code World}, and neither {@code Simulation} nor
 * {@code NeuralLink} may expose a new mutable domain object through a public
 * or protected member.
 *
 * <p>The last rule has one explicit historical allowlist. {@code NeuralLink}
 * already is the D-013 association: its Human and Avatar fields plus its
 * constructor are the audited mutable jack rather than new causal transport.
 * They remain; LinkKind is an immutable enum and needs no exception. A later
 * receipt/intent method carrying another Human, Brain, World or other mutable
 * domain object does not. The root may coordinate both worlds privately; its
 * public boundary still speaks immutable values.
 *
 * <p><b>Clause 3 is shallow, and says so.</b> <i>{@code World} holds no
 * real-world objects</i> is not an import question: a field typed
 * {@code Object}, or a collection of a shared supertype, holds one without
 * naming one. What is read is the DECLARED TYPE of each field, which catches
 * every way the law has ever been broken in this tree and does not pretend to
 * catch a laundered one. A deeper answer needs a parser and D-009 refuses the
 * dependency.
 *
 * <p><b>Comments are stripped before every count.</b> This file's own subject
 * is a sentence about imports, and {@code src/matrix/package-info.java} says
 * {@code {@link matrix.Main}} out loud while explaining what {@code Main} is
 * for — documentation, not a dependency. A checker that finds its own subject
 * is the shape {@code SheetFence} was bitten by four separate times.
 *
 * <p>{@code --root DIR} points the whole reading at another tree.
 * {@code --selfcheck} builds private source trees for every clause, including
 * a multiline NeuralLink transport signature, so the reader is retained
 * against the exact false shapes it claims to reject.
 */
public final class LatticeFence {

    private static final String DEFAULT_ROOT = "src";

    /** The real-world side, by name. A field of any of these types is clause 2's finding. */
    private static final List<String> REAL_WORLD_TYPES =
            List.of("Brain", "Pod", "PodFarm", "NeuralLink", "Human", "RealWorld");

    /** Any reverse reference, imported or fully qualified. */
    private static final Pattern REAL_WORLD_REFERENCE =
            Pattern.compile("\\bmatrix\\s*\\.\\s*realworld\\s*\\.");

    /** The neutral grammar may depend on Java and itself, never a runtime room. */
    private static final Pattern OUTSIDE_CAUSAL_REFERENCE =
            Pattern.compile("\\bmatrix\\s*\\.\\s*(?!causal\\b)[A-Za-z_]");

    /** The entry point is a forbidden type/value dependency under any dot spacing. */
    private static final Pattern MAIN_REFERENCE =
            Pattern.compile("\\b(?:matrix\\s*\\.\\s*)?Main\\b");

    /** A mind-side causal participant is derived from its input/output vocabulary. */
    private static final Pattern MIND_REDUCER_VOCABULARY = Pattern.compile(
            "\\b(?:CausalRecord\\s*\\.\\s*)?(?:PerceptReceipt|IntentProposal)\\b");
    private static final Pattern WORLD_REFERENCE =
            Pattern.compile("\\b(?:matrix\\.core\\.)?World\\b");

    /** A Java declaration prefix whose visibility may follow annotations/modifiers. */
    private static final Pattern VISIBLE_DECLARATION_START = Pattern.compile(
            "^(?:(?:@[A-Za-z_$][\\w.$]*(?:\\([^;]*\\))?"
                    + "|abstract|default|static|final|sealed|non-sealed|strictfp"
                    + "|synchronized|native|transient|volatile)\\s+)*"
                    + "(?:public|protected)\\b.*");

    /** D-013's existing jack, normalized without whitespace. */
    private static final List<String> LEGACY_LINK_DECLARATIONS = List.of(
            "publicfinalclassNeuralLink{",
            "publicfinalHumanhuman;",
            "publicfinalAvataravatar;",
            "publicNeuralLink(Humanhuman,Avataravatar,LinkKindkind){");

    /** Existing root declarations that name a runtime class without exporting one. */
    private static final List<String> LEGACY_ROOT_DECLARATIONS = List.of(
            "publicfinalclassSimulation{",
            "publicSimulation(longseed,OutputStreamsink,StringfollowName){",
            "publicSimulation(longseed,OutputStreamsink,StringfollowName,OutputStreamchronosSink){",
            "publicList<ChronosLog.Birth>births(){",
            "publicList<Digest>run(longticks){",
            "publicSnapshotsnapshotNow(){");

    public static void main(String[] args) throws IOException {
        // Clause 7 of the probe contract, and lock 8 is its keeper: a probe's
        // first statement pins its streams, because a verdict quoted in a PR
        // must be the bytes another box prints (#836, #965). This file shipped
        // without it and the lane's scan named it — which is the check working
        // on the day the probe arrived.
        matrix.Streams.utf8();
        if (args.length == 1 && "--selfcheck".equals(args[0])) {
            selfcheck();
            return;
        }

        String root = DEFAULT_ROOT;
        for (int i = 0; i < args.length; i++) {
            if ("--root".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                root = args[i];
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        Reading reading = inspect(Path.of(root));
        for (String offence : reading.offences()) {
            System.out.println(offence);
        }

        // Populations ride the census and never the verdict (#1221). They move
        // when a class or member is added; none is a fixed success target.
        System.out.println("LATTICE_CENSUS swept=" + reading.swept()
                + " world_fields=" + reading.worldFields()
                + " mind_participants=" + reading.mindParticipants()
                + " bridge_declarations=" + reading.bridgeDeclarations());

        // Finding counts stay separate, so a failure says which door opened.
        // swept_none belongs here because an unread tree is not a clean tree.
        Probes.leave(String.format(
                "VERDICT LATTICE_HELD entities_reach=%d core_reach=%d world_holds=%d"
                        + " causal_reach=%d reducer_world=%d bridge_mutable=%d"
                        + " main_depended=%d swept_none=%d",
                reading.entitiesReach(), reading.coreReach(), reading.worldHolds(),
                reading.causalReach(), reading.reducerWorld(), reading.bridgeMutable(),
                reading.mainDepended(), reading.swept() == 0 ? 1 : 0), reading.held());
    }

    /** Read every fire-door clause from one source root. */
    private static Reading inspect(Path root) throws IOException {
        List<Path> files = javaFiles(root);
        Pattern mutableCrossing = mutableCrossing(root, files);
        int swept = 0;
        int entitiesReach = 0;
        int coreReach = 0;
        int worldHolds = 0;
        int mainDepended = 0;
        int causalReach = 0;
        int reducerWorld = 0;
        int bridgeMutable = 0;
        int worldFields = 0;
        int mindParticipants = 0;
        int bridgeDeclarations = 0;
        List<String> offences = new ArrayList<>();

        Path entitiesDir = root.resolve(Path.of("matrix", "entities"));
        Path coreDir = root.resolve(Path.of("matrix", "core"));
        for (Path file : files) {
            swept++;
            List<String> code = Probes.uncommentedLines(file);
            String source = String.join("\n", code);
            String relative = root.relativize(file).toString().replace('\\', '/');

            // CLAUSE 1 — the bridge is one-way. Matrix entities may not reach out.
            if (file.startsWith(entitiesDir)) {
                int findings = occurrences(REAL_WORLD_REFERENCE, source);
                entitiesReach += findings;
                for (int i = 0; i < findings; i++) {
                    offences.add("LATTICE entities " + file + " reaches the real world");
                }
            }

            // CLAUSE 2 — the Matrix kernel cannot receive a real-side type either.
            if (file.startsWith(coreDir)) {
                int findings = occurrences(REAL_WORLD_REFERENCE, source);
                coreReach += findings;
                for (int i = 0; i < findings; i++) {
                    offences.add("LATTICE core " + file + " reaches the real world");
                }
            }

            // CLAUSE 7 — nothing depends on the composition root's entry point.
            if (!file.getFileName().toString().equals("Main.java")) {
                int findings = occurrences(MAIN_REFERENCE, source);
                mainDepended += findings;
                for (int i = 0; i < findings; i++) {
                    offences.add("LATTICE main " + file + " depends on Main");
                }
            }

            // CLAUSE 3 — World holds no real-world object, read off declared types.
            if (file.endsWith(Path.of("core", "World.java"))) {
                for (String field : fieldDeclarations(code)) {
                    worldFields++;
                    for (String type : REAL_WORLD_TYPES) {
                        if (field.matches(".*\\b" + type + "\\b.*")) {
                            worldHolds++;
                            offences.add("LATTICE world field of type " + type + ": " + field.trim());
                            break;
                        }
                    }
                }
            }

            // CLAUSE 4 — IDs and records are neutral grammar, not a back stair.
            if (relative.startsWith("matrix/causal/")) {
                int findings = occurrences(OUTSIDE_CAUSAL_REFERENCE, source);
                causalReach += findings;
                for (int i = 0; i < findings; i++) {
                    offences.add("LATTICE causal " + file + " reaches runtime code");
                }
            }

            // CLAUSE 5 — derive a mind reducer from the causal values it handles,
            // not from a class-name convention. A receipt/intent participant may
            // not also consult omniscient World.
            if (relative.startsWith("matrix/realworld/")
                    && MIND_REDUCER_VOCABULARY.matcher(source).find()) {
                mindParticipants++;
                if (WORLD_REFERENCE.matcher(source).find()) {
                    reducerWorld++;
                    offences.add("LATTICE reducer " + file + " queries World");
                }
            }

            // CLAUSE 6 — the only mutable public NeuralLink declarations are the
            // historical D-013 jack. Simulation gets no such exception.
            boolean link = relative.equals("matrix/realworld/NeuralLink.java");
            boolean simulation = relative.equals("matrix/Simulation.java");
            if (link || simulation) {
                for (String declaration : visibleDeclarations(code)) {
                    bridgeDeclarations++;
                    if (!mutableCrossing.matcher(declaration).find()) {
                        continue;
                    }
                    if (link && legacyLinkDeclaration(declaration)) {
                        continue;
                    }
                    if (simulation && legacyRootDeclaration(declaration)) {
                        continue;
                    }
                    bridgeMutable++;
                    offences.add("LATTICE bridge " + file
                            + " exposes mutable declaration: " + declaration.strip());
                }
            }
        }

        return new Reading(swept, entitiesReach, coreReach, worldHolds, causalReach,
                reducerWorld, bridgeMutable, mainDepended, worldFields,
                mindParticipants, bridgeDeclarations, offences);
    }

    private static int occurrences(Pattern pattern, String source) {
        int count = 0;
        var matcher = pattern.matcher(source);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Every non-causal runtime class, interface or record is conservatively
     * mutable at this boundary; neutral causal records and enums are values.
     * Deriving the
     * names from the inspected root means a newly introduced mind/runtime type
     * enters the fence in the same commit rather than waiting for a hand-kept
     * blacklist update. Object remains explicit because it can erase any such
     * type without naming a repository class.
     */
    private static Pattern mutableCrossing(Path root, List<Path> files) throws IOException {
        List<String> names = new ArrayList<>();
        names.add("Object");
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (relative.startsWith("matrix/causal/")) {
                continue;
            }
            String filename = file.getFileName().toString();
            String name = filename.substring(0, filename.length() - ".java".length());
            String source = String.join("\n", Probes.uncommentedLines(file));
            Pattern declaration = Pattern.compile(
                    "\\b(?:class|interface|record)\\s+" + Pattern.quote(name) + "\\b");
            if (declaration.matcher(source).find() && !names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String::compareTo);
        List<String> quoted = new ArrayList<>();
        for (String name : names) {
            quoted.add(Pattern.quote(name));
        }
        return Pattern.compile("\\b(?:" + String.join("|", quoted) + ")\\b");
    }

    /**
     * Class-body declarations ending in a semicolon, normalized across source
     * lines. This is still a source reader rather than a Java parser, but it
     * tracks the outer class body so a package-private field, a split type, or
     * an initializer call cannot hide a direct real-side declaration. Method,
     * initializer and nested-class bodies are skipped; quoted braces do not
     * move the depth.
     */
    private static List<String> fieldDeclarations(List<String> code) {
        String source = String.join("\n", code);
        List<String> declarations = new ArrayList<>();
        StringBuilder member = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        boolean character = false;
        boolean escaped = false;
        boolean fieldInitializer = false;

        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (quoted || character) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if ((quoted && ch == '"') || (character && ch == '\'')) {
                    quoted = false;
                    character = false;
                }
                continue;
            }
            if (ch == '"') {
                quoted = true;
                continue;
            }
            if (ch == '\'') {
                character = true;
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    depth = 1;
                    member.setLength(0);
                    continue;
                }
                if (depth == 1) {
                    String prefix = member.toString();
                    int equals = prefix.indexOf('=');
                    int paren = prefix.indexOf('(');
                    fieldInitializer = equals >= 0 && (paren < 0 || equals < paren);
                    if (!fieldInitializer) {
                        member.setLength(0);
                    }
                }
                depth++;
                continue;
            }
            if (ch == '}') {
                if (depth > 0) {
                    depth--;
                }
                if (depth == 1 && !fieldInitializer) {
                    member.setLength(0);
                } else if (depth == 1) {
                    member.append(' ');
                    fieldInitializer = false;
                } else if (depth == 0) {
                    member.setLength(0);
                }
                continue;
            }
            if (depth != 1) {
                continue;
            }
            member.append(ch);
            if (ch == ';') {
                String declaration = member.toString().strip().replaceAll("\\s+", " ");
                if (!declaration.isEmpty()) {
                    declarations.add(declaration);
                }
                member.setLength(0);
            }
        }
        return declarations;
    }

    /** Public/protected member headers, joined across their formatting lines. */
    private static List<String> visibleDeclarations(List<String> code) {
        List<String> declarations = new ArrayList<>();
        StringBuilder declaration = null;
        for (String line : code) {
            String trimmed = line.strip();
            if (declaration == null) {
                // Java permits annotations and modifiers before visibility.
                // The explicit prefix grammar catches that order without
                // mistaking method-body text such as "public Human" for an API.
                if (!VISIBLE_DECLARATION_START.matcher(trimmed).matches()) {
                    continue;
                }
                declaration = new StringBuilder(trimmed);
            } else if (!trimmed.isEmpty()) {
                declaration.append(' ').append(trimmed);
            }
            if (trimmed.contains("{") || trimmed.endsWith(";")) {
                declarations.add(declaration.toString());
                declaration = null;
            }
        }
        return declarations;
    }

    /** True only for the four public declarations that constitute D-013's jack. */
    private static boolean legacyLinkDeclaration(String declaration) {
        String compact = declaration.replaceAll("\\s+", "");
        return LEGACY_LINK_DECLARATIONS.contains(compact);
    }

    /** True only for the root declarations that name, but do not leak, a class. */
    private static boolean legacyRootDeclaration(String declaration) {
        String compact = declaration.replaceAll("\\s+", "");
        return LEGACY_ROOT_DECLARATIONS.contains(compact);
    }

    /** Retain the source reader against one clean tree and every named mutant. */
    private static void selfcheck() throws IOException {
        Path scratch = Files.createTempDirectory("lattice-fire-door-");
        List<Case> cases = new ArrayList<>();
        try {
            Map<String, String> clean = cleanSources();
            cases.add(readCase(scratch, "clean", clean, Reading::held));
            cases.add(readCase(scratch, "empty-refused", Map.of(),
                    reading -> reading.swept() == 0 && !reading.held()));

            cases.add(readCase(scratch, "entities-realworld",
                    changed(clean, "matrix/entities/Avatar.java",
                            "package matrix.entities;\n"
                                    + "import matrix . realworld . Human;\n"
                                    + "public final class Avatar { Human mind; }\n"),
                    reading -> oneFinding(reading, reading.entitiesReach())));
            cases.add(readCase(scratch, "entities-split-realworld",
                    changed(clean, "matrix/entities/Avatar.java",
                            "package matrix.entities;\n"
                                    + "import matrix\n"
                                    + "    . realworld\n"
                                    + "    . Human;\n"
                                    + "public final class Avatar { Human mind; }\n"),
                    reading -> oneFinding(reading, reading.entitiesReach())));
            cases.add(readCase(scratch, "core-realworld",
                    changed(clean, "matrix/core/Kernel.java",
                            "package matrix.core;\n"
                                    + "final class Kernel { matrix.realworld.Human mind; }\n"),
                    reading -> oneFinding(reading, reading.coreReach())));
            cases.add(readCase(scratch, "world-holds-human",
                    changed(clean, "matrix/core/World.java",
                            "package matrix.core;\n"
                                    + "public final class World {\n"
                                    + "  private final java.util.List<Human> minds = null;\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.worldHolds())));
            cases.add(readCase(scratch, "world-split-field",
                    changed(clean, "matrix/core/World.java",
                            "package matrix.core;\n"
                                    + "public final class World {\n"
                                    + "  Human\n"
                                    + "      mind = load();\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.worldHolds())));
            cases.add(readCase(scratch, "main-dependency",
                    changed(clean, "matrix/entities/Avatar.java",
                            "package matrix.entities;\n"
                                    + "public final class Avatar {\n"
                                    + "  void run() { matrix . Main . run(); }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.mainDepended())));
            cases.add(readCase(scratch, "causal-runtime-reach",
                    changed(clean, "matrix/causal/Leak.java",
                            "package matrix.causal;\n"
                                    + "final class Leak { matrix . realworld . Human mind; }\n"),
                    reading -> oneFinding(reading, reading.causalReach())));
            cases.add(readCase(scratch, "causal-split-reach",
                    changed(clean, "matrix/causal/Leak.java",
                            "package matrix.causal;\n"
                                    + "final class Leak { matrix\n"
                                    + "    . realworld\n"
                                    + "    . Human mind; }\n"),
                    reading -> oneFinding(reading, reading.causalReach())));
            cases.add(readCase(scratch, "reducer-queries-world",
                    changed(clean, "matrix/realworld/MindReducer.java",
                            "package matrix.realworld;\n"
                                    + "import matrix.causal.CausalRecord.PerceptReceipt;\n"
                                    + "import matrix.core.World;\n"
                                    + "final class MindReducer {\n"
                                    + "  PerceptReceipt reduce(World world) { return null; }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.reducerWorld())));
            cases.add(readCase(scratch, "reducer-receipts-only",
                    changed(clean, "matrix/realworld/MindReducer.java",
                            "package matrix.realworld;\n"
                                    + "import matrix.causal.CausalRecord.PerceptReceipt;\n"
                                    + "final class MindReducer {\n"
                                    + "  PerceptReceipt reduce() { return null; }\n"
                                    + "}\n"), Reading::held));
            cases.add(readCase(scratch, "legacy-jack-remains", clean,
                    reading -> reading.held() && reading.bridgeDeclarations() >= 4));
            cases.add(readCase(scratch, "link-mutable-transport",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            "package matrix.realworld;\n"
                                    + "public final class NeuralLink {\n"
                                    + "  public final Human human;\n"
                                    + "  public final Avatar avatar;\n"
                                    + "  public final LinkKind kind;\n"
                                    + "  public NeuralLink(Human human, Avatar avatar, LinkKind kind) {\n"
                                    + "    this.human = human; this.avatar = avatar; this.kind = kind;\n"
                                    + "  }\n"
                                    + "  public void deliver(\n"
                                    + "      CausalRecord.PerceptReceipt receipt,\n"
                                    + "      Human replacement) { }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-class-line-smuggle",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            "package matrix.realworld;\n"
                                    + "public final class NeuralLink { public Human leak() { return null; } }\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-modifier-smuggle",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            "package matrix.realworld;\n"
                                    + "public final class NeuralLink {\n"
                                    + "  @Deprecated static public Human leak() { return null; }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-string-is-not-api",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            "package matrix.realworld;\n"
                                    + "public final class NeuralLink {\n"
                                    + "  public final Human human;\n"
                                    + "  public final Avatar avatar;\n"
                                    + "  public final LinkKind kind;\n"
                                    + "  public NeuralLink(Human human, Avatar avatar, LinkKind kind) {\n"
                                    + "    this.human = human; this.avatar = avatar; this.kind = kind;\n"
                                    + "  }\n"
                                    + "  public void explain() {\n"
                                    + "    String text = \"public Human is not a declaration\";\n"
                                    + "  }\n"
                                    + "}\n"), Reading::held));
            cases.add(readCase(scratch, "simulation-mutable-export",
                    changed(clean, "matrix/Simulation.java",
                            "package matrix;\n"
                                    + "public final class Simulation {\n"
                                    + "  public Human exposeMind() { return null; }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            Map<String, String> futureMind = changed(clean, "matrix/realworld/MindState.java",
                    "package matrix.realworld;\npublic record MindState(String belief) { }\n");
            futureMind = changed(futureMind, "matrix/Simulation.java",
                    "package matrix;\n"
                            + "import matrix.realworld.MindState;\n"
                            + "public final class Simulation {\n"
                            + "  public MindState exposeMind() { return null; }\n"
                            + "}\n");
            cases.add(readCase(scratch, "future-mind-export", futureMind,
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            Map<String, String> comments = changed(clean, "matrix/entities/Avatar.java",
                    "package matrix.entities;\n"
                            + "// import matrix.realworld.Human;\n"
                            + "public final class Avatar { }\n");
            comments = changed(comments, "matrix/Simulation.java",
                    "package matrix;\n"
                            + "public final class Simulation {\n"
                            + "  /* public Human exposeMind() { return null; } */\n"
                            + "}\n");
            cases.add(readCase(scratch, "comments-ignored", comments, Reading::held));
        } finally {
            deleteTree(scratch);
        }

        int failed = 0;
        for (Case test : cases) {
            if (!test.held()) {
                failed++;
            }
            System.out.printf("LATTICE case=%-28s %s%n",
                    test.name(), test.held() ? "OK" : "BROKEN");
        }
        boolean held = !cases.isEmpty() && failed == 0;
        Probes.leave("LATTICE SELFCHECK VERDICT READER_HOLDS cases=" + cases.size()
                + " cases_none=" + (cases.isEmpty() ? 1 : 0)
                + " failed=" + failed, held);
    }

    private static boolean oneFinding(Reading reading, int named) {
        return named == 1 && reading.findings() == 1;
    }

    private static Case readCase(Path scratch, String name, Map<String, String> sources,
                                 Predicate<Reading> expectation) throws IOException {
        Path root = scratch.resolve(name);
        Files.createDirectories(root);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = root.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
        }
        return new Case(name, expectation.test(inspect(root)));
    }

    private static Map<String, String> cleanSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("matrix/entities/Avatar.java",
                "package matrix.entities;\npublic final class Avatar { }\n");
        sources.put("matrix/core/World.java",
                "package matrix.core;\n"
                        + "public final class World { private final int tick = 0; }\n");
        sources.put("matrix/Main.java",
                "package matrix;\npublic final class Main { }\n");
        sources.put("matrix/causal/CausalRecord.java",
                "package matrix.causal;\npublic interface CausalRecord { }\n");
        sources.put("matrix/realworld/NeuralLink.java",
                "package matrix.realworld;\n"
                        + "public final class NeuralLink {\n"
                        + "  public final Human human;\n"
                        + "  public final Avatar avatar;\n"
                        + "  public final LinkKind kind;\n"
                        + "  public NeuralLink(Human human, Avatar avatar, LinkKind kind) {\n"
                        + "    this.human = human; this.avatar = avatar; this.kind = kind;\n"
                        + "  }\n"
                        + "  public boolean closed() { return false; }\n"
                        + "}\n");
        sources.put("matrix/realworld/Human.java",
                "package matrix.realworld;\npublic final class Human { }\n");
        sources.put("matrix/Simulation.java",
                "package matrix;\n"
                        + "public final class Simulation {\n"
                        + "  public long tick() { return 0; }\n"
                        + "}\n");
        return sources;
    }

    private static Map<String, String> changed(Map<String, String> source,
                                                String path, String content) {
        Map<String, String> changed = new LinkedHashMap<>(source);
        changed.put(path, content);
        return changed;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private record Reading(int swept, int entitiesReach, int coreReach,
                           int worldHolds, int causalReach, int reducerWorld,
                           int bridgeMutable, int mainDepended, int worldFields,
                           int mindParticipants, int bridgeDeclarations,
                           List<String> offences) {
        private Reading {
            offences = List.copyOf(offences);
        }

        private int findings() {
            return entitiesReach + coreReach + worldHolds + causalReach
                    + reducerWorld + bridgeMutable + mainDepended;
        }

        private boolean held() {
            return swept > 0 && findings() == 0;
        }
    }

    private record Case(String name, boolean held) {}

    private LatticeFence() {}
}
