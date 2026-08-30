import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

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
 * naming one. What is read is the attributed declared type of each field,
 * recursively through generic, array, wildcard and bounded forms. This does
 * not claim points-to identity hidden behind Object/shared supertypes, or
 * reflection and dynamic loading.
 *
 * <p>The JDK 17 compiler parses and attributes the complete supplied tree.
 * Package/type/member identity therefore comes from resolved elements rather
 * than comments, strings, formatting, Unicode spelling, or simple names. Any
 * syntax or attribution error is a refusal, never an empty green graph.
 *
 * <p>{@code --root DIR} points the whole reading at another tree.
 * {@code --selfcheck} builds semantically valid private source trees for every
 * clause and retains Unicode, inherited reducer, generic-erasure, multiline,
 * annotation, comment and string escape attempts.
 */
public final class LatticeFence {

    private static final String DEFAULT_ROOT = "src";

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
        if (files.isEmpty()) {
            return new Reading(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("JDK compiler unavailable for lattice fence");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "17", "-proc:none", "-classpath",
                            System.getProperty("java.class.path")), null,
                    manager.getJavaFileObjectsFromPaths(files));
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            task.analyze();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IOException("lattice source unreadable: "
                            + diagnostic.getMessage(null));
                }
            }
            Trees trees = Trees.instance(task);
            Elements elements = task.getElements();
            SemanticReading reading = new SemanticReading(trees, elements);
            for (CompilationUnitTree unit : units) {
                reading.scan(unit, null);
            }
            return reading.finish(files.size());
        }
    }

    /** One attributed source model; Unicode spelling and formatting vanish at attribution. */
    private static final class SemanticReading extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Elements elements;
        private final Map<String, TypeElement> types = new LinkedHashMap<>();
        private final java.util.Set<String> worldQueryOwners = new java.util.HashSet<>();
        private final List<String> offences = new ArrayList<>();
        private CompilationUnitTree unit;
        private boolean entitiesFile;
        private boolean coreFile;
        private boolean causalFile;
        private boolean mainFile;
        private boolean entitiesHit;
        private boolean coreHit;
        private boolean causalHit;
        private boolean mainHit;
        private int entitiesReach;
        private int coreReach;
        private int causalReach;
        private int mainDepended;

        private SemanticReading(Trees trees, Elements elements) {
            this.trees = trees;
            this.elements = elements;
        }

        @Override public Void visitCompilationUnit(CompilationUnitTree node, Void unused) {
            CompilationUnitTree outer = unit;
            unit = node;
            String pkg = node.getPackageName() == null ? "" : node.getPackageName().toString();
            entitiesFile = pkg.equals("matrix.entities");
            coreFile = pkg.equals("matrix.core");
            causalFile = pkg.equals("matrix.causal");
            mainFile = node.getSourceFile().getName().endsWith("/Main.java");
            entitiesHit = coreHit = causalHit = mainHit = false;
            super.visitCompilationUnit(node, unused);
            unit = outer;
            return null;
        }

        @Override public Void visitClass(com.sun.source.tree.ClassTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type) {
                types.put(type.getQualifiedName().toString(), type);
            }
            return super.visitClass(node, unused);
        }

        @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
            reference(trees.getElement(getCurrentPath()));
            return super.visitIdentifier(node, unused);
        }

        @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            reference(trees.getElement(getCurrentPath()));
            return super.visitMemberSelect(node, unused);
        }

        private void reference(Element element) {
            TypeElement owner = owningType(element);
            if (owner == null) return;
            String name = owner.getQualifiedName().toString();
            String pkg = elements.getPackageOf(owner).getQualifiedName().toString();
            if (entitiesFile && pkg.equals("matrix.realworld") && !entitiesHit) {
                entitiesHit = true; entitiesReach++;
                offences.add("LATTICE entities reaches " + name);
            }
            if (coreFile && pkg.equals("matrix.realworld") && !coreHit) {
                coreHit = true; coreReach++;
                offences.add("LATTICE core reaches " + name);
            }
            if (causalFile && pkg.startsWith("matrix.") && !pkg.equals("matrix.causal")
                    && !causalHit) {
                causalHit = true; causalReach++;
                offences.add("LATTICE causal reaches " + name);
            }
            if (!mainFile && name.equals("matrix.Main") && !mainHit) {
                mainHit = true; mainDepended++;
                offences.add("LATTICE depends on matrix.Main");
            }
            TypeElement current = enclosingSourceType();
            if (current != null && (name.equals("matrix.core.World")
                    || containsQualified(element.asType(), "matrix.core.World"))) {
                worldQueryOwners.add(current.getQualifiedName().toString());
            }
        }

        private Reading finish(int swept) throws IOException {
            TypeElement world = types.get("matrix.core.World");
            int worldFields = 0;
            int worldHolds = 0;
            if (world != null) {
                for (Element member : world.getEnclosedElements()) {
                    if (member.getKind() == ElementKind.FIELD
                            || member.getKind() == ElementKind.RECORD_COMPONENT) {
                        worldFields++;
                        if (containsPackage(member.asType(), "matrix.realworld")) {
                            worldHolds++;
                            offences.add("LATTICE World holds " + member.asType());
                        }
                    }
                }
            }
            int mindParticipants = 0;
            int reducerWorld = 0;
            for (TypeElement type : types.values()) {
                if (!elements.getPackageOf(type).getQualifiedName()
                        .contentEquals("matrix.realworld")) continue;
                List<? extends Element> members = elements.getAllMembers(type);
                boolean participant = members.stream().anyMatch(this::mindSignature);
                if (!participant) continue;
                mindParticipants++;
                boolean declaredWorldCapability = members.stream().anyMatch(member ->
                        member.getKind() == ElementKind.FIELD
                                && containsQualified(member.asType(), "matrix.core.World")
                        || member instanceof ExecutableElement method
                                && (containsQualified(method.getReturnType(), "matrix.core.World")
                                || method.getParameters().stream().anyMatch(parameter ->
                                containsQualified(parameter.asType(), "matrix.core.World"))));
                boolean usedWorldCapability = members.stream()
                                .map(SemanticReading::owningType)
                                .filter(java.util.Objects::nonNull)
                                .map(owner -> owner.getQualifiedName().toString())
                                .anyMatch(worldQueryOwners::contains);
                boolean worldCapability = declaredWorldCapability || usedWorldCapability;
                if (worldCapability) {
                    reducerWorld++;
                    offences.add("LATTICE reducer " + type.getQualifiedName()
                            + " inherits/holds World");
                }
            }
            int bridgeDeclarations = 0;
            int bridgeMutable = 0;
            for (String bridge : List.of("matrix.Simulation", "matrix.realworld.NeuralLink")) {
                TypeElement type = types.get(bridge);
                if (type == null) continue;
                for (Element member : elements.getAllMembers(type)) {
                    if (!sourceMember(member) || !visible(member)) continue;
                    bridgeDeclarations++;
                    if (auditedBridge(bridge, member)) continue;
                    if (mutableApi(member)) {
                        bridgeMutable++;
                        offences.add("LATTICE bridge " + bridge + " exposes " + member);
                    }
                }
            }
            if (swept == 0 || types.isEmpty() || world == null
                    || worldFields == 0 || bridgeDeclarations == 0) {
                throw new IOException("lattice semantic populations vanished");
            }
            return new Reading(swept, entitiesReach, coreReach, worldHolds, causalReach,
                    reducerWorld, bridgeMutable, mainDepended, worldFields,
                    mindParticipants, bridgeDeclarations, offences);
        }

        private boolean mindSignature(Element member) {
            if (!(member instanceof ExecutableElement method)) return false;
            if (causalMindType(method.getReturnType())) return true;
            return method.getParameters().stream().anyMatch(p -> causalMindType(p.asType()));
        }

        private boolean causalMindType(TypeMirror type) {
            return containsQualified(type, "matrix.causal.CausalRecord")
                    || containsQualified(type, "matrix.causal.CausalRecord.PerceptReceipt")
                    || containsQualified(type, "matrix.causal.CausalRecord.IntentProposal");
        }

        private boolean auditedBridge(String bridge, Element member) {
            String name = member.getSimpleName().toString();
            if (bridge.equals("matrix.realworld.NeuralLink")) {
                if (member instanceof VariableElement field) {
                    return field.getModifiers().equals(Set.of(Modifier.PUBLIC, Modifier.FINAL))
                            && Map.of("human", "matrix.realworld.Human",
                            "avatar", "matrix.entities.Avatar",
                            "kind", "matrix.realworld.LinkKind")
                            .getOrDefault(name, "").equals(field.asType().toString());
                }
                if (member.getKind() == ElementKind.CONSTRUCTOR
                        && member instanceof ExecutableElement constructor) {
                    return constructor.getModifiers().equals(Set.of(Modifier.PUBLIC))
                            && parameterTypes(constructor).equals(List.of("matrix.realworld.Human",
                            "matrix.entities.Avatar", "matrix.realworld.LinkKind"));
                }
                return false;
            }
            if (member instanceof ExecutableElement method) {
                if (member.getKind() == ElementKind.CONSTRUCTOR) {
                    return method.getModifiers().equals(Set.of(Modifier.PUBLIC))
                            && (parameterTypes(method).equals(List.of("long", "java.io.OutputStream",
                            "java.lang.String")) || parameterTypes(method).equals(List.of("long",
                            "java.io.OutputStream", "java.lang.String", "java.io.OutputStream")));
                }
                if (!method.getModifiers().equals(Set.of(Modifier.PUBLIC))) return false;
                Map<String, List<String>> parameters = Map.of(
                        "births", List.of(), "lastCausalPhases", List.of(),
                        "run", List.of("long"), "snapshotNow", List.of());
                return parameters.getOrDefault(name, List.of("<absent>"))
                        .equals(parameterTypes(method))
                        && Map.of("births", "java.util.List<matrix.core.ChronosLog.Birth>",
                        "lastCausalPhases", "java.util.List<matrix.causal.CausalPhase>",
                        "run", "java.util.List<matrix.core.Digest>",
                        "snapshotNow", "matrix.core.Snapshot")
                        .getOrDefault(name, "").equals(method.getReturnType().toString());
            }
            return false;
        }

        private boolean mutableApi(Element member) {
            if (member instanceof VariableElement field) return mutableType(field.asType());
            if (member instanceof ExecutableElement method) {
                if (mutableType(method.getReturnType())) return true;
                if (method.getParameters().stream().anyMatch(p -> mutableType(p.asType()))) return true;
                return method.getTypeParameters().stream().anyMatch(p -> mutableType(p.asType()));
            }
            return false;
        }

        private boolean mutableType(TypeMirror type) {
            if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID) return false;
            if (type.getKind() == TypeKind.TYPEVAR) return true;
            if (type.getKind() == TypeKind.ARRAY) return mutableType(((ArrayType) type).getComponentType());
            if (type instanceof DeclaredType declared) {
                TypeElement element = (TypeElement) declared.asElement();
                String pkg = elements.getPackageOf(element).getQualifiedName().toString();
                if (pkg.startsWith("matrix.")) return true;
                return declared.getTypeArguments().stream().anyMatch(this::mutableType);
            }
            return true;
        }

        private boolean containsPackage(TypeMirror type, String pkg) {
            return containsPackage(type, pkg, new java.util.HashSet<>());
        }

        private boolean containsPackage(TypeMirror type, String pkg, java.util.Set<String> seen) {
            if (!seen.add(type.getKind() + ":" + type)) return false;
            if (type instanceof DeclaredType declared) {
                TypeElement element = (TypeElement) declared.asElement();
                if (elements.getPackageOf(element).getQualifiedName().contentEquals(pkg)) return true;
                return declared.getTypeArguments().stream().anyMatch(t -> containsPackage(t, pkg, seen));
            }
            if (type instanceof ArrayType array) return containsPackage(array.getComponentType(), pkg, seen);
            if (type instanceof TypeVariable variable) return containsPackage(variable.getUpperBound(), pkg, seen)
                    || containsPackage(variable.getLowerBound(), pkg, seen);
            if (type instanceof WildcardType wildcard) return (wildcard.getExtendsBound() != null
                    && containsPackage(wildcard.getExtendsBound(), pkg, seen))
                    || (wildcard.getSuperBound() != null
                    && containsPackage(wildcard.getSuperBound(), pkg, seen));
            return false;
        }

        private boolean containsQualified(TypeMirror type, String qualified) {
            return containsQualified(type, qualified, new java.util.HashSet<>());
        }

        private boolean containsQualified(TypeMirror type, String qualified,
                java.util.Set<String> seen) {
            if (!seen.add(type.getKind() + ":" + type)) return false;
            if (type instanceof DeclaredType declared) {
                TypeElement element = (TypeElement) declared.asElement();
                if (element.getQualifiedName().contentEquals(qualified)) return true;
                return declared.getTypeArguments().stream().anyMatch(
                        t -> containsQualified(t, qualified, seen));
            }
            if (type instanceof ArrayType array) return containsQualified(array.getComponentType(), qualified, seen);
            if (type instanceof TypeVariable variable) return containsQualified(variable.getUpperBound(), qualified, seen);
            if (type instanceof WildcardType wildcard) return wildcard.getExtendsBound() != null
                    && containsQualified(wildcard.getExtendsBound(), qualified, seen);
            return false;
        }

        private static boolean visible(Element member) {
            return member.getModifiers().contains(Modifier.PUBLIC)
                    || member.getModifiers().contains(Modifier.PROTECTED);
        }

        private boolean sourceMember(Element member) {
            TypeElement owner = owningType(member);
            return owner != null && types.containsKey(owner.getQualifiedName().toString());
        }

        private static TypeElement owningType(Element element) {
            while (element != null && !(element instanceof TypeElement)) {
                element = element.getEnclosingElement();
            }
            return (TypeElement) element;
        }

        private TypeElement enclosingSourceType() {
            for (var path = getCurrentPath(); path != null; path = path.getParentPath()) {
                Element element = trees.getElement(path);
                if (element instanceof TypeElement type) return type;
            }
            return null;
        }

        private static List<String> parameterTypes(ExecutableElement method) {
            return method.getParameters().stream().map(p -> p.asType().toString()).toList();
        }
    }
    /** Retain the semantic reader against one clean tree and every named mutant. */
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
            cases.add(readCase(scratch, "entities-unicode-realworld",
                    changed(clean, "matrix/entities/Avatar.java",
                            "package matrix.entities;\n"
                                    + "import matrix.\\u0072ealworld.Human;\n"
                                    + "public final class Avatar { private Human mind; }\n"),
                    reading -> oneFinding(reading, reading.entitiesReach())));
            cases.add(readCase(scratch, "core-realworld",
                    changed(clean, "matrix/core/Kernel.java",
                            "package matrix.core;\n"
                                    + "final class Kernel { matrix.realworld.Human mind; }\n"),
                    reading -> oneFinding(reading, reading.coreReach())));
            cases.add(readCase(scratch, "world-holds-human",
                    changed(clean, "matrix/core/World.java",
                            "package matrix.core;\n"
                                    + "import matrix.realworld.Human;\n"
                                    + "public final class World {\n"
                                    + "  private final java.util.List<Human> minds = null;\n"
                                    + "}\n"),
                    reading -> reading.worldHolds() == 1 && reading.coreReach() == 1
                            && reading.findings() == 2));
            cases.add(readCase(scratch, "world-split-field",
                    changed(clean, "matrix/core/World.java",
                            "package matrix.core;\n"
                                    + "import matrix.realworld.Human;\n"
                                    + "public final class World {\n"
                                    + "  Human\n"
                                    + "      mind = null;\n"
                                    + "}\n"),
                    reading -> reading.worldHolds() == 1 && reading.coreReach() == 1
                            && reading.findings() == 2));
            cases.add(readCase(scratch, "main-dependency",
                    changed(clean, "matrix/entities/Avatar.java",
                            "package matrix.entities;\n"
                                    + "public final class Avatar {\n"
                                    + "  Class<?> root() { return matrix . Main . class; }\n"
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
            cases.add(readCase(scratch, "generic-reducer-world",
                    changed(clean, "matrix/realworld/GenericReducer.java",
                            "package matrix.realworld;\n"
                                    + "import matrix.causal.CausalRecord;\n"
                                    + "import matrix.core.World;\n"
                                    + "final class GenericReducer {\n"
                                    + "  CausalRecord reduce(World world) { return null; }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.reducerWorld())));
            Map<String, String> inheritedReducer = changed(clean,
                    "matrix/realworld/ReceiptConsumer.java",
                    "package matrix.realworld;\n"
                            + "import matrix.causal.CausalRecord.PerceptReceipt;\n"
                            + "interface ReceiptConsumer { void reduce(PerceptReceipt value); }\n");
            inheritedReducer = changed(inheritedReducer,
                    "matrix/realworld/WorldReader.java",
                    "package matrix.realworld;\nimport matrix.core.World;\n"
                            + "class WorldReader { protected final World world = null; }\n");
            inheritedReducer = changed(inheritedReducer,
                    "matrix/realworld/MindReducer.java",
                    "package matrix.realworld;\n"
                            + "final class MindReducer extends WorldReader"
                            + " implements ReceiptConsumer {\n"
                            + " public void reduce(matrix.causal.CausalRecord.PerceptReceipt value)"
                            + " { world.toString(); } }\n");
            cases.add(readCase(scratch, "inherited-reducer-world", inheritedReducer,
                    reading -> reading.reducerWorld() >= 1 && !reading.held()));
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
                                    + "import matrix.entities.Avatar;\n"
                                    + "import matrix.causal.CausalRecord;\n"
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
                                    + "import matrix.entities.Avatar;\n"
                                    + "public final class NeuralLink {\n"
                                    + "  @Deprecated static public Human leak() { return null; }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-protected-human-refused",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            linkFieldFixture("protected final Human human = null;")),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-static-human-refused",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            linkFieldFixture("public static final Human human = null;")),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-nonfinal-human-refused",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            linkFieldFixture("public Human human;")),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "link-string-is-not-api",
                    changed(clean, "matrix/realworld/NeuralLink.java",
                            "package matrix.realworld;\n"
                                    + "import matrix.entities.Avatar;\n"
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
                                    + "import matrix.realworld.Human;\n"
                                    + "public final class Simulation {\n"
                                    + "  public Human exposeMind() { return null; }\n"
                                    + "}\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "simulation-generic-erasure-export",
                    changed(clean, "matrix/Simulation.java",
                            "package matrix;\nimport matrix.realworld.Human;\n"
                                    + "public final class Simulation {"
                                    + " private final Human hidden = new Human();"
                                    + " @SuppressWarnings(\"unchecked\")"
                                    + " public <T> T exposeMind() { return (T) hidden; } }\n"),
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            Map<String, String> runOverload = changed(clean, "matrix/core/Digest.java",
                    "package matrix.core; public record Digest(long tick, String value) {}\n");
            runOverload = changed(runOverload, "matrix/Simulation.java",
                    "package matrix;\nimport java.util.List;\n"
                            + "import matrix.core.Digest; import matrix.realworld.Human;\n"
                            + "public final class Simulation {"
                            + " public List<Digest> run(Human ignored) { return List.of(); } }\n");
            cases.add(readCase(scratch, "simulation-run-overload-refused", runOverload,
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            Map<String, String> futureMind = changed(clean, "matrix/realworld/Human.java",
                    "package matrix.realworld;\n"
                            + "public final class Human {\n"
                            + "  public record MindState(String belief) { }\n"
                            + "}\n");
            futureMind = changed(futureMind, "matrix/Simulation.java",
                    "package matrix;\n"
                            + "import matrix.realworld.Human.MindState;\n"
                            + "public final class Simulation {\n"
                            + "  public MindState exposeMind() { return null; }\n"
                            + "}\n");
            cases.add(readCase(scratch, "future-mind-export", futureMind,
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            Map<String, String> futureCausal = changed(clean,
                    "matrix/causal/MutableMind.java",
                    "package matrix.causal;\n"
                            + "public enum MutableMind { INSTANCE; int belief; }\n");
            futureCausal = changed(futureCausal, "matrix/Simulation.java",
                    "package matrix;\n"
                            + "import matrix.causal.MutableMind;\n"
                            + "public final class Simulation {\n"
                            + "  public MutableMind exposeMind() { return null; }\n"
                            + "}\n");
            cases.add(readCase(scratch, "future-causal-enum", futureCausal,
                    reading -> oneFinding(reading, reading.bridgeMutable())));
            cases.add(readCase(scratch, "type-words-ignored",
                    changed(clean, "matrix/Noise.java",
                            "package matrix;\n"
                                    + "final class Noise {\n"
                                    + "  String text = \"record LinkKind\";\n"
                                    + "}\n"), Reading::held));
            Map<String, String> futureWorld = changed(clean,
                    "matrix/realworld/Mind$State.java",
                    "package matrix.realworld;\npublic record Mind$State(String belief) { }\n");
            futureWorld = changed(futureWorld, "matrix/core/World.java",
                    "package matrix.core;\n"
                            + "import matrix.realworld.Mind$State;\n"
                            + "public final class World { private Mind$State mind; }\n");
            cases.add(readCase(scratch, "world-future-mind", futureWorld,
                    reading -> reading.coreReach() == 1 && reading.worldHolds() == 1
                            && reading.findings() == 2));
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
            cases.add(refusedCase(scratch, "malformed-refused",
                    changed(clean, "matrix/entities/Broken.java",
                            "package matrix.entities; final class Broken { void x( }")));
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

    /** Invalid or unattributed Java must be a refusal, never an empty green graph. */
    private static Case refusedCase(Path scratch, String name, Map<String, String> sources)
            throws IOException {
        Path root = scratch.resolve(name);
        Files.createDirectories(root);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = root.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
        }
        try {
            inspect(root);
            return new Case(name, false);
        } catch (IOException expected) {
            return new Case(name, true);
        }
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
                "package matrix.causal;\npublic interface CausalRecord {"
                        + " record PerceptReceipt() implements CausalRecord {}"
                        + " record IntentProposal() implements CausalRecord {} }\n");
        sources.put("matrix/realworld/NeuralLink.java",
                "package matrix.realworld;\n"
                        + "import matrix.entities.Avatar;\n"
                        + "public final class NeuralLink {\n"
                        + "  public final Human human;\n"
                        + "  public final Avatar avatar;\n"
                        + "  public final LinkKind kind;\n"
                        + "  public NeuralLink(Human human, Avatar avatar, LinkKind kind) {\n"
                        + "    this.human = human; this.avatar = avatar; this.kind = kind;\n"
                        + "  }\n"
                        + "  public boolean closed() { return false; }\n"
                        + "}\n");
        sources.put("matrix/realworld/LinkKind.java",
                "package matrix.realworld;\npublic enum LinkKind { POD }\n");
        sources.put("matrix/realworld/Human.java",
                "package matrix.realworld;\npublic final class Human { }\n");
        sources.put("matrix/Simulation.java",
                "package matrix;\n"
                        + "public final class Simulation {\n"
                        + "  public long tick() { return 0; }\n"
                        + "}\n");
        return sources;
    }

    /** One historical jack with a deliberately misshaped Human field. */
    private static String linkFieldFixture(String humanField) {
        return "package matrix.realworld;\nimport matrix.entities.Avatar;\n"
                + "public final class NeuralLink { " + humanField + "\n"
                + " public final Avatar avatar; public final LinkKind kind;\n"
                + " public NeuralLink(Human human, Avatar avatar, LinkKind kind) {"
                + " this.avatar=avatar; this.kind=kind; } }\n";
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
