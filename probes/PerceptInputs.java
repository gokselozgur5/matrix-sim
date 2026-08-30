import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Probe: can the root allocate one canonical input without hidden audit leaks?
 *
 * <p>The runtime attacks visible ordering, equal-occurrence multiplicity,
 * post-allocation idempotence, subject scoping, canonical bytes, audit
 * preservation and hidden twins. The source half uses the JDK 17 attributed
 * tree API; it is added below rather than interpreting Java with regular
 * expressions or brace counts.
 */
public final class PerceptInputs {
    private static int cases;
    private static int failures;
    private static final Map<String, Integer> BY_KIND = new HashMap<>();

    private record Fixture(CausalRecord.Subject subject,
                           CausalRecord.Principal claimed,
                           CausalRecord.ReceiptAudit internal,
                           CausalRecord.ReceiptAudit vision,
                           CausalRecord.ReceiptAudit degradedA,
                           CausalRecord.ReceiptAudit degradedB) {}

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        Path root = Path.of("src");
        boolean suppliedRoot = false;
        if (args.length != 0) {
            if (args.length != 2 || !args[0].equals("--root")) {
                System.err.println("Usage: PerceptInputs [--root DIR]");
                System.exit(Probes.Outcome.REFUSED.code());
            }
            root = Path.of(args[1]);
            suppliedRoot = true;
        }
        Fixture fixture = fixture();
        allocationAndOrder(fixture);
        visibleMultiplicity(fixture);
        hiddenNoninterference(fixture);
        scopedIdempotence(fixture);
        constructorAndCanonical(fixture);
        sourceContract(root);
        if (!suppliedRoot) sourceMutants(root);

