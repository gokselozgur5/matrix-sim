import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import matrix.causal.CausalId;
import matrix.causal.CausalRecord;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Probe: does one delivery attempt become only the projection one mind may see?
 *
 * <p>The runtime half attacks the complete visible tuple, delivered/degraded/
 * occluded shapes, hidden-audit twins, uncertainty, tick agreement, explicit
 * refusal of unproven silence and constructor contradictions. The source half asks
 * JDK 17 to parse and attribute the complete production tree against an empty
 * project classpath. It admits a closed positive roster of exact accessors,
 * constructors and enum constants; actual truth/source, live worlds,
 * reflection, callbacks, native/bodyless helpers and unmodeled types therefore
 * have no door rather than depending on a blacklist of suspicious spellings.
 *
 * <p>This proves one-attempt projection only. Identity allocation, batching,
 * ordering, gaps, deduplication and reducer/intent noninterference remain
 * #1693's jurisdiction.
 */
public final class PerceptReceipts {
    private static int cases;
    private static int failures;
    private static final Map<String, Integer> BY_KIND = new HashMap<>();

    private record Fixture(CausalRecord.Subject subject,
                           CausalRecord.Principal claimed,
                           CausalRecord.Payload visible,
                           CausalRecord.DeliveryAttempt delivered,
                           CausalRecord.DeliveryAttempt degraded,
                           CausalRecord.DeliveryAttempt occluded) {}

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        Path root = Path.of("src");
        boolean suppliedRoot = false;
        if (args.length != 0) {
            if (args.length != 2 || !args[0].equals("--root")) {
                System.err.println("Usage: PerceptReceipts [--root DIR]");
                System.exit(Probes.Outcome.REFUSED.code());
            }
            root = Path.of(args[1]);
            suppliedRoot = true;
        }
        Fixture fixture = fixture();
        projectionMatrix(fixture);
        hiddenAuditTwins(fixture);
        constructorAndApiContract(fixture);
        sourceContract(root);
        if (!suppliedRoot) sourceMutants(root);

