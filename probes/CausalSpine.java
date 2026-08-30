import matrix.Simulation;
import matrix.causal.CausalPhase;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Probe: D-066 has one executable phase order and {@code Simulation} owns it.
 *
 * <p>The production hooks landed intentionally empty in #1688; behavior now
 * enters them one leaf at a time. This probe's contract remains structural:
 * every normal tick must traverse the complete nine-phase order once, the root
 * must reject an out-of-order or repeated hand-over before it reaches a digest,
 * the completed receipt must be immutable, and no second production class may
 * advance the phase vocabulary. Each behavior leaf owns its own semantic
 * probe; {@code TruthSnapshots} is phase one's first such keeper.
 *
 * <p>Three tempting false greens are attacked directly through the private
 * root door. Calling delivery before snapshot, calling snapshot twice, and
 * calling observation before digest must each throw the root's ordering
 * refusal. Reflection is confined to this probe; it cannot install a callback
 * or mutate either world. The ordinary path is still exercised through
 * {@link Simulation#tickOnce()} and read only through the public immutable
 * receipt.
 *
 * <p>The source fence uses the JDK 17 compiler's attributed trees. It selects
 * the exact public instance no-argument {@code Simulation.tickOnce}, resolves
 * invocation and field owners, and proves structured dominance in that outer
 * block: snapshot before the exact SystemNode loop, then the causal digest hook
 * before the digest walk, observation, follow region and finish. This is not a
 * whole-program CFG theorem. Conditional/helper/callback/local-class forms at
 * these capabilities fail closed until their execution timing is modeled.
 * The SystemNode invocation may sit under ordinary control inside the one
 * certified enhanced-for body: the unconditional outer snapshot still
 * dominates every execution of that invocation, which is the claimed law;
 * the probe does not claim that every node must execute on every iteration.
 * Begin and snapshot are the first two outer statements. The digest walk's
 * attributed condition is exactly the tickOnce-local long {@code t} modulo
 * {@code Config.DIGEST_EVERY_TICKS} equal to zero (on either equality side,
 * with harmless parentheses); a lookalike if is not the cadence gate.
 * CausalPhase ownership likewise follows resolved symbols, never source text.
 *
 * <p>Usage: {@code java -cp out:probes/out CausalSpine [--root src]}
 */
public final class CausalSpine {

    private static final List<CausalPhase> EXPECTED = List.of(
            CausalPhase.SNAPSHOT_TRUTH,
            CausalPhase.DELIVER_PERCEPTS,
            CausalPhase.REDUCE_MINDS,
            CausalPhase.PROPOSE_INTENTS,
            CausalPhase.VALIDATE_AND_COMMIT,
            CausalPhase.APPLY_EFFECTS,
            CausalPhase.SETTLE_CONSEQUENCES,
            CausalPhase.DIGEST,
            CausalPhase.OBSERVE);

    private static final Map<String, Integer> CASES = new LinkedHashMap<>();
    private static final Map<String, Integer> FAILURES = new LinkedHashMap<>();
    private static final List<String> BREAKS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        Path root = arguments(args);
        if (!Files.isDirectory(root)) {
            System.out.println("FATAL CAUSAL_SPINE_ROOT_UNREADABLE root=" + root);
            System.exit(Probes.Outcome.REFUSED.code());
        }

        runtimeOrder();
        productionGuard();
        rootFence(root);
        scannerFalsifier();

        for (String broken : BREAKS) {
            System.out.println("CAUSAL_SPINE_BREAK " + broken);
        }
        int cases = CASES.values().stream().mapToInt(Integer::intValue).sum();
        boolean held = cases > 0
                && FAILURES.values().stream().mapToInt(Integer::intValue).sum() == 0;
        Probes.leave("VERDICT CAUSAL_SPINE_" + (held ? "HELD" : "BROKEN")
                + " cases=" + cases
                + " cases_none=" + (cases == 0 ? 1 : 0)
                + " order_fail=" + failures("order")
                + " guard_fail=" + failures("guard")
                + " root_fail=" + failures("root"), held);
    }

    /** A normal tick publishes the exact complete order, not a live list. */
    private static void runtimeOrder() {
        check("order", "enum-roster", List.of(CausalPhase.values()).equals(EXPECTED));
        check("order", "canonical-roster", CausalPhase.canonicalOrder().equals(EXPECTED));
        rejectsMutation("canonical-order-immutable", CausalPhase.canonicalOrder());

        Simulation simulation = new Simulation(42, null, null);
        check("order", "before-first-tick-empty", simulation.lastCausalPhases().isEmpty());
        simulation.tickOnce();
        List<CausalPhase> first = simulation.lastCausalPhases();
        check("order", "first-tick-complete", first.equals(EXPECTED));
        rejectsMutation("completed-receipt-immutable", first);

        simulation.tickOnce();
        List<CausalPhase> second = simulation.lastCausalPhases();
        check("order", "second-tick-does-not-accumulate", second.equals(EXPECTED));
    }

    /** The private root gate refuses the issue's three named mutant shapes. */
    private static void productionGuard() throws ReflectiveOperationException {
        Method begin = method("beginCausalTick");
        Method snapshot = method("snapshotTruth");
        Method delivery = method("deliverPercepts");
        Method reduce = method("reduceMinds");
        Method propose = method("proposeIntents");
        Method validate = method("validateAndCommit");
        Method effects = method("applyEffects");
        Method settle = method("settleConsequences");
        Method observe = method("observeCausalState");
        Method finish = method("finishCausalTick");

        Simulation reordered = new Simulation(42, null, null);
        begin.invoke(reordered);
        rejectsInvocation("delivery-before-snapshot", delivery, reordered);

        Simulation doubled = new Simulation(42, null, null);
        begin.invoke(doubled);
        snapshot.invoke(doubled);
        rejectsInvocation("snapshot-twice", snapshot, doubled);

        Simulation earlyObserver = new Simulation(42, null, null);
        begin.invoke(earlyObserver);
        for (Method phase : List.of(snapshot, delivery, reduce, propose, validate, effects, settle)) {
            phase.invoke(earlyObserver);
        }
        rejectsInvocation("observer-before-digest", observe, earlyObserver);
        rejectsInvocation("incomplete-tick-cannot-finish", finish, earlyObserver);
    }

    /**
     * Pin both ownership and placement in the source that actually ran. The
     * placement checks distinguish the phase receipt from a decorative list:
     * snapshot is before the node loop; digest wraps the existing seal; and
     * observation follows that seal before the current follow tap.
     */
    private static void rootFence(Path root) throws IOException {
        Fence reading = inspect(root);
        check("root", "source-population-nonempty", reading.swept() > 0);
        check("root", "one-vocabulary", reading.vocabularies() == 1);
        check("root", "one-root-user", reading.roots() == 1);
        check("root", "no-off-root-user", reading.offRoots() == 0);

        check("root", "snapshot-before-world-advance", reading.snapshotHeld());
        check("root", "digest-before-observation", reading.digestHeld());
    }

    /** Compiling fixtures retain semantic entry selection, order and refusal. */
    private static void scannerFalsifier() throws IOException {
        Path scratch = Files.createTempDirectory("causal-spine-fence-");
        try {
            fixture(scratch, "clean", tickBody("snapshotTruth();", "digestCausalState();"),
                    proof -> proof.snapshotHeld() && proof.digestHeld());
            fixture(scratch, "explicit-this",
                    tickBody("this.snapshotTruth();", "this.digestCausalState();"),
                    proof -> proof.snapshotHeld() && proof.digestHeld());
            fixture(scratch, "string-decoy-reorder",
                    tickBody("String decoy=\"snapshotTruth();\";", "digestCausalState();")
                            .replace("for(SystemNode n:nodes){n.tick(1);}",
                                    "for(SystemNode n:nodes){n.tick(1);} snapshotTruth();"),
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "duplicate-snapshot",
                    tickBody("snapshotTruth(); snapshotTruth();", "digestCausalState();"),
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "conditional-snapshot",
                    tickBody("if(nodes!=null) snapshotTruth();", "digestCausalState();"),
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "digest-reordered",
                    reorderedDigestBody(), proof -> !proof.digestHeld());
            fixture(scratch, "overload-isolated",
                    tickBody("snapshotTruth();", "digestCausalState();")
                            + " public void tickOnce(int ignored){ snapshotTruth(); }",
                    proof -> proof.snapshotHeld() && proof.digestHeld());
            fixture(scratch, "dead-method-isolated",
                    tickBody("", "digestCausalState();")
                            + " private void dead(){ snapshotTruth(); }",
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "helper-wrapped-snapshot-refused",
                    tickBody("helper();", "digestCausalState();")
                            + " private void helper(){ snapshotTruth(); }",
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "lambda-capability-refused",
                    tickBody("Runnable r=()->snapshotTruth(); snapshotTruth();",
                            "digestCausalState();"), proof -> !proof.snapshotHeld());
            fixture(scratch, "member-reference-capability-refused",
                    tickBody("Runnable r=this::snapshotTruth; snapshotTruth();",
                            "digestCausalState();"), proof -> !proof.snapshotHeld());
            fixture(scratch, "local-class-capability-refused",
                    tickBody("class Local { void run(){snapshotTruth();}} snapshotTruth();",
                            "digestCausalState();"), proof -> !proof.snapshotHeld());
            fixture(scratch, "duplicate-digest-refused",
                    tickBody("snapshotTruth();",
                            "digestCausalState(); digestCausalState();"),
                    proof -> !proof.digestHeld());
            fixture(scratch, "duplicate-node-tick-refused",
                    tickBody("snapshotTruth();", "digestCausalState();")
                            .replace("for(SystemNode n:nodes){n.tick(1);}",
                                    "for(SystemNode n:nodes){n.tick(1);}"
                                            + " for(SystemNode n:nodes){n.tick(1);}"),
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "duplicate-observe-refused",
                    tickBody("snapshotTruth();",
                            "digestCausalState(); observeCausalState();"),
                    proof -> !proof.digestHeld());
            fixture(scratch, "hook-overload-impersonation-refused",
                    tickBody("snapshotTruth(1);", "digestCausalState();")
                            + " private void snapshotTruth(int ignored){}",
                    proof -> !proof.snapshotHeld());
            fixture(scratch, "digest-target-substitution-refused",
                    tickBody("snapshotTruth();", "digestCausalState();")
                            .replace("real.digestInto(sink);",
                                    "world.digestInto(sink);"),
                    proof -> !proof.digestHeld());
            fixture(scratch, "digest-if-true-refused",
                    tickBody("snapshotTruth();", "digestCausalState();")
                            .replace("if(t%Config.DIGEST_EVERY_TICKS==0)", "if(true)"),
                    proof -> !proof.digestHeld());
            fixture(scratch, "digest-wrong-cadence-refused",
                    tickBody("snapshotTruth();", "digestCausalState();")
                            .replace("Config.DIGEST_EVERY_TICKS",
                                    "Config.FOLLOW_EVERY_TICKS"),
                    proof -> !proof.digestHeld());
            fixture(scratch, "early-helper-before-snapshot-refused",
                    tickBody("helper(); snapshotTruth();", "digestCausalState();")
                            + " private void helper(){}",
                    proof -> !proof.snapshotHeld());
            refusedFixture(scratch, "malformed", "package matrix; class Simulation { void x( }");
            refusedFixture(scratch, "unresolved", fixtureSource(
                    tickBody("missing(); snapshotTruth();", "digestCausalState();")));
            Path foreign = scratch.resolve("off-root/matrix/Foreign.java");
            writeFixture(scratch.resolve("off-root"), fixtureSource(
                    tickBody("snapshotTruth();", "digestCausalState();")));
            Files.writeString(foreign,
                    "package matrix; import matrix.causal.CausalPhase;"
                            + " class Foreign { CausalPhase phase; }\n");
            Fence escaped = inspect(scratch.resolve("off-root"));
            check("root", "fixture-off-root-red", escaped.offRoots() == 1);
        } finally {
            deleteTree(scratch);
        }
    }

    /** Attribute the complete supplied root; compiler errors are refusals. */
    private static Fence inspect(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        if (files.isEmpty()) return new Fence(0, 0, 0, 0, false, false);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("JDK compiler unavailable");
        Path emptyClasspath = Files.createTempDirectory("causal-spine-classpath-");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "17", "-proc:none", "-classpath",
                            emptyClasspath.toString()), null,
                    manager.getJavaFileObjectsFromPaths(files));
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            task.analyze();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IOException("causal spine source unreadable: "
                            + diagnostic.getMessage(null));
                }
            }
            SemanticProof proof = new SemanticProof(Trees.instance(task));
            for (CompilationUnitTree unit : units) proof.scan(unit, null);
            return proof.finish(files.size());
        } finally {
            Files.deleteIfExists(emptyClasspath);
        }
    }

    private static final class SemanticProof extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final List<TreePath> entries = new ArrayList<>();
        private final Set<String> phaseFiles = new java.util.HashSet<>();
        private int vocabularies;
        private TypeElement simulationType;
        private CompilationUnitTree unit;

        private SemanticProof(Trees trees) { this.trees = trees; }

        @Override public Void visitCompilationUnit(CompilationUnitTree node, Void unused) {
            CompilationUnitTree outer = unit;
            unit = node;
            super.visitCompilationUnit(node, unused);
            unit = outer;
            return null;
        }

        @Override public Void visitClass(com.sun.source.tree.ClassTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type
                    && type.getQualifiedName().contentEquals("matrix.causal.CausalPhase")) {
                vocabularies++;
            }
            if (element instanceof TypeElement type
                    && type.getQualifiedName().contentEquals("matrix.Simulation")) {
                simulationType = type;
            }
            return super.visitClass(node, unused);
        }

        @Override public Void visitMethod(MethodTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof ExecutableElement method
                    && owner(method).equals("matrix.Simulation")
                    && method.getSimpleName().contentEquals("tickOnce")
                    && method.getParameters().isEmpty()
                    && method.getModifiers().equals(Set.of(Modifier.PUBLIC))
                    && method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID
                    && method.getTypeParameters().isEmpty()
                    && node.getBody() != null) {
                entries.add(getCurrentPath());
            }
            return super.visitMethod(node, unused);
        }

        @Override public Void visitIdentifier(com.sun.source.tree.IdentifierTree node, Void unused) {
            phaseReference(trees.getElement(getCurrentPath()));
            return super.visitIdentifier(node, unused);
        }

        @Override public Void visitMemberSelect(com.sun.source.tree.MemberSelectTree node,
                Void unused) {
            phaseReference(trees.getElement(getCurrentPath()));
            return super.visitMemberSelect(node, unused);
        }

        private void phaseReference(Element element) {
            if (element == null || unit == null) return;
            TypeElement type = owningType(element);
            if (type != null && type.getQualifiedName()
                    .contentEquals("matrix.causal.CausalPhase")) {
                phaseFiles.add(Path.of(unit.getSourceFile().toUri()).toString());
            }
        }

        private Fence finish(int swept) throws IOException {
            if (entries.size() != 1 || vocabularies != 1 || !nodesCertificate()) {
                throw new IOException("causal spine entry/vocabulary ambiguous: entries="
                        + entries.size() + " vocabularies=" + vocabularies);
            }
            TreePath entry = entries.get(0);
            TickFacts facts = new TickFacts(trees, entry);
            facts.scan(new TreePath(entry, ((MethodTree) entry.getLeaf()).getBody()), null);
            int roots = 0;
            int offRoots = 0;
            for (String file : phaseFiles) {
                if (file.endsWith("/matrix/causal/CausalPhase.java")) continue;
                if (file.endsWith("/matrix/Simulation.java")) roots++;
                else offRoots++;
            }
            if (roots != 1 || facts.population() == 0) {
                throw new IOException("causal spine semantic populations vanished");
            }
            return new Fence(swept, vocabularies, roots, offRoots,
                    facts.snapshotHeld(), facts.digestHeld());
        }

        private boolean nodesCertificate() {
            if (simulationType == null) return false;
            return simulationType.getEnclosedElements().stream()
                    .filter(element -> element instanceof VariableElement)
                    .map(element -> (VariableElement) element)
                    .anyMatch(field -> field.getSimpleName().contentEquals("nodes")
                            && field.getModifiers().equals(
                                    Set.of(Modifier.PRIVATE, Modifier.FINAL))
                            && field.asType().toString()
                                    .equals("java.util.List<matrix.SystemNode>"));
        }
    }

    private static final class TickFacts extends TreePathScanner<Void, Void> {
        private static final Map<String, String> MARKERS = Map.of(
                "beginCausalTick", "matrix.Simulation#beginCausalTick()",
                "snapshotTruth", "matrix.Simulation#snapshotTruth()",
                "digestCausalState", "matrix.Simulation#digestCausalState()",
                "observeCausalState", "matrix.Simulation#observeCausalState()",
                "finishCausalTick", "matrix.Simulation#finishCausalTick()");
        private static final Set<String> DIGEST_WALK = Set.of(
                "matrix.core.World#digestInto(matrix.core.StateSink)",
                "matrix.realworld.RealWorld#digestInto(matrix.core.StateSink)",
                "matrix.realworld.Bond.Registry#digestInto(matrix.core.StateSink)",
                "matrix.core.DigestCalculator#finishHex()");
        private final Trees trees;
        private final BlockTree body;
        private final Map<String, List<Integer>> markerIndexes = new LinkedHashMap<>();
        private final List<Integer> nodeIndexes = new ArrayList<>();
        private final Map<String, List<Integer>> digestIndexes = new LinkedHashMap<>();
        private final List<Integer> digestConditionIndexes = new ArrayList<>();
        private final List<Integer> followIndexes = new ArrayList<>();
        private boolean boundary;

        private TickFacts(Trees trees, TreePath entry) {
            this.trees = trees;
            this.body = ((MethodTree) entry.getLeaf()).getBody();
        }

        @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof ExecutableElement method)) {
                boundary = true;
                return super.visitMethodInvocation(node, unused);
            }
            String key = key(method);
            int index = topIndex();
            String marker = method.getSimpleName().toString();
            if (MARKERS.getOrDefault(marker, "").equals(key)) {
                markerIndexes.computeIfAbsent(method.getSimpleName().toString(), ignored ->
                        new ArrayList<>()).add(directOuterCall() ? index : -1);
            }
            if (key.equals("matrix.SystemNode#tick(long)")) {
                StatementTree top = topStatement();
                boolean exactLoop = top instanceof EnhancedForLoopTree loop
                        && field(loop.getExpression(), "matrix.Simulation", "nodes")
                        && loopVariableIsSystemNode(loop);
                nodeIndexes.add(exactLoop ? index : -1);
            }
            if (DIGEST_WALK.contains(key)) digestIndexes
                    .computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
            return super.visitMethodInvocation(node, unused);
        }

        @Override public Void visitIf(IfTree node, Void unused) {
            int index = topIndex();
            if (index >= 0) {
                if (digestCondition(node)) digestConditionIndexes.add(index);
                FieldReads reads = new FieldReads(trees);
                reads.scan(new TreePath(getCurrentPath(), node.getCondition()), null);
                if (reads.keys.contains("matrix.Simulation#followName")
                        && reads.keys.contains("matrix.core.Config#FOLLOW_EVERY_TICKS")) {
                    followIndexes.add(index);
                }
            }
            return super.visitIf(node, unused);
        }

        @Override public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            boundary = true; return null;
        }

        @Override public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            boundary = true; return null;
        }

        @Override public Void visitClass(com.sun.source.tree.ClassTree node, Void unused) {
            boundary = true; return null;
        }

        private boolean snapshotHeld() {
            int begin = one("beginCausalTick");
            int snapshot = one("snapshotTruth");
            return !boundary && begin == 0 && snapshot == 1 && nodeIndexes.size() == 1
                    && nodeIndexes.get(0) > snapshot;
        }

        private boolean digestHeld() {
            int digest = one("digestCausalState");
            int observe = one("observeCausalState");
            int finish = one("finishCausalTick");
            if (boundary || digest < 0 || !digestIndexes.keySet().equals(DIGEST_WALK)
                    || followIndexes.size() != 1 || observe < 0 || finish < 0) return false;
            if (digestIndexes.values().stream().anyMatch(indexes -> indexes.size() != 1)) {
                return false;
            }
            int walk = digestIndexes.values().iterator().next().get(0);
            return digestIndexes.values().stream().allMatch(indexes -> indexes.get(0) == walk)
                    && body.getStatements().get(walk) instanceof IfTree
                    && digestConditionIndexes.equals(List.of(walk))
                    && digest < walk && walk < observe && observe < followIndexes.get(0)
                    && followIndexes.get(0) < finish;
        }

        private int population() {
            return markerIndexes.values().stream().mapToInt(List::size).sum()
                    + nodeIndexes.size() + digestIndexes.values().stream()
                            .mapToInt(List::size).sum() + followIndexes.size();
        }

        private int one(String marker) {
            List<Integer> indexes = markerIndexes.getOrDefault(marker, List.of());
            return indexes.size() == 1 ? indexes.get(0) : -1;
        }

        private boolean directOuterCall() {
            TreePath parent = getCurrentPath().getParentPath();
            return parent != null && parent.getLeaf() instanceof ExpressionStatementTree
                    && parent.getParentPath() != null
                    && parent.getParentPath().getLeaf() == body;
        }

        private int topIndex() {
            StatementTree statement = topStatement();
            return statement == null ? -1 : body.getStatements().indexOf(statement);
        }

        private StatementTree topStatement() {
            TreePath path = getCurrentPath();
            while (path != null && path.getParentPath() != null
                    && path.getParentPath().getLeaf() != body) path = path.getParentPath();
            return path != null && path.getLeaf() instanceof StatementTree statement
                    ? statement : null;
        }

        private boolean field(com.sun.source.tree.ExpressionTree expression,
                String owner, String name) {
            TreePath loopPath = getCurrentPath();
            while (loopPath != null
                    && !(loopPath.getLeaf() instanceof EnhancedForLoopTree)) {
                loopPath = loopPath.getParentPath();
            }
            if (loopPath == null) return false;
            Element element = trees.getElement(new TreePath(loopPath, expression));
            return element instanceof VariableElement variable
                    && owningType(variable) != null
                    && owningType(variable).getQualifiedName().contentEquals(owner)
                    && variable.getSimpleName().contentEquals(name);
        }

        private boolean digestCondition(IfTree tree) {
            TreePath ifPath = getCurrentPath();
            ExpressionTree condition = unwrap(tree.getCondition());
            if (!(condition instanceof BinaryTree equality)
                    || equality.getKind() != Tree.Kind.EQUAL_TO) return false;
            ExpressionTree left = unwrap(equality.getLeftOperand());
            ExpressionTree right = unwrap(equality.getRightOperand());
            ExpressionTree remainder = zero(left) ? right : zero(right) ? left : null;
            if (!(remainder instanceof BinaryTree modulo)
                    || modulo.getKind() != Tree.Kind.REMAINDER) return false;
            TreePath conditionPath = new TreePath(ifPath, tree.getCondition());
            Element tick = elementWithin(conditionPath, modulo.getLeftOperand());
            Element cadence = elementWithin(conditionPath, modulo.getRightOperand());
            return tick instanceof VariableElement local
                    && local.getKind() == javax.lang.model.element.ElementKind.LOCAL_VARIABLE
                    && local.getSimpleName().contentEquals("t")
                    && local.asType().toString().equals("long")
                    && local.getEnclosingElement() instanceof ExecutableElement method
                    && key(method).equals("matrix.Simulation#tickOnce()")
                    && cadence instanceof VariableElement field
                    && owningType(field) != null
                    && owningType(field).getQualifiedName().contentEquals("matrix.core.Config")
                    && field.getSimpleName().contentEquals("DIGEST_EVERY_TICKS");
        }

        private Element elementWithin(TreePath root, Tree target) {
            final Element[] found = new Element[1];
            new TreePathScanner<Void, Void>() {
                @Override public Void visitIdentifier(
                        com.sun.source.tree.IdentifierTree node, Void unused) {
                    if (node == target) found[0] = trees.getElement(getCurrentPath());
                    return super.visitIdentifier(node, unused);
                }
                @Override public Void visitMemberSelect(
                        com.sun.source.tree.MemberSelectTree node, Void unused) {
                    if (node == target) found[0] = trees.getElement(getCurrentPath());
                    return super.visitMemberSelect(node, unused);
                }
            }.scan(root, null);
            return found[0];
        }

        private static ExpressionTree unwrap(ExpressionTree expression) {
            while (expression instanceof ParenthesizedTree parentheses) {
                expression = parentheses.getExpression();
            }
            return expression;
        }

        private static boolean zero(ExpressionTree expression) {
            expression = unwrap(expression);
            return expression instanceof LiteralTree literal
                    && literal.getValue() instanceof Number number
                    && number.longValue() == 0;
        }

        private boolean loopVariableIsSystemNode(EnhancedForLoopTree loop) {
            TreePath path = getCurrentPath();
            while (path != null && path.getLeaf() != loop) path = path.getParentPath();
            if (path == null) return false;
            Element element = trees.getElement(new TreePath(path, loop.getVariable()));
            return element instanceof VariableElement variable
                    && variable.asType().toString().equals("matrix.SystemNode");
        }
    }

    private static final class FieldReads extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Set<String> keys = new java.util.HashSet<>();
        private FieldReads(Trees trees) { this.trees = trees; }
        @Override public Void visitIdentifier(com.sun.source.tree.IdentifierTree node, Void unused) {
            add(trees.getElement(getCurrentPath())); return super.visitIdentifier(node, unused);
        }
        @Override public Void visitMemberSelect(com.sun.source.tree.MemberSelectTree node,
                Void unused) {
            add(trees.getElement(getCurrentPath())); return super.visitMemberSelect(node, unused);
        }
        private void add(Element element) {
            if (element instanceof VariableElement field && owningType(field) != null) {
                keys.add(owningType(field).getQualifiedName() + "#" + field.getSimpleName());
            }
        }
    }

    private static String owner(ExecutableElement method) {
        return ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
    }

    private static TypeElement owningType(Element element) {
        while (element != null && !(element instanceof TypeElement)) {
            element = element.getEnclosingElement();
        }
        return (TypeElement) element;
    }

    private static String key(ExecutableElement method) {
        return owner(method) + "#" + method.getSimpleName() + "("
                + String.join(",", method.getParameters().stream()
                        .map(parameter -> parameter.asType().toString()).toList()) + ")";
    }

    private static void fixture(Path scratch, String name, String members,
            Predicate<Fence> expected) throws IOException {
        Path root = scratch.resolve(name);
        writeFixture(root, fixtureSource(members));
        check("root", "fixture-" + name, expected.test(inspect(root)));
    }

    private static void refusedFixture(Path scratch, String name, String simulation)
            throws IOException {
        Path root = scratch.resolve(name);
        if (simulation.startsWith("package matrix; class Simulation")) {
            Path file = root.resolve("matrix/Simulation.java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, simulation);
        } else {
            writeFixture(root, simulation);
        }
        boolean refused = false;
        try { inspect(root); } catch (IOException expected) { refused = true; }
        check("root", "fixture-" + name + "-refused", refused);
    }

    private static String tickBody(String snapshot, String digest) {
        return "public void tickOnce(){ beginCausalTick(); " + snapshot
                + " for(SystemNode n:nodes){n.tick(1);} "
                + "long t=1; " + digest
                + " if(t%Config.DIGEST_EVERY_TICKS==0){"
                + " world.digestInto(sink); real.digestInto(sink);"
                + " bonds.digestInto(sink); digests.finishHex(); }"
                + " observeCausalState();"
                + " if(followName!=null && Config.FOLLOW_EVERY_TICKS==1){}"
                + " finishCausalTick(); }";
    }

    private static String reorderedDigestBody() {
        return "public void tickOnce(){ beginCausalTick(); snapshotTruth();"
                + " for(SystemNode n:nodes){n.tick(1);}"
                + " long t=1; if(t%Config.DIGEST_EVERY_TICKS==0){"
                + " world.digestInto(sink); real.digestInto(sink);"
                + " bonds.digestInto(sink); digests.finishHex(); }"
                + " observeCausalState(); digestCausalState();"
                + " if(followName!=null && Config.FOLLOW_EVERY_TICKS==1){}"
                + " finishCausalTick(); }";
    }

    private static String fixtureSource(String members) {
        return "package matrix;\nimport java.util.*; import matrix.causal.CausalPhase;"
                + " import matrix.core.*; import matrix.realworld.*;\n"
                + "public class Simulation {"
                + " private final List<SystemNode> nodes=List.of();"
                + " private final World world=new World();"
                + " private final RealWorld real=new RealWorld();"
                + " private final Bond.Registry bonds=new Bond.Registry();"
                + " private final DigestCalculator digests=new DigestCalculator();"
                + " private final StateSink sink=null; private String followName;"
                + " private CausalPhase phase;"
                + " private void beginCausalTick(){} private void snapshotTruth(){}"
                + " private void digestCausalState(){} private void observeCausalState(){}"
                + " private void finishCausalTick(){} " + members + "}\n";
    }

    private static void writeFixture(Path root, String simulation) throws IOException {
        write(root, "matrix/Simulation.java", simulation);
        write(root, "matrix/SystemNode.java",
                "package matrix; public interface SystemNode { void tick(long tick); }\n");
        write(root, "matrix/core/Config.java",
                "package matrix.core; public final class Config {"
                        + " public static final int FOLLOW_EVERY_TICKS=1;"
                        + " public static final int DIGEST_EVERY_TICKS=1; }\n");
        write(root, "matrix/causal/CausalPhase.java",
                "package matrix.causal; public enum CausalPhase { SNAPSHOT_TRUTH }\n");
        write(root, "matrix/core/StateSink.java",
                "package matrix.core; public interface StateSink {}\n");
        write(root, "matrix/core/World.java",
                "package matrix.core; public class World {"
                        + " public void digestInto(StateSink sink){} }\n");
        write(root, "matrix/core/DigestCalculator.java",
                "package matrix.core; public class DigestCalculator {"
                        + " public String finishHex(){return \"\";} }\n");
        write(root, "matrix/realworld/RealWorld.java",
                "package matrix.realworld; import matrix.core.StateSink;"
                        + " public class RealWorld { public void digestInto(StateSink sink){} }\n");
        write(root, "matrix/realworld/Bond.java",
                "package matrix.realworld; import matrix.core.StateSink;"
                        + " public final class Bond { public static class Registry {"
                        + " public void digestInto(StateSink sink){} } }\n");
    }

    private static void write(Path root, String relative, String source) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) Files.deleteIfExists(path);
    }

    private static Path arguments(String[] args) {
        if (args.length == 0) {
            return Path.of("src");
        }
        if (args.length == 2 && args[0].equals("--root")) {
            return Path.of(args[1]);
        }
        System.exit(Probes.Outcome.REFUSED.code());
        throw new AssertionError("unreachable");
    }

    private static Method method(String name) throws NoSuchMethodException {
        Method method = Simulation.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static void rejectsInvocation(String name, Method method, Simulation simulation) {
        boolean refused = false;
        try {
            method.invoke(simulation);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            refused = e.getCause() instanceof IllegalStateException;
        }
        check("guard", name, refused);
    }

    private static void rejectsMutation(String name, List<CausalPhase> phases) {
        boolean addRefused = false;
        try {
            phases.add(CausalPhase.OBSERVE);
        } catch (UnsupportedOperationException expected) {
            addRefused = true;
        }
        check("order", name + "-add", addRefused);

        boolean setRefused = false;
        try {
            phases.set(0, CausalPhase.OBSERVE);
        } catch (UnsupportedOperationException expected) {
            setRefused = true;
        }
        check("order", name + "-set", setRefused);
    }

    private static boolean ordered(int... positions) {
        int previous = -1;
        for (int position : positions) {
            if (position <= previous) {
                return false;
            }
            previous = position;
        }
        return true;
    }

    private static void check(String subject, String name, boolean held) {
        CASES.merge(subject, 1, Integer::sum);
        if (!held) {
            FAILURES.merge(subject, 1, Integer::sum);
            BREAKS.add(subject + " case=" + name);
        }
    }

    private static int failures(String subject) {
        return FAILURES.getOrDefault(subject, 0);
    }

    private record Fence(int swept, int vocabularies, int roots, int offRoots,
                         boolean snapshotHeld, boolean digestHeld) {}

    private CausalSpine() {}
}