        System.out.printf("PERCEPT_INPUT_CENSUS cases=%d allocation=%d multiplicity=%d"
                        + " hidden=%d scoped=%d canonical=%d source=%d roster=%d%n",
                cases, cases("allocation"), cases("multiplicity"), cases("hidden"),
                cases("scoped"), cases("canonical"), cases("source"), cases("roster"));
        Probes.leave(String.format(
                        "VERDICT PERCEPT_INPUTS_%s cases=%d cases_none=%d"
                                + " allocation_fail=%d multiplicity_fail=%d"
                                + " hidden_leak=%d scoped_fail=%d canonical_fail=%d"
                                + " source_fail=%d roster_fail=%d",
                        failures == 0 && cases > 0 ? "HELD" : "BROKEN",
                        cases, cases == 0 ? 1 : 0, failures("allocation"),
                        failures("multiplicity"), failures("hidden"), failures("scoped"),
                        failures("canonical"), failures("source"), failures("roster")),
                failures == 0 && cases > 0);
    }

    private static Fixture fixture() {
        CausalRecord.Subject subject = new CausalRecord.Subject("human-7");
        CausalRecord.Principal human = principal(CausalRecord.PrincipalKind.HUMAN, "human-7");
        CausalRecord.Principal claimed = principal(
                CausalRecord.PrincipalKind.SYSTEM, "matrix-world");
        CausalRecord.Principal sensorA = principal(
                CausalRecord.PrincipalKind.SYSTEM, "sensor-a");
        CausalRecord.Principal sensorB = principal(
                CausalRecord.PrincipalKind.SYSTEM, "sensor-b");

        CausalRecord.ReceiptAudit internal = audit(subject, human, claimed, claimed,
                12, 0, 3, CausalRecord.Channel.INTERNAL,
                new CausalRecord.Payload("awake"), new CausalRecord.Payload("awake"), 100,
                CausalRecord.Fidelity.FULL, CausalRecord.DeliveryOutcome.DELIVERED);
        CausalRecord.ReceiptAudit vision = audit(subject, human, claimed, claimed,
                12, 1, 91, CausalRecord.Channel.VISION,
                new CausalRecord.Payload("12,34"), new CausalRecord.Payload("12,34"), 200,
                CausalRecord.Fidelity.FULL, CausalRecord.DeliveryOutcome.DELIVERED);
        CausalRecord.Payload visible = new CausalRecord.Payload("same-visible-warning");
        CausalRecord.ReceiptAudit degradedA = audit(subject, human, sensorA, claimed,
                12, 2, 800, CausalRecord.Channel.VISION,
                new CausalRecord.Payload("hidden-a"), visible, 777,
                CausalRecord.Fidelity.PARTIAL, CausalRecord.DeliveryOutcome.DEGRADED);
        CausalRecord.ReceiptAudit degradedB = audit(subject, human, sensorB, claimed,
                12, 9, 4, CausalRecord.Channel.VISION,
                new CausalRecord.Payload("hidden-b"), visible, 777,
                CausalRecord.Fidelity.PARTIAL, CausalRecord.DeliveryOutcome.DEGRADED);
        return new Fixture(subject, claimed, internal, vision, degradedA, degradedB);
    }

    private static void allocationAndOrder(Fixture fixture) {
        matrix.causal.PerceptInputs.Allocation allocation =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject,
                        List.of(fixture.vision, fixture.internal));
        matrix.causal.PerceptInputs.MindInput input = allocation.input();
        check("allocation", "declared-tick", input.tick() == 12);
        check("allocation", "declared-subject", input.subject().equals(fixture.subject));
        check("allocation", "complete-size", input.receipts().size() == 2);
        check("allocation", "visible-order-not-provisional-id",
                input.receipts().get(0).channel() == CausalRecord.Channel.VISION
                        && input.receipts().get(1).channel() == CausalRecord.Channel.INTERNAL);
        check("allocation", "dense-zero", input.receipts().get(0).id().equals(
                new CausalId.Percept(12, 0)));
        check("allocation", "dense-one", input.receipts().get(1).id().equals(
                new CausalId.Percept(12, 1)));
        check("allocation", "all-audits-retained", allocation.audits().size() == 2);
        check("allocation", "audits-rebound-to-final-identities",
                allocation.audits().stream().allMatch(audit ->
                        input.receipts().contains(audit.receipt())));

        matrix.causal.PerceptInputs.MindInput reversed =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject,
                        List.of(fixture.internal, fixture.vision)).input();
        check("allocation", "input-permutation-neutral", input.equals(reversed));
        check("canonical", "input-permutation-byte-neutral",
                bytes(input).equals(bytes(reversed)));
    }

    private static void visibleMultiplicity(Fixture fixture) {
        matrix.causal.PerceptInputs.Allocation allocation =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject,
                        List.of(fixture.degradedA, fixture.degradedB));
        List<CausalRecord.PerceptReceipt> receipts = allocation.input().receipts();
        check("multiplicity", "equal-occurrences-both-retained", receipts.size() == 2);
        check("multiplicity", "equal-values-get-distinct-ids",
                !receipts.get(0).id().equals(receipts.get(1).id())
                        && visibleEquals(receipts.get(0), receipts.get(1)));
        check("multiplicity", "every-hidden-attempt-retained",
                allocation.audits().size() == 2
                        && !allocation.audits().get(0).delivery().equals(
                        allocation.audits().get(1).delivery()));
        check("multiplicity", "each-occurrence-citable",
                allocation.audits().get(0).receipt().ref().compareTo(
                        allocation.audits().get(1).receipt().ref()) != 0);
    }

    private static void hiddenNoninterference(Fixture fixture) {
        matrix.causal.PerceptInputs.Allocation first =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject,
                        List.of(fixture.degradedA, fixture.degradedB));
        matrix.causal.PerceptInputs.Allocation second =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject,
                        List.of(fixture.degradedB, fixture.degradedA));
        check("hidden", "hidden-twins-really-differ",
                !first.audits().equals(second.audits()));
        check("hidden", "mind-input-value-identical", first.input().equals(second.input()));
        check("hidden", "mind-input-hash-identical",
                first.input().hashCode() == second.input().hashCode());
        check("hidden", "mind-input-bytes-identical",
                bytes(first.input()).equals(bytes(second.input())));
        check("hidden", "scoped-bases-identical",
                first.input().receipts().stream().map(CausalRecord.PerceptReceipt::ref).toList()
                        .equals(second.input().receipts().stream()
                                .map(CausalRecord.PerceptReceipt::ref).toList()));
        check("hidden", "root-audits-stay-root-distinct",
                first.audits().stream().map(CausalRecord.ReceiptAudit::delivery).toList()
                        .containsAll(List.of(fixture.degradedA.delivery(),
                                fixture.degradedB.delivery())));
    }

    private static void scopedIdempotence(Fixture fixture) {
        matrix.causal.PerceptInputs.MindInput allocated =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject,
                        List.of(fixture.internal, fixture.vision)).input();
        CausalRecord.PerceptReceipt zero = allocated.receipts().get(0);
        CausalRecord.PerceptReceipt one = allocated.receipts().get(1);
        matrix.causal.PerceptInputs.MindInput retried =
                new matrix.causal.PerceptInputs.MindInput(12, fixture.subject,
                        List.of(one, zero, one));
        check("scoped", "exact-retransmission-once", retried.equals(allocated));
        check("scoped", "retransmission-canonical-once",
                bytes(retried).equals(bytes(allocated)));

        CausalRecord.PerceptReceipt conflict = new CausalRecord.PerceptReceipt(
                one.id(), one.subject(), one.channel(),
                new CausalRecord.Payload("conflict"), one.perceivedSource(),
                one.uncertaintyBasisPoints(), one.fidelity());
        check("scoped", "conflict-order-is-not-equality",
                one.compareTo(conflict) != 0 && !one.equals(conflict));
        java.util.TreeSet<CausalRecord.PerceptReceipt> conflictSet =
                new java.util.TreeSet<>(List.of(one, conflict));
        check("scoped", "sorted-set-cannot-hide-conflict", conflictSet.size() == 2);
        rejects("scoped", "same-scoped-id-conflict",
                () -> new matrix.causal.PerceptInputs.MindInput(
                        12, fixture.subject, List.of(zero, one, conflict)));
        rejects("scoped", "dense-gap-refused",
                () -> new matrix.causal.PerceptInputs.MindInput(
                        12, fixture.subject, List.of(one)));

        CausalRecord.Subject other = new CausalRecord.Subject("human-8");
        CausalRecord.PerceptRef otherRef = new CausalRecord.PerceptRef(other, zero.id());
        check("scoped", "same-local-id-different-subject",
                !zero.ref().equals(otherRef) && zero.ref().compareTo(otherRef) != 0);
        CausalRecord.PerceptReceipt otherReceipt = new CausalRecord.PerceptReceipt(
                zero.id(), other, zero.channel(), zero.content(), zero.perceivedSource(),
                zero.uncertaintyBasisPoints(), zero.fidelity());
        check("scoped", "receipt-order-is-subject-scoped",
                zero.compareTo(otherReceipt) != 0);
        rejects("scoped", "intent-refuses-other-subject-basis",
                () -> new CausalRecord.IntentProposal(
                        new CausalId.Intent(12, 0), new CausalId.Choice(12, 0),
                        fixture.subject, new CausalRecord.Symbol("goal"),
                        new CausalRecord.Symbol("act"), fixture.claimed,
                        List.of(otherRef), List.of()));
    }

    private static void constructorAndCanonical(Fixture fixture) {
        matrix.causal.PerceptInputs.Allocation empty =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject, List.of());
        check("canonical", "empty-is-explicit", empty.input().receipts().isEmpty()
                && empty.audits().isEmpty());
        check("canonical", "empty-canonical-nonempty", !empty.input().canonical().isEmpty());

        rejects("allocation", "negative-tick",
                () -> matrix.causal.PerceptInputs.allocate(-1, fixture.subject, List.of()));
        rejects("allocation", "null-subject",
                () -> matrix.causal.PerceptInputs.allocate(12, null, List.of()));
        rejects("allocation", "null-audits",
                () -> matrix.causal.PerceptInputs.allocate(12, fixture.subject, null));
        rejects("allocation", "mixed-tick",
                () -> matrix.causal.PerceptInputs.allocate(13, fixture.subject,
                        List.of(fixture.internal)));
        rejects("allocation", "mixed-subject",
                () -> matrix.causal.PerceptInputs.allocate(12,
                        new CausalRecord.Subject("human-8"), List.of(fixture.internal)));

        ArrayList<CausalRecord.ReceiptAudit> mutable = new ArrayList<>();
        mutable.add(fixture.internal);
        matrix.causal.PerceptInputs.Allocation copied =
                matrix.causal.PerceptInputs.allocate(12, fixture.subject, mutable);
        mutable.add(fixture.vision);
        check("canonical", "caller-audits-defensive-copy", copied.audits().size() == 1);
        rejectsMutation("canonical", "receipts-unmodifiable",
                () -> copied.input().receipts().add(copied.input().receipts().get(0)));
        rejectsMutation("canonical", "audits-unmodifiable",
                () -> copied.audits().add(fixture.vision));
        rejects("canonical", "allocation-needs-receipt-coverage",
                () -> new matrix.causal.PerceptInputs.Allocation(copied.input(), List.of()));

        CausalRecord.PerceptReceipt forgedSecond = new CausalRecord.PerceptReceipt(
                new CausalId.Percept(12, 1), copied.input().receipts().get(0).subject(),
                copied.input().receipts().get(0).channel(),
                copied.input().receipts().get(0).content(),
                copied.input().receipts().get(0).perceivedSource(),
                copied.input().receipts().get(0).uncertaintyBasisPoints(),
                copied.input().receipts().get(0).fidelity());
        matrix.causal.PerceptInputs.MindInput forgedInput =
                new matrix.causal.PerceptInputs.MindInput(12, fixture.subject,
                        List.of(copied.input().receipts().get(0), forgedSecond));
        rejects("canonical", "one-attempt-cannot-mint-two-percepts",
                () -> new matrix.causal.PerceptInputs.Allocation(forgedInput,
                        List.of(copied.audits().get(0), new CausalRecord.ReceiptAudit(
                                forgedSecond, copied.audits().get(0).delivery()))));

        CausalRecord.ReceiptAudit unicodeAudit = audit(fixture.subject,
                principal(CausalRecord.PrincipalKind.HUMAN, "human-7"),
                fixture.claimed, fixture.claimed, 12, 20, 20,
                CausalRecord.Channel.TEXT, new CausalRecord.Payload("é"),
                new CausalRecord.Payload("é"), 0, CausalRecord.Fidelity.FULL,
                CausalRecord.DeliveryOutcome.DELIVERED);
        String unicodeCanonical = matrix.causal.PerceptInputs.allocate(
                12, fixture.subject, List.of(unicodeAudit)).input().canonical();
        check("canonical", "utf8-byte-length-prefix", unicodeCanonical.contains("2:é;"));
        rejects("canonical", "lone-surrogate-cannot-collide-with-question-mark",
                () -> new CausalRecord.Payload("\uD800"));
        CausalRecord.Payload supplementary = new CausalRecord.Payload("😀");
        check("canonical", "supplementary-utf8-remains-four-bytes",
                supplementary.text().getBytes(StandardCharsets.UTF_8).length == 4);

        check("roster", "mind-input-exact-components",
                java.util.Arrays.stream(matrix.causal.PerceptInputs.MindInput.class
                                .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList()
                        .equals(List.of("tick", "subject", "receipts")));
        check("roster", "allocation-exact-components",
                java.util.Arrays.stream(matrix.causal.PerceptInputs.Allocation.class
                                .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList()
                        .equals(List.of("input", "audits")));
        check("roster", "percept-ref-exact-components",
                java.util.Arrays.stream(CausalRecord.PerceptRef.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList()
                        .equals(List.of("subject", "id")));
        check("roster", "percept-ref-exact-constructor",
                java.util.Arrays.stream(CausalRecord.PerceptRef.class.getDeclaredConstructors())
                        .map(java.lang.reflect.Constructor::toGenericString).toList().equals(List.of(
                                "public matrix.causal.CausalRecord$PerceptRef(matrix.causal.CausalRecord$Subject,matrix.causal.CausalId$Percept)")));
        check("roster", "percept-ref-closed-method-surface",
                java.util.Arrays.stream(CausalRecord.PerceptRef.class.getDeclaredMethods())
                        .map(PerceptInputs::methodShape).sorted().toList().equals(List.of(
                                "compareTo(java.lang.Object)->int:public volatile",
                                "compareTo(matrix.causal.CausalRecord$PerceptRef)->int:public",
                                "equals(java.lang.Object)->boolean:public final",
                                "hashCode()->int:public final",
                                "id()->matrix.causal.CausalId$Percept:public",
                                "subject()->matrix.causal.CausalRecord$Subject:public",
                                "toString()->java.lang.String:public final")));
        check("roster", "intent-basis-is-scoped",
                java.util.Arrays.stream(CausalRecord.IntentProposal.class.getRecordComponents())
                        .filter(component -> component.getName().equals("receiptBasis"))
                        .map(component -> component.getGenericType().getTypeName()).toList()
                        .equals(List.of(
                                "java.util.List<matrix.causal.CausalRecord$PerceptRef>")));
        check("roster", "percept-ref-exact-fields",
                java.util.Arrays.stream(CausalRecord.PerceptRef.class.getDeclaredFields())
                        .map(field -> field.getName() + ":" + field.getType().getName()
                                + ":" + java.lang.reflect.Modifier.toString(
                                field.getModifiers()))
                        .sorted().toList().equals(List.of(
                                "id:matrix.causal.CausalId$Percept:private final",
                                "subject:matrix.causal.CausalRecord$Subject:private final")));
    }

    private static String methodShape(java.lang.reflect.Method method) {
        String parameters = java.util.Arrays.stream(method.getParameterTypes())
                .map(Class::getName).reduce((left, right) -> left + "," + right).orElse("");
        return method.getName() + "(" + parameters + ")->" + method.getReturnType().getName()
                + ":" + java.lang.reflect.Modifier.toString(method.getModifiers());
    }

    private static void sourceContract(Path root) {
        check("source", "attributed-visible-only-allocation", sourceHeld(root, true));
    }

    private static void sourceMutants(Path root) throws IOException {
        String source = Files.readString(root.resolve(
                Path.of("matrix", "causal", "PerceptInputs.java")), StandardCharsets.UTF_8);
        mutant(root, source.replace(
                        "int order = Integer.compare(channelRank(left.channel()),"
                                + " channelRank(right.channel()));",
                        "int order = left.id().compareTo(right.id());"),
                "provisional-id-order");
        mutant(root, source.replace("new CausalId.Percept(tick, sequence),",
                        "new CausalId.Percept(tick, provisionalAudit.delivery().sequence()),"),
                "hidden-delivery-sequence");
        mutant(root, source.replace("package matrix.causal;",
                        "package matrix.causal;\n\n"
                                + "import static matrix.core.Config.HUNT_VERIFY;")
                        .replace("new CausalId.Percept(tick, sequence),",
                                "new CausalId.Percept(tick, sequence"
                                        + " + (HUNT_VERIFY ? 1 : 0)),"),
                "static-import-state");
        mutant(root, source.replace("ArrayList<CausalRecord.ReceiptAudit> ordered =",
                        "provisional.forEach(value -> value.delivery());\n"
                                + "        ArrayList<CausalRecord.ReceiptAudit> ordered ="),
                "callback-hidden-read");
        mutant(root, source.replace("public String canonical() {",
                        "public <T> T hiddenDoor() { return null; }\n\n"
                                + "        public String canonical() {"),
                "mind-input-generic-door");
        mutant(root, source.replace(
                        "if (previous.tick() == audit.delivery().tick()\n"
                                + "                            && previous.sequence()"
                                + " == audit.delivery().sequence()) {",
                        "if (false) {"),
                "duplicate-root-attempt");
        mutant(root, source.replace("public final class PerceptInputs {",
                        "public final class PerceptInputs { this is not Java"),
                "malformed");
    }

    private static void mutant(Path root, String source, String name) throws IOException {
        Path scratch = Files.createTempDirectory("percept-inputs-mutant-");
        copyTree(root, scratch);
        Files.writeString(scratch.resolve(Path.of("matrix", "causal", "PerceptInputs.java")),
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
            Path classes = Files.createTempDirectory("percept-inputs-classes-");
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
        Set<String> expectedTypes = Set.of(
                "matrix.causal.PerceptInputs",
                "matrix.causal.PerceptInputs.MindInput",
                "matrix.causal.PerceptInputs.Allocation");
        Set<String> seenTypes = new HashSet<>();
        Set<String> violations = new LinkedHashSet<>();
        Set<String> compareReads = new LinkedHashSet<>();
        Set<String> canonicalReads = new LinkedHashSet<>();
        Set<String> allocationAuditReads = new LinkedHashSet<>();

        Set<String> allowedCalls = Set.of(
                "matrix.causal.CausalId#canonical()",
                "java.lang.Enum#compareTo(java.lang.Enum)",
                "java.lang.Enum#name()",
                "java.lang.Integer#compare(int,int)",
                "java.lang.Object#<init>()",
                "java.lang.Record#<init>()",
                "java.lang.String#compareTo(java.lang.String)",
                "java.lang.String#equals(java.lang.Object)",
                "java.lang.String#length()",
                "java.lang.String#getBytes(java.nio.charset.Charset)",
                "java.lang.StringBuilder#append(char)",
                "java.lang.StringBuilder#append(int)",
                "java.lang.StringBuilder#append(java.lang.String)",
                "java.lang.StringBuilder#append(long)",
                "java.lang.StringBuilder#toString()",
                "java.util.ArrayList#add(int,java.lang.Object)",
                "java.util.ArrayList#add(java.lang.Object)",
                "java.util.ArrayList#get(int)",
                "java.util.ArrayList#isEmpty()",
                "java.util.ArrayList#size()",
                "java.util.List#copyOf(java.util.Collection)",
                "java.util.List#get(int)",
                "java.util.List#size()",
                "java.util.Objects#requireNonNull(java.lang.Object,java.lang.String)",
                "matrix.causal.CausalId.Percept#canonical()",
                "matrix.causal.CausalId.Percept#compareTo(matrix.causal.CausalId.Percept)",
                "matrix.causal.CausalId.Percept#equals(java.lang.Object)",
                "matrix.causal.CausalId.Percept#sequence()",
                "matrix.causal.CausalRecord.Payload#text()",
                "matrix.causal.CausalRecord.DeliveryAttempt#sequence()",
                "matrix.causal.CausalRecord.DeliveryAttempt#tick()",
                "matrix.causal.CausalRecord.PerceptReceipt#channel()",
                "matrix.causal.CausalRecord.PerceptReceipt#content()",
                "matrix.causal.CausalRecord.PerceptReceipt#equals(java.lang.Object)",
                "matrix.causal.CausalRecord.PerceptReceipt#fidelity()",
                "matrix.causal.CausalRecord.PerceptReceipt#id()",
                "matrix.causal.CausalRecord.PerceptReceipt#perceivedSource()",
                "matrix.causal.CausalRecord.PerceptReceipt#subject()",
                "matrix.causal.CausalRecord.PerceptReceipt#tick()",
                "matrix.causal.CausalRecord.PerceptReceipt#uncertaintyBasisPoints()",
                "matrix.causal.CausalRecord.Principal#compareTo(matrix.causal.CausalRecord.Principal)",
                "matrix.causal.CausalRecord.Principal#key()",
                "matrix.causal.CausalRecord.Principal#kind()",
                "matrix.causal.CausalRecord.ReceiptAudit#delivery()",
                "matrix.causal.CausalRecord.ReceiptAudit#receipt()",
                "matrix.causal.CausalRecord.Subject#equals(java.lang.Object)",
                "matrix.causal.CausalRecord.Subject#key()",
                "matrix.causal.CausalRecord.Symbol#compareTo(matrix.causal.CausalRecord.Symbol)",
                "matrix.causal.CausalRecord.Symbol#value()",
                "matrix.causal.PerceptInputs.MindInput#receipts()",
                "matrix.causal.PerceptInputs.MindInput#subject()",
                "matrix.causal.PerceptInputs.MindInput#tick()"
        );
        Set<String> allowedFields = Set.of(
                "Array#length",
                "java.nio.charset.StandardCharsets#UTF_8",
                "matrix.causal.CausalRecord.Channel#VISION",
                "matrix.causal.CausalRecord.Channel#AUDIO",
                "matrix.causal.CausalRecord.Channel#TEXT",
                "matrix.causal.CausalRecord.Channel#HAPTIC",
                "matrix.causal.CausalRecord.Channel#INTERNAL",
                "matrix.causal.CausalRecord.Channel#NO_SIGNAL",
                "matrix.causal.CausalRecord.PrincipalKind#HUMAN",
                "matrix.causal.CausalRecord.PrincipalKind#MACHINE",
                "matrix.causal.CausalRecord.PrincipalKind#SYSTEM",
                "matrix.causal.CausalRecord.PrincipalKind#INSTITUTION",
                "matrix.causal.CausalRecord.PrincipalKind#PLACE",
                "matrix.causal.CausalRecord.PrincipalKind#UNKNOWN",
                "matrix.causal.CausalRecord.Fidelity#FULL",
                "matrix.causal.CausalRecord.Fidelity#PARTIAL",
                "matrix.causal.CausalRecord.Fidelity#NONE");
        Set<String> allowedConstructors = Set.of(
                "java.lang.IllegalArgumentException#<init>(java.lang.String)",
                "java.lang.StringBuilder#<init>(java.lang.String)",
                "java.util.ArrayList#<init>(int)",
                "matrix.causal.CausalId.Percept#<init>(long,int)",
                "matrix.causal.CausalRecord.PerceptReceipt#<init>(matrix.causal.CausalId.Percept,matrix.causal.CausalRecord.Subject,matrix.causal.CausalRecord.Channel,matrix.causal.CausalRecord.Payload,matrix.causal.CausalRecord.Principal,int,matrix.causal.CausalRecord.Fidelity)",
                "matrix.causal.CausalRecord.ReceiptAudit#<init>(matrix.causal.CausalRecord.PerceptReceipt,matrix.causal.CausalRecord.DeliveryAttempt)",
                "matrix.causal.PerceptInputs.Allocation#<init>(matrix.causal.PerceptInputs.MindInput,java.util.List)",
                "matrix.causal.PerceptInputs.MindInput#<init>(long,matrix.causal.CausalRecord.Subject,java.util.List)"
        );

        for (CompilationUnitTree unit : units) {
            boolean mapperFile = unit.getSourceFile().getName()
                    .endsWith("/matrix/causal/PerceptInputs.java");
            new TreePathScanner<Void, Void>() {
                private int ownedDepth;
                private ExecutableElement currentMethod;

                @Override public Void visitClass(ClassTree node, Void unused) {
                    Element element = trees.getElement(getCurrentPath());
                    boolean enters = element instanceof TypeElement type
                            && expectedTypes.contains(type.getQualifiedName().toString());
                    if (mapperFile && element instanceof TypeElement type) {
                        String name = type.getQualifiedName().toString();
                        seenTypes.add(name);
                        if (!expectedTypes.contains(name)) violations.add("type " + name);
                    }
                    if (enters) ownedDepth++;
                    super.visitClass(node, unused);
                    if (enters) ownedDepth--;
                    return null;
                }

                @Override public Void visitMethod(MethodTree node, Void unused) {
                    ExecutableElement previous = currentMethod;
                    Element element = trees.getElement(getCurrentPath());
                    if (ownedDepth > 0) {
                        if (!(element instanceof ExecutableElement executable)
                                || node.getBody() == null
                                || executable.getModifiers().contains(Modifier.NATIVE)) {
                            violations.add("bodyless executable");
                        } else {
                            currentMethod = executable;
                        }
                    }
                    super.visitMethod(node, unused);
                    currentMethod = previous;
                    return null;
                }

                @Override public Void visitMethodInvocation(MethodInvocationTree node,
                                                            Void unused) {
                    if (ownedDepth > 0) {
                        Element element = trees.getElement(new TreePath(
                                getCurrentPath(), node.getMethodSelect()));
                        if (!(element instanceof ExecutableElement executable)) {
                            violations.add("unresolved call");
                        } else {
                            String call = key(executable, types);
                            String method = currentMethod == null ? "" : currentMethod
                                    .getSimpleName().toString();
                            if (!expectedTypes.contains(owner(executable))
                                    && !allowedCalls.contains(call)) {
                                violations.add("call " + call);
                            }
                            if (method.equals("allocate")
                                    && (owner(executable).equals(
                                    "matrix.causal.CausalRecord.DeliveryAttempt")
                                    || call.equals(
                                    "matrix.causal.CausalRecord.PerceptReceipt#id()"))) {
                                violations.add("hidden allocation input " + call);
                            }
                            if (call.equals(
                                    "matrix.causal.CausalRecord.ReceiptAudit#delivery()")
                                    && !isReceiptAuditArgument(getCurrentPath(), trees, types)
                                    && (currentMethod == null || !owner(currentMethod).equals(
                                    "matrix.causal.PerceptInputs.Allocation"))) {
                                violations.add("delivery escapes audit reconstruction");
                            }
                            if (method.equals("visibleCompare")
                                    && owner(executable).equals(
                                    "matrix.causal.CausalRecord.PerceptReceipt")) {
                                compareReads.add(call);
                            }
                            if (method.equals("canonical")
                                    && owner(executable).equals(
                                    "matrix.causal.CausalRecord.PerceptReceipt")) {
                                canonicalReads.add(call);
                            }
                            if (currentMethod != null
                                    && currentMethod.getKind() == ElementKind.CONSTRUCTOR
                                    && owner(currentMethod).equals(
                                    "matrix.causal.PerceptInputs.Allocation")) {
                                allocationAuditReads.add(call);
                            }
                        }
                    }
                    return super.visitMethodInvocation(node, unused);
                }

                @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                    if (ownedDepth > 0) recordField(trees.getElement(getCurrentPath()));
                    return super.visitMemberSelect(node, unused);
                }

                @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                    if (ownedDepth > 0) recordField(trees.getElement(getCurrentPath()));
                    return super.visitIdentifier(node, unused);
                }

                private void recordField(Element element) {
                    if (element == null || (!element.getKind().isField()
                            && element.getKind() != ElementKind.ENUM_CONSTANT)) return;
                    if (element.getEnclosingElement() instanceof TypeElement type) {
                        String field = type.getQualifiedName() + "#" + element.getSimpleName();
                        if (!expectedTypes.contains(type.getQualifiedName().toString())
                                && !allowedFields.contains(field)) {
                            violations.add("external field " + field);
                        }
                    }
                }

                @Override public Void visitNewClass(NewClassTree node, Void unused) {
                    if (ownedDepth > 0) {
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
                    if (ownedDepth > 0) violations.add("lambda");
                    return super.visitLambdaExpression(node, unused);
                }

                @Override public Void visitMemberReference(MemberReferenceTree node,
                                                           Void unused) {
                    if (ownedDepth > 0) violations.add("member reference");
                    return super.visitMemberReference(node, unused);
                }
            }.scan(unit, null);
        }

        Set<String> expectedCompareReads = Set.of(
                "matrix.causal.CausalRecord.PerceptReceipt#channel()",
                "matrix.causal.CausalRecord.PerceptReceipt#content()",
                "matrix.causal.CausalRecord.PerceptReceipt#perceivedSource()",
                "matrix.causal.CausalRecord.PerceptReceipt#uncertaintyBasisPoints()",
                "matrix.causal.CausalRecord.PerceptReceipt#fidelity()");
        Set<String> expectedCanonicalReads = Set.of(
                "matrix.causal.CausalRecord.PerceptReceipt#id()",
                "matrix.causal.CausalRecord.PerceptReceipt#channel()",
                "matrix.causal.CausalRecord.PerceptReceipt#content()",
                "matrix.causal.CausalRecord.PerceptReceipt#perceivedSource()",
                "matrix.causal.CausalRecord.PerceptReceipt#uncertaintyBasisPoints()",
                "matrix.causal.CausalRecord.PerceptReceipt#fidelity()");
        TypeElement mapper = elements.getTypeElement("matrix.causal.PerceptInputs");
        TypeElement mindInput = elements.getTypeElement("matrix.causal.PerceptInputs.MindInput");
        TypeElement allocation = elements.getTypeElement("matrix.causal.PerceptInputs.Allocation");
        boolean typeShape = mapper != null && mindInput != null && allocation != null
                && mapper.getModifiers().containsAll(Set.of(Modifier.PUBLIC, Modifier.FINAL))
                && mapper.getSuperclass().toString().equals("java.lang.Object")
                && mapper.getInterfaces().isEmpty()
                && mapper.getEnclosedElements().stream()
                .noneMatch(element -> element.getKind().isField())
                && mindInput.getKind() == ElementKind.RECORD
                && allocation.getKind() == ElementKind.RECORD
                && mindInput.getRecordComponents().stream()
                .map(component -> component.getSimpleName() + ":" + component.asType())
                .toList().equals(List.of("tick:long",
                        "subject:matrix.causal.CausalRecord.Subject",
                        "receipts:java.util.List<matrix.causal.CausalRecord.PerceptReceipt>"))
                && allocation.getRecordComponents().stream()
                .map(component -> component.getSimpleName() + ":" + component.asType())
                .toList().equals(List.of("input:matrix.causal.PerceptInputs.MindInput",
                        "audits:java.util.List<matrix.causal.CausalRecord.ReceiptAudit>"));
        List<ExecutableElement> publicMapperMethods = mapper == null ? List.of()
                : mapper.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(method -> method.getModifiers().contains(Modifier.PUBLIC)).toList();
        boolean exactDoor = publicMapperMethods.size() == 1
                && key(publicMapperMethods.get(0), types).equals(
                "matrix.causal.PerceptInputs#allocate(long,matrix.causal.CausalRecord.Subject,java.util.List)")
                && publicMapperMethods.get(0).getModifiers().equals(
                Set.of(Modifier.PUBLIC, Modifier.STATIC))
                && publicMapperMethods.get(0).getTypeParameters().isEmpty()
                && publicMapperMethods.get(0).getParameters().get(2).asType().toString().equals(
                "java.util.List<matrix.causal.CausalRecord.ReceiptAudit>")
                && publicMapperMethods.get(0).getReturnType().toString().equals(
                "matrix.causal.PerceptInputs.Allocation");
        boolean exactMindPublic = exactPublicMethods(mindInput, types, Set.of(
                "matrix.causal.PerceptInputs.MindInput#canonical()",
                "matrix.causal.PerceptInputs.MindInput#toString()",
                "matrix.causal.PerceptInputs.MindInput#hashCode()",
                "matrix.causal.PerceptInputs.MindInput#equals(java.lang.Object)",
                "matrix.causal.PerceptInputs.MindInput#tick()",
                "matrix.causal.PerceptInputs.MindInput#subject()",
                "matrix.causal.PerceptInputs.MindInput#receipts()"));
        boolean exactAllocationPublic = exactPublicMethods(allocation, types, Set.of(
                "matrix.causal.PerceptInputs.Allocation#toString()",
                "matrix.causal.PerceptInputs.Allocation#hashCode()",
                "matrix.causal.PerceptInputs.Allocation#equals(java.lang.Object)",
                "matrix.causal.PerceptInputs.Allocation#input()",
                "matrix.causal.PerceptInputs.Allocation#audits()"));
        boolean exactExecutables = exactExecutables(mapper, types, Set.of(
                "matrix.causal.PerceptInputs#<init>()",
                "matrix.causal.PerceptInputs#allocate(long,matrix.causal.CausalRecord.Subject,java.util.List)",
                "matrix.causal.PerceptInputs#visibleCompare(matrix.causal.CausalRecord.PerceptReceipt,matrix.causal.CausalRecord.PerceptReceipt)",
                "matrix.causal.PerceptInputs#channelRank(matrix.causal.CausalRecord.Channel)",
                "matrix.causal.PerceptInputs#principalKindRank(matrix.causal.CausalRecord.PrincipalKind)",
                "matrix.causal.PerceptInputs#fidelityRank(matrix.causal.CausalRecord.Fidelity)",
                "matrix.causal.PerceptInputs#normalize(long,matrix.causal.CausalRecord.Subject,java.util.List)",
                "matrix.causal.PerceptInputs#word(java.lang.StringBuilder,java.lang.String)",
                "matrix.causal.PerceptInputs#number(java.lang.StringBuilder,long)"))
                && exactExecutables(mindInput, types, Set.of(
                "matrix.causal.PerceptInputs.MindInput#<init>(long,matrix.causal.CausalRecord.Subject,java.util.List)",
                "matrix.causal.PerceptInputs.MindInput#canonical()",
                "matrix.causal.PerceptInputs.MindInput#toString()",
                "matrix.causal.PerceptInputs.MindInput#hashCode()",
                "matrix.causal.PerceptInputs.MindInput#equals(java.lang.Object)",
                "matrix.causal.PerceptInputs.MindInput#tick()",
                "matrix.causal.PerceptInputs.MindInput#subject()",
                "matrix.causal.PerceptInputs.MindInput#receipts()"))
                && exactExecutables(allocation, types, Set.of(
                "matrix.causal.PerceptInputs.Allocation#<init>(matrix.causal.PerceptInputs.MindInput,java.util.List)",
                "matrix.causal.PerceptInputs.Allocation#toString()",
                "matrix.causal.PerceptInputs.Allocation#hashCode()",
                "matrix.causal.PerceptInputs.Allocation#equals(java.lang.Object)",
                "matrix.causal.PerceptInputs.Allocation#input()",
                "matrix.causal.PerceptInputs.Allocation#audits()"));
        boolean held = typeShape && exactDoor && exactMindPublic && exactAllocationPublic
                && exactExecutables
                && seenTypes.equals(expectedTypes) && violations.isEmpty()
                && compareReads.equals(expectedCompareReads)
                && canonicalReads.equals(expectedCanonicalReads)
                && allocationAuditReads.containsAll(Set.of(
                "matrix.causal.CausalRecord.ReceiptAudit#receipt()",
                "matrix.causal.CausalRecord.ReceiptAudit#delivery()",
                "matrix.causal.CausalRecord.DeliveryAttempt#tick()",
                "matrix.causal.CausalRecord.DeliveryAttempt#sequence()",
                "matrix.causal.PerceptInputs.MindInput#receipts()"));
        if (!held && explain) {
            System.out.println("SOURCE_DIAGNOSTIC shape=" + typeShape + " door=" + exactDoor
                    + " mind=" + exactMindPublic + " allocation=" + exactAllocationPublic
                    + " executables=" + exactExecutables
                    + " types=" + seenTypes + " compare=" + compareReads
                    + " canonical=" + canonicalReads + " audit_reads="
                    + allocationAuditReads + " violations=" + violations);
        }
        return held;
    }

    private static boolean isReceiptAuditArgument(TreePath invocation, Trees trees,
                                                  Types types) {
        TreePath parent = invocation.getParentPath();
        if (parent == null || !(parent.getLeaf() instanceof NewClassTree creation)
                || creation.getArguments().size() != 2
                || creation.getArguments().get(1) != invocation.getLeaf()) {
            return false;
        }
        Element element = trees.getElement(parent);
        return element instanceof ExecutableElement executable
                && key(executable, types).equals(
                "matrix.causal.CausalRecord.ReceiptAudit#<init>(matrix.causal.CausalRecord.PerceptReceipt,matrix.causal.CausalRecord.DeliveryAttempt)");
    }

    private static boolean exactPublicMethods(TypeElement type, Types types,
                                              Set<String> expected) {
        if (type == null) return false;
        List<ExecutableElement> methods = type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(method -> method.getModifiers().contains(Modifier.PUBLIC)).toList();
        return methods.stream().allMatch(method -> method.getTypeParameters().isEmpty())
                && methods.stream().map(method -> key(method, types))
                .collect(java.util.stream.Collectors.toSet()).equals(expected);
    }

    private static boolean exactExecutables(TypeElement type, Types types,
                                            Set<String> expected) {
        if (type == null) return false;
        List<ExecutableElement> methods = type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast).toList();
        return methods.stream().allMatch(method -> method.getTypeParameters().isEmpty())
                && methods.stream().map(method -> key(method, types))
                .collect(java.util.stream.Collectors.toSet()).equals(expected);
    }

    private static String owner(ExecutableElement executable) {
        return ((TypeElement) executable.getEnclosingElement()).getQualifiedName().toString();
    }

    private static String key(ExecutableElement executable, Types types) {
        String name = executable.getKind() == ElementKind.CONSTRUCTOR
                ? "<init>" : executable.getSimpleName().toString();
        String parameters = executable.getParameters().stream()
                .map(parameter -> types.erasure(parameter.asType()).toString().replace('$', '.'))
                .reduce((left, right) -> left + "," + right).orElse("");
        return owner(executable) + "#" + name + "(" + parameters + ")";
    }

    private static String bytes(matrix.causal.PerceptInputs.MindInput input) {
        return java.util.HexFormat.of().formatHex(
                input.canonical().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean visibleEquals(CausalRecord.PerceptReceipt left,
                                         CausalRecord.PerceptReceipt right) {
        return left.subject().equals(right.subject())
                && left.channel() == right.channel()
                && left.content().equals(right.content())
                && left.perceivedSource().equals(right.perceivedSource())
                && left.uncertaintyBasisPoints() == right.uncertaintyBasisPoints()
                && left.fidelity() == right.fidelity();
    }

    private static CausalRecord.ReceiptAudit audit(
            CausalRecord.Subject subject, CausalRecord.Principal human,
            CausalRecord.Principal actual, CausalRecord.Principal declared,
            long tick, int sequence, int provisionalSequence,
            CausalRecord.Channel channel, CausalRecord.Payload truthValue,
            CausalRecord.Payload visible, int uncertainty,
            CausalRecord.Fidelity fidelity, CausalRecord.DeliveryOutcome outcome) {
        CausalRecord.TruthEntry truth = new CausalRecord.TruthEntry(tick, sequence, human,
                new CausalRecord.Fact(new CausalRecord.Symbol("fixture.fact"), truthValue), actual);
        CausalRecord.DeliveryAttempt attempt = new CausalRecord.DeliveryAttempt(
                tick, sequence, subject, channel, actual, declared, truth, fidelity, outcome,
                Optional.of(visible), CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                outcome == CausalRecord.DeliveryOutcome.DELIVERED
                        ? CausalRecord.DisclosureClass.AUDIT_MATCHED
                        : CausalRecord.DisclosureClass.AUDIT_DIVERGED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        CausalRecord.PerceptReceipt receipt = new CausalRecord.PerceptReceipt(
                new CausalId.Percept(tick, provisionalSequence), subject, channel, visible,
                declared, uncertainty, fidelity);
        return new CausalRecord.ReceiptAudit(receipt, attempt);
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
            // A different checked exception is not the promised refusal.
        }
        check(kind, name, rejected);
    }

    private static void rejectsMutation(String kind, String name, Throwing action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        } catch (Exception unexpected) {
            // A different exception is not an immutability proof.
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

    private PerceptInputs() {}
}