        System.out.printf("PERCEPT_RECEIPT_CENSUS cases=%d projection=%d hidden=%d"
                        + " constructor=%d silence=%d source=%d roster=%d%n",
                cases, cases("projection"), cases("hidden"), cases("constructor"),
                cases("silence"), cases("source"), cases("roster"));
        Probes.leave(String.format(
                        "VERDICT PERCEPT_RECEIPTS_%s cases=%d cases_none=%d"
                                + " projection_fail=%d hidden_leak=%d constructor_fail=%d"
                                + " silence_fail=%d source_fail=%d roster_fail=%d",
                        failures == 0 && cases > 0 ? "HELD" : "BROKEN",
                        cases, cases == 0 ? 1 : 0, failures("projection"),
                        failures("hidden"), failures("constructor"), failures("silence"),
                        failures("source"), failures("roster")),
                failures == 0 && cases > 0);
    }

    private static Fixture fixture() {
        CausalRecord.Subject subject = new CausalRecord.Subject("human-7");
        CausalRecord.Principal human = principal(CausalRecord.PrincipalKind.HUMAN, "human-7");
        CausalRecord.Principal claimed = principal(CausalRecord.PrincipalKind.SYSTEM,
                "matrix-world");
        CausalRecord.Principal actual = principal(CausalRecord.PrincipalKind.SYSTEM,
                "sensor-a");
        CausalRecord.Payload visible = new CausalRecord.Payload("12,34");
        CausalRecord.TruthEntry exactTruth = truth(7, 0, human, visible, claimed);
        CausalRecord.DeliveryAttempt delivered = attempt(exactTruth, subject,
                CausalRecord.Channel.VISION, claimed, claimed, CausalRecord.Fidelity.FULL,
                CausalRecord.DeliveryOutcome.DELIVERED, Optional.of(visible));

        CausalRecord.TruthEntry hiddenTruth = truth(7, 1, human,
                new CausalRecord.Payload("99,88"), actual);
        CausalRecord.DeliveryAttempt degraded = attempt(hiddenTruth, subject,
                CausalRecord.Channel.VISION, actual, claimed, CausalRecord.Fidelity.PARTIAL,
                CausalRecord.DeliveryOutcome.DEGRADED, Optional.of(visible));
        CausalRecord.DeliveryAttempt occluded = attempt(exactTruth, subject,
                CausalRecord.Channel.VISION, claimed, claimed, CausalRecord.Fidelity.NONE,
                CausalRecord.DeliveryOutcome.OCCLUDED, Optional.empty());
        return new Fixture(subject, claimed, visible, delivered, degraded, occluded);
    }

    private static void projectionMatrix(Fixture fixture) {
        matrix.causal.PerceptReceipts.Presentation presented =
                new matrix.causal.PerceptReceipts.Presentation(
                        new CausalId.Percept(7, 3), 321);
        CausalRecord.ReceiptAudit delivered = matrix.causal.PerceptReceipts.project(
                fixture.delivered, Optional.of(presented)).orElseThrow();
        exactProjection("delivered", delivered, fixture.delivered, presented,
                CausalRecord.Fidelity.FULL);

        CausalRecord.ReceiptAudit degraded = matrix.causal.PerceptReceipts.project(
                fixture.degraded, Optional.of(presented)).orElseThrow();
        exactProjection("degraded", degraded, fixture.degraded, presented,
                CausalRecord.Fidelity.PARTIAL);

        check("silence", "occluded-empty",
                matrix.causal.PerceptReceipts.project(
                        fixture.occluded, Optional.empty()).isEmpty());
        rejects("silence", "occluded-refuses-visible-id",
                () -> matrix.causal.PerceptReceipts.project(
                        fixture.occluded, Optional.of(presented)));
        rejects("projection", "presented-needs-metadata",
                () -> matrix.causal.PerceptReceipts.project(
                        fixture.delivered, Optional.empty()));
        rejects("projection", "tick-must-match",
                () -> matrix.causal.PerceptReceipts.project(fixture.delivered,
                        Optional.of(new matrix.causal.PerceptReceipts.Presentation(
                                new CausalId.Percept(8, 0), 321))));

        CausalRecord.TruthEntry noSignalTruth = truth(7, 2,
                principal(CausalRecord.PrincipalKind.HUMAN, "human-7"),
                new CausalRecord.Payload("link-heartbeat-absent"), fixture.claimed);
        CausalRecord.DeliveryAttempt explicitNoSignal = attempt(noSignalTruth,
                fixture.subject, CausalRecord.Channel.NO_SIGNAL,
                fixture.claimed, fixture.claimed, CausalRecord.Fidelity.FULL,
                CausalRecord.DeliveryOutcome.DELIVERED,
                Optional.of(noSignalTruth.fact().value()));
        rejects("silence", "no-signal-needs-typed-availability",
                () -> matrix.causal.PerceptReceipts.project(explicitNoSignal,
                        Optional.of(new matrix.causal.PerceptReceipts.Presentation(
                                new CausalId.Percept(7, 4), 0))));
    }

    private static void exactProjection(String name, CausalRecord.ReceiptAudit audit,
                                        CausalRecord.DeliveryAttempt attempt,
                                        matrix.causal.PerceptReceipts.Presentation presented,
                                        CausalRecord.Fidelity fidelity) {
        CausalRecord.PerceptReceipt receipt = audit.receipt();
        check("projection", name + "-root-pair", audit.delivery() == attempt);
        check("projection", name + "-id", receipt.id().equals(presented.id()));
        check("projection", name + "-tick", receipt.tick() == attempt.tick()
                && receipt.tick() == receipt.id().tick());
        check("projection", name + "-subject", receipt.subject().equals(attempt.subject()));
        check("projection", name + "-channel", receipt.channel() == attempt.channel());
        check("projection", name + "-content",
                receipt.content().equals(attempt.presentedContent().orElseThrow()));
        check("projection", name + "-claimed-source",
                receipt.perceivedSource().equals(attempt.declaredSource()));
        check("projection", name + "-uncertainty",
                receipt.uncertaintyBasisPoints() == presented.uncertaintyBasisPoints());
        check("projection", name + "-presented-fidelity", receipt.fidelity() == fidelity);
    }

    private static void hiddenAuditTwins(Fixture fixture) {
        CausalRecord.Principal actualB = principal(CausalRecord.PrincipalKind.SYSTEM, "sensor-b");
        CausalRecord.TruthEntry truthB = truth(7, 9,
                principal(CausalRecord.PrincipalKind.HUMAN, "human-7"),
                new CausalRecord.Payload("hidden-other"), actualB);
        CausalRecord.DeliveryAttempt degradedB = attempt(truthB, fixture.subject,
                fixture.degraded.channel(), actualB, fixture.claimed,
                CausalRecord.Fidelity.PARTIAL, CausalRecord.DeliveryOutcome.DEGRADED,
                Optional.of(fixture.visible));
        matrix.causal.PerceptReceipts.Presentation presented =
                new matrix.causal.PerceptReceipts.Presentation(
                        new CausalId.Percept(7, 5), 777);
        CausalRecord.ReceiptAudit first = matrix.causal.PerceptReceipts.project(
                fixture.degraded, Optional.of(presented)).orElseThrow();
        CausalRecord.ReceiptAudit second = matrix.causal.PerceptReceipts.project(
                degradedB, Optional.of(presented)).orElseThrow();
        check("hidden", "root-audits-really-differ", !first.delivery().equals(second.delivery()));
        check("hidden", "actual-sources-differ",
                !first.delivery().actualSource().equals(second.delivery().actualSource()));
        check("hidden", "truths-differ", !first.delivery().truth().equals(second.delivery().truth()));
        check("hidden", "visible-values-equal", first.receipt().equals(second.receipt()));
        check("hidden", "visible-hashes-equal",
                first.receipt().hashCode() == second.receipt().hashCode());
        check("hidden", "visible-order-equal",
                first.receipt().compareTo(second.receipt()) == 0);

        matrix.causal.PerceptReceipts.Presentation otherUncertainty =
                new matrix.causal.PerceptReceipts.Presentation(presented.id(), 778);
        CausalRecord.PerceptReceipt changed = matrix.causal.PerceptReceipts.project(
                fixture.degraded, Optional.of(otherUncertainty)).orElseThrow().receipt();
        check("projection", "visible-uncertainty-is-causal",
                !changed.equals(first.receipt()));
    }

    private static void constructorAndApiContract(Fixture fixture) throws Exception {
        rejects("constructor", "presentation-null-id",
                () -> new matrix.causal.PerceptReceipts.Presentation(null, 0));
        rejects("constructor", "presentation-negative-uncertainty",
                () -> new matrix.causal.PerceptReceipts.Presentation(
                        new CausalId.Percept(7, 0), -1));
        rejects("constructor", "presentation-high-uncertainty",
                () -> new matrix.causal.PerceptReceipts.Presentation(
                        new CausalId.Percept(7, 0), 10_001));
        rejects("constructor", "receipt-none-fidelity",
                () -> new CausalRecord.PerceptReceipt(new CausalId.Percept(7, 0),
                        fixture.subject, CausalRecord.Channel.VISION, fixture.visible,
                        fixture.claimed, 0, CausalRecord.Fidelity.NONE));
        rejects("constructor", "receipt-null-fidelity",
                () -> new CausalRecord.PerceptReceipt(new CausalId.Percept(7, 0),
                        fixture.subject, CausalRecord.Channel.VISION, fixture.visible,
                        fixture.claimed, 0, null));
        rejects("silence", "receipt-constructor-refuses-unproven-no-signal",
                () -> new CausalRecord.PerceptReceipt(new CausalId.Percept(7, 0),
                        fixture.subject, CausalRecord.Channel.NO_SIGNAL, fixture.visible,
                        fixture.claimed, 0, CausalRecord.Fidelity.FULL));

        List<String> receiptFields = java.util.Arrays.stream(
                        CausalRecord.PerceptReceipt.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        check("roster", "receipt-exact-visible-components", receiptFields.equals(List.of(
                "id", "subject", "channel", "content", "perceivedSource",
                "uncertaintyBasisPoints", "fidelity")));
        for (String hidden : List.of("actual", "truth", "provenance", "audit", "delivery",
                "outcome", "rule", "authority", "consent", "disclosure", "constraint",
                "obligation", "reason")) {
            check("roster", "receipt-omits-" + hidden,
                    receiptFields.stream().noneMatch(field -> field.contains(hidden)));
        }
        check("roster", "presentation-exact-components",
                java.util.Arrays.stream(
                                matrix.causal.PerceptReceipts.Presentation.class
                                        .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList()
                        .equals(List.of("id", "uncertaintyBasisPoints")));
        check("roster", "mapper-one-public-door",
                java.util.Arrays.stream(matrix.causal.PerceptReceipts.class.getDeclaredMethods())
                        .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName).toList().equals(List.of("project")));
    }

    private static void sourceContract(Path root) {
        check("source", "complete-source-capability-roster", sourceHeld(root, true));
    }

    private static void sourceMutants(Path root) throws IOException {
        String source = Files.readString(root.resolve(
                Path.of("matrix", "causal", "PerceptReceipts.java")), StandardCharsets.UTF_8);
        mutant(root, source.replace("attempt.declaredSource()", "attempt.actualSource()"),
                "actual-source-leak");
        mutant(root, source.replace("Presentation visible = presentation.get();",
                "attempt.truth();\n        Presentation visible = presentation.get();"),
                "truth-read");
        mutant(root, source.replace("Presentation visible = presentation.get();",
                "PerceptReceipts.class.getDeclaredFields();\n"
                        + "        Presentation visible = presentation.get();"),
                "reflection-read");
        mutant(root, source.replace("private PerceptReceipts() {}",
                "private static native long leak();\n\n"
                        + "    private PerceptReceipts() { leak(); }"),
                "same-class-native");
        mutant(root, source.replace("    private PerceptReceipts() {}\n}",
                "    private PerceptReceipts() { PerceptReceiptsEscape.leak(); }\n}\n\n"
                        + "class PerceptReceiptsEscape { static native long leak(); }"),
                "prefix-owner-native");
        mutant(root, source.replace("Presentation visible = presentation.get();",
                "Optional.of(attempt).map(value -> value);\n"
                        + "        Presentation visible = presentation.get();"),
                "callback");
        mutant(root, source.replace(
                        "public static Optional<CausalRecord.ReceiptAudit> project(",
                        "public static Optional<Object> project("),
                "generic-return-door");
        mutant(root, source.replace("private PerceptReceipts() {}",
                        "static void helper() {}\n\n    private PerceptReceipts() {}"),
                "extra-package-door");
        mutant(root, source.replace("        }\n    }\n\n    /**\n     * Project one attempt",
                        "        }\n\n"
                                + "        public <T> T mutableCapabilityDoor() { return null; }\n"
                                + "    }\n\n    /**\n     * Project one attempt"),
                "presentation-generic-door");
        mutant(root, source.replace("package matrix.causal;",
                        "package matrix.causal;\n\n"
                                + "import static matrix.core.Config.HUNT_VERIFY;")
                        .replace("visible.uncertaintyBasisPoints(),",
                                "visible.uncertaintyBasisPoints()"
                                        + " + (HUNT_VERIFY ? 1 : 0),"),
                "static-import-field");
        mutant(root, source.replace("public final class PerceptReceipts {",
                "public final class PerceptReceipts { this is not Java"),
                "malformed");
    }

    private static void mutant(Path root, String source, String name) throws IOException {
        Path scratch = Files.createTempDirectory("percept-receipts-mutant-");
        copyTree(root, scratch);
        Files.writeString(scratch.resolve(Path.of("matrix", "causal", "PerceptReceipts.java")),
                source, StandardCharsets.UTF_8);
        check("source", name, !sourceHeld(scratch, false));
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var paths = Files.walk(from)) {
            for (Path path : paths.toList()) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static boolean sourceHeld(Path root, boolean explain) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return false;
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted().toList();
        } catch (IOException failure) {
            return false;
        }
        if (sources.isEmpty()) return false;

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Path classes = Files.createTempDirectory("percept-receipts-classes-");
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-classpath", classes.toString(),
                            "-d", classes.toString()), null, units);
            List<CompilationUnitTree> parsed = new ArrayList<>();
            task.parse().forEach(parsed::add);
            task.analyze();
            if (diagnostics.getDiagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
                return false;
            }
            return inspectAttributed(task, parsed, explain);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static boolean inspectAttributed(JavacTask task,
                                             List<CompilationUnitTree> units,
                                             boolean explain) {
        Trees trees = Trees.instance(task);
        Elements elements = task.getElements();
        Types types = task.getTypes();
        Set<String> expectedTypes = Set.of("matrix.causal.PerceptReceipts",
                "matrix.causal.PerceptReceipts.Presentation");
        Set<String> seenTypes = new HashSet<>();
        Set<String> violations = new LinkedHashSet<>();

        Set<String> allowedCalls = Set.of(
                "java.lang.Object#<init>()",
                "java.lang.Record#<init>()",
                "java.util.Objects#requireNonNull(java.lang.Object,java.lang.String)",
                "java.util.Optional#isPresent()",
                "java.util.Optional#isEmpty()",
                "java.util.Optional#get()",
                "java.util.Optional#empty()",
                "java.util.Optional#of(java.lang.Object)",
                "matrix.causal.CausalRecord.DeliveryAttempt#outcome()",
                "matrix.causal.CausalRecord.DeliveryAttempt#tick()",
                "matrix.causal.CausalRecord.DeliveryAttempt#subject()",
                "matrix.causal.CausalRecord.DeliveryAttempt#channel()",
                "matrix.causal.CausalRecord.DeliveryAttempt#presentedContent()",
                "matrix.causal.CausalRecord.DeliveryAttempt#declaredSource()",
                "matrix.causal.CausalRecord.DeliveryAttempt#fidelity()",
                "matrix.causal.CausalId.Percept#tick()",
                "matrix.causal.PerceptReceipts.Presentation#id()",
                "matrix.causal.PerceptReceipts.Presentation#uncertaintyBasisPoints()"
        );
        Set<String> allowedConstructors = Set.of(
                "java.lang.IllegalArgumentException#<init>(java.lang.String)",
                "matrix.causal.CausalRecord.PerceptReceipt#<init>(matrix.causal.CausalId.Percept,matrix.causal.CausalRecord.Subject,matrix.causal.CausalRecord.Channel,matrix.causal.CausalRecord.Payload,matrix.causal.CausalRecord.Principal,int,matrix.causal.CausalRecord.Fidelity)",
                "matrix.causal.CausalRecord.ReceiptAudit#<init>(matrix.causal.CausalRecord.PerceptReceipt,matrix.causal.CausalRecord.DeliveryAttempt)"
        );
        Set<String> allowedFields = Set.of(
                "matrix.causal.CausalRecord.DeliveryOutcome#OCCLUDED",
                "matrix.causal.CausalRecord.Channel#NO_SIGNAL"
        );

        for (CompilationUnitTree unit : units) {
            boolean mapperFile = unit.getSourceFile().getName()
                    .endsWith("/matrix/causal/PerceptReceipts.java");
            new TreePathScanner<Void, Void>() {
                private boolean owned() {
                    TreePath path = getCurrentPath();
                    while (path != null) {
                        Element element = trees.getElement(path);
                        if (element instanceof TypeElement type) {
                            return expectedTypes.contains(type.getQualifiedName().toString());
                        }
                        path = path.getParentPath();
                    }
                    return false;
                }

                @Override public Void visitClass(ClassTree node, Void unused) {
                    Element element = trees.getElement(getCurrentPath());
                    if (element instanceof TypeElement type) {
                        String name = type.getQualifiedName().toString();
                        if (mapperFile) {
                            seenTypes.add(name);
                            if (!expectedTypes.contains(name)) violations.add("type " + name);
                        }
                    }
                    return super.visitClass(node, unused);
                }

                @Override public Void visitMethod(MethodTree node, Void unused) {
                    if (owned()) {
                        Element element = trees.getElement(getCurrentPath());
                        if (!(element instanceof ExecutableElement executable)
                                || node.getBody() == null
                                || executable.getModifiers().contains(Modifier.NATIVE)) {
                            violations.add("bodyless executable");
                        }
                    }
                    return super.visitMethod(node, unused);
                }

                @Override public Void visitMethodInvocation(MethodInvocationTree node,
                                                            Void unused) {
                    if (owned()) {
                        Element element = trees.getElement(new TreePath(
                                getCurrentPath(), node.getMethodSelect()));
                        if (!(element instanceof ExecutableElement executable)) {
                            violations.add("unresolved call");
                        } else {
                            String key = key(executable, types);
                            String owner = ((TypeElement) executable.getEnclosingElement())
                                    .getQualifiedName().toString();
                            if (!expectedTypes.contains(owner) && !allowedCalls.contains(key)) {
                                violations.add("call " + key);
                            }
                        }
                    }
                    return super.visitMethodInvocation(node, unused);
                }

                @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                    if (owned()) recordField(trees.getElement(getCurrentPath()));
                    return super.visitMemberSelect(node, unused);
                }

                @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                    if (owned()) recordField(trees.getElement(getCurrentPath()));
                    return super.visitIdentifier(node, unused);
                }

                private void recordField(Element element) {
                    if (element == null || (!element.getKind().isField()
                            && element.getKind() != ElementKind.ENUM_CONSTANT)) return;
                    Element enclosing = element.getEnclosingElement();
                    if (!(enclosing instanceof TypeElement owner)) {
                        violations.add("field without type owner " + element);
                        return;
                    }
                    String key = owner.getQualifiedName() + "#" + element.getSimpleName();
                    if (!allowedFields.contains(key)) violations.add("field " + key);
                }

                @Override public Void visitNewClass(NewClassTree node, Void unused) {
                    if (owned()) {
                        Element element = trees.getElement(getCurrentPath());
                        if (!(element instanceof ExecutableElement executable)
                                || !allowedConstructors.contains(key(executable, types))) {
                            violations.add("construction " + element);
                        }
                    }
                    return super.visitNewClass(node, unused);
                }

                @Override public Void visitLambdaExpression(LambdaExpressionTree node,
                                                            Void unused) {
                    if (owned()) violations.add("lambda");
                    return super.visitLambdaExpression(node, unused);
                }

                @Override public Void visitMemberReference(MemberReferenceTree node,
                                                           Void unused) {
                    if (owned()) violations.add("member reference");
                    return super.visitMemberReference(node, unused);
                }
            }.scan(unit, null);
        }

        TypeElement mapper = elements.getTypeElement("matrix.causal.PerceptReceipts");
        if (mapper == null) return false;
        List<ExecutableElement> publicMethods = mapper.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(method -> method.getModifiers().contains(Modifier.PUBLIC))
                .toList();
        ExecutableElement door = publicMethods.size() == 1 ? publicMethods.get(0) : null;
        boolean exactDoor = door != null
                && key(door, types).equals(
                "matrix.causal.PerceptReceipts#project(matrix.causal.CausalRecord.DeliveryAttempt,java.util.Optional)")
                && door.getModifiers().equals(Set.of(Modifier.PUBLIC, Modifier.STATIC))
                && door.getTypeParameters().isEmpty()
                && door.getParameters().get(0).asType().toString().equals(
                        "matrix.causal.CausalRecord.DeliveryAttempt")
                && door.getParameters().get(1).asType().toString().equals(
                        "java.util.Optional<matrix.causal.PerceptReceipts.Presentation>")
                && door.getReturnType().toString().equals(
                        "java.util.Optional<matrix.causal.CausalRecord.ReceiptAudit>");
        List<ExecutableElement> mapperExecutables = mapper.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast).toList();
        boolean mapperDoors = mapperExecutables.size() == 2
                && mapperExecutables.stream().anyMatch(method -> method.equals(door))
                && mapperExecutables.stream().anyMatch(method ->
                        key(method, types).equals("matrix.causal.PerceptReceipts#<init>()")
                                && method.getModifiers().equals(Set.of(Modifier.PRIVATE)));
        boolean noFields = mapper.getEnclosedElements().stream()
                .noneMatch(element -> element.getKind().isField());
        TypeElement presentation = elements.getTypeElement(
                "matrix.causal.PerceptReceipts.Presentation");
        Set<String> presentationExecutables = presentation == null ? Set.of()
                : presentation.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .map(method -> key(method, types) + "->" + method.getReturnType()
                        + ":" + method.getModifiers()
                        + ":typeparams=" + method.getTypeParameters().size())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> expectedPresentationExecutables = Set.of(
                "matrix.causal.PerceptReceipts.Presentation#<init>(matrix.causal.CausalId.Percept,int)->void:[public]:typeparams=0",
                "matrix.causal.PerceptReceipts.Presentation#toString()->java.lang.String:[public, final]:typeparams=0",
                "matrix.causal.PerceptReceipts.Presentation#hashCode()->int:[public, final]:typeparams=0",
                "matrix.causal.PerceptReceipts.Presentation#equals(java.lang.Object)->boolean:[public, final]:typeparams=0",
                "matrix.causal.PerceptReceipts.Presentation#id()->matrix.causal.CausalId.Percept:[public]:typeparams=0",
                "matrix.causal.PerceptReceipts.Presentation#uncertaintyBasisPoints()->int:[public]:typeparams=0");
        boolean typeShape = mapper.getModifiers().containsAll(
                    Set.of(Modifier.PUBLIC, Modifier.FINAL))
                && mapper.getSuperclass().toString().equals("java.lang.Object")
                && mapper.getInterfaces().isEmpty()
                && presentation != null
                && presentation.getKind() == ElementKind.RECORD
                && presentation.getModifiers().containsAll(
                    Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
                && presentation.getEnclosingElement().equals(mapper)
                && presentation.getSuperclass().toString().equals("java.lang.Record")
                && presentation.getInterfaces().isEmpty()
                && presentationExecutables.equals(expectedPresentationExecutables)
                && presentation.getRecordComponents().stream()
                    .map(component -> component.getSimpleName() + ":" + component.asType())
                    .toList().equals(List.of(
                            "id:matrix.causal.CausalId.Percept",
                            "uncertaintyBasisPoints:int"))
                && presentation.getEnclosedElements().stream()
                    .filter(element -> element.getKind().isField())
                    .allMatch(field -> Set.of("id", "uncertaintyBasisPoints")
                            .contains(field.getSimpleName().toString())
                            && field.getModifiers().containsAll(
                                    Set.of(Modifier.PRIVATE, Modifier.FINAL)));
        boolean held = exactDoor && mapperDoors && noFields && typeShape
                && seenTypes.equals(expectedTypes)
                && violations.isEmpty();
        if (!held && explain) {
            System.out.println("SOURCE_DIAGNOSTIC door=" + exactDoor
                    + " mapper_doors=" + mapperDoors + " no_fields=" + noFields
                    + " type_shape=" + typeShape + " types=" + seenTypes
                    + " violations=" + violations);
        }
        return held;
    }

    private static String key(ExecutableElement executable, Types types) {
        TypeElement owner = (TypeElement) executable.getEnclosingElement();
        String name = executable.getKind() == ElementKind.CONSTRUCTOR
                ? "<init>" : executable.getSimpleName().toString();
        String parameters = executable.getParameters().stream()
                .map(parameter -> erased(parameter.asType(), types))
                .reduce((left, right) -> left + "," + right).orElse("");
        return owner.getQualifiedName() + "#" + name + "(" + parameters + ")";
    }

    private static String erased(TypeMirror type, Types types) {
        return types.erasure(type).toString().replace('$', '.');
    }

    private static CausalRecord.TruthEntry truth(long tick, int sequence,
                                                  CausalRecord.Principal subject,
                                                  CausalRecord.Payload value,
                                                  CausalRecord.Principal provenance) {
        return new CausalRecord.TruthEntry(tick, sequence, subject,
                new CausalRecord.Fact(new CausalRecord.Symbol("signal.value"), value),
                provenance);
    }

    private static CausalRecord.DeliveryAttempt attempt(
            CausalRecord.TruthEntry truth, CausalRecord.Subject subject,
            CausalRecord.Channel channel, CausalRecord.Principal actual,
            CausalRecord.Principal declared, CausalRecord.Fidelity fidelity,
            CausalRecord.DeliveryOutcome outcome, Optional<CausalRecord.Payload> content) {
        return new CausalRecord.DeliveryAttempt(truth.tick(), truth.sequence(), subject, channel,
                actual, declared, truth, fidelity, outcome, content,
                CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                switch (outcome) {
                    case DELIVERED -> CausalRecord.DisclosureClass.AUDIT_MATCHED;
                    case DEGRADED -> CausalRecord.DisclosureClass.AUDIT_DIVERGED;
                    case OCCLUDED -> CausalRecord.DisclosureClass.NOT_PRESENTED;
                }, CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
    }

    private static CausalRecord.Principal principal(CausalRecord.PrincipalKind kind,
                                                    String key) {
        return new CausalRecord.Principal(kind, key);
    }

    private static void rejects(String kind, String name, Throwing action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            rejected = true;
        } catch (Exception unexpected) {
            // A different checked exception is not the constructor refusal promised here.
        }
        check(kind, name, rejected);
    }

    private static void check(String kind, String name, boolean held) {
        cases++;
        BY_KIND.merge("cases:" + kind, 1, Integer::sum);
        if (!held) {
            failures++;
            BY_KIND.merge("failures:" + kind, 1, Integer::sum);
            System.out.println("FAIL " + kind + " " + name);
        }
    }

    private static int cases(String kind) {
        return BY_KIND.getOrDefault("cases:" + kind, 0);
    }

    private static int failures(String kind) {
        return BY_KIND.getOrDefault("failures:" + kind, 0);
    }

    @FunctionalInterface
    private interface Throwing { void run() throws Exception; }

    private PerceptReceipts() {}
}
