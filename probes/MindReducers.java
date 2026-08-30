import matrix.causal.CausalId;
import matrix.causal.CausalRecord;
import matrix.causal.PerceptInputs;
import matrix.realworld.MindReducer;
import matrix.realworld.MindState;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Runtime falsifiers for the first real, deliberately unresolved mind transition. */
public final class MindReducers {
    private static int cases;
    private static int transitionFail;
    private static int boundaryFail;
    private static int boundedFail;
    private static int deterministicFail;
    private static int rosterFail;

    public static void main(String[] args) {
        matrix.Streams.utf8();
        if (args.length != 0) {
            System.exit(Probes.Outcome.REFUSED.code());
        }
        CausalRecord.Subject subject = new CausalRecord.Subject("human-41");
        MindState genesis = MindState.initial(subject);

        PerceptInputs.MindInput empty = new PerceptInputs.MindInput(7, subject, List.of());
        transition(MindReducer.reduce(genesis, empty) == genesis, "empty-is-exact-noop");

        PerceptInputs.MindInput seen = input(subject, 8,
                receipt(subject, 8, 0, CausalRecord.Channel.VISION, "red door",
                        new CausalRecord.Principal(CausalRecord.PrincipalKind.PLACE, "room-303"),
                        2400, CausalRecord.Fidelity.FULL),
                receipt(subject, 8, 1, CausalRecord.Channel.AUDIO, "stay",
                        CausalRecord.Principal.unknown(), 8100, CausalRecord.Fidelity.PARTIAL));
        MindState next = MindReducer.reduce(genesis, seen);
        transition(next.revision() == 1 && next.history().size() == 2, "one-revision-complete-input");
        for (int i = 0; i < next.history().size(); i++) {
            MindState.MemoryTrace trace = next.history().get(i);
            CausalRecord.PerceptReceipt source = seen.receipts().get(i);
            MindState.InterpretationV1 value = trace.interpretation();
            transition(trace.id().equals(new CausalRecord.MemoryRef(subject, 1, i))
                    && trace.basis().equals(source.ref()), "ordered-citation-" + i);
            transition(value.channel() == source.channel()
                    && value.presentedContent().equals(source.content())
                    && value.perceivedSource().equals(source.perceivedSource())
                    && value.uncertaintyBasisPoints() == source.uncertaintyBasisPoints()
                    && value.presentedFidelity() == source.fidelity()
                    && value.status() == MindState.EpistemicStatus.UNRESOLVED
                    && value.presentedClaim().equals(source.presentedClaim()),
                    "complete-visible-unresolved-" + i);
        }

        MindState equal = MindReducer.reduce(MindState.initial(subject), input(subject, 8,
                seen.receipts().toArray(CausalRecord.PerceptReceipt[]::new)));
        deterministic(next.equals(equal)
                && Arrays.equals(next.canonicalBytes(), equal.canonicalBytes()),
                "equal-input-byte-identical");
        deterministic(!Arrays.equals(next.canonicalBytes(),
                MindReducer.reduce(genesis, input(subject, 8,
                        receipt(subject, 8, 0, CausalRecord.Channel.VISION, "blue door",
                                new CausalRecord.Principal(CausalRecord.PrincipalKind.PLACE,
                                        "room-303"), 2400, CausalRecord.Fidelity.FULL)))
                        .canonicalBytes()), "visible-change-changes-transition");

        CausalRecord.Principal human = new CausalRecord.Principal(
                CausalRecord.PrincipalKind.HUMAN, "human-41");
        CausalRecord.Principal claimed = new CausalRecord.Principal(
                CausalRecord.PrincipalKind.SYSTEM, "claimed-system");
        CausalRecord.ReceiptAudit hiddenA = audit(subject, human,
                new CausalRecord.Principal(CausalRecord.PrincipalKind.MACHINE, "sensor-a"),
                claimed, 10, 2, "hidden-truth-a", "visible-warning");
        CausalRecord.ReceiptAudit hiddenB = audit(subject, human,
                new CausalRecord.Principal(CausalRecord.PrincipalKind.MACHINE, "sensor-b"),
                claimed, 10, 91, "hidden-truth-b", "visible-warning");
        PerceptInputs.Allocation allocatedA = PerceptInputs.allocate(10, subject, List.of(hiddenA));
        PerceptInputs.Allocation allocatedB = PerceptInputs.allocate(10, subject, List.of(hiddenB));
        deterministic(!allocatedA.audits().equals(allocatedB.audits())
                && allocatedA.input().equals(allocatedB.input())
                && Arrays.equals(MindReducer.reduce(genesis, allocatedA.input()).canonicalBytes(),
                        MindReducer.reduce(genesis, allocatedB.input()).canonicalBytes()),
                "different-hidden-audits-cannot-change-real-transition");

        refused(() -> MindReducer.reduce(null, seen), "null-prior");
        refused(() -> MindReducer.reduce(genesis, null), "null-input");
        CausalRecord.Subject other = new CausalRecord.Subject("human-42");
        refused(() -> MindReducer.reduce(genesis, input(other, 8,
                receipt(other, 8, 0, CausalRecord.Channel.TEXT, "foreign",
                        CausalRecord.Principal.unknown(), 5000,
                        CausalRecord.Fidelity.PARTIAL))), "foreign-subject");
        refused(() -> MindReducer.reduce(next, seen), "replayed-nonempty-tick");
        refused(() -> MindReducer.reduce(next, input(subject, 8,
                receipt(subject, 8, 0, CausalRecord.Channel.TEXT, "different-same-tick",
                        CausalRecord.Principal.unknown(), 1,
                        CausalRecord.Fidelity.FULL))), "second-nonempty-batch-same-tick");
        refused(() -> MindReducer.reduce(next, input(subject, 7,
                receipt(subject, 7, 0, CausalRecord.Channel.TEXT, "older",
                        CausalRecord.Principal.unknown(), 5000,
                        CausalRecord.Fidelity.PARTIAL))), "older-nonempty-tick");

        ArrayList<CausalRecord.PerceptReceipt> oversized = new ArrayList<>();
        for (int i = 0; i <= MindState.MAX_HISTORY_V1; i++) {
            oversized.add(receipt(subject, 9, i, CausalRecord.Channel.TEXT, "v" + i,
                    CausalRecord.Principal.unknown(), i, CausalRecord.Fidelity.PARTIAL));
        }
        refused(() -> MindReducer.reduce(next,
                new PerceptInputs.MindInput(9, subject, oversized)), "oversized-input-refused");
        MindState maxRevision = new MindState(subject, Long.MAX_VALUE, List.of());
        refused(() -> MindReducer.reduce(maxRevision, input(subject, 9,
                receipt(subject, 9, 0, CausalRecord.Channel.TEXT, "overflow",
                        CausalRecord.Principal.unknown(), 0,
                        CausalRecord.Fidelity.FULL))), "revision-overflow-refused");

        MindState full = genesis;
        for (int tick = 1; tick <= MindState.MAX_HISTORY_V1; tick++) {
            full = MindReducer.reduce(full, input(subject, tick,
                    receipt(subject, tick, 0, CausalRecord.Channel.TEXT, "old-" + tick,
                            CausalRecord.Principal.unknown(), tick,
                            CausalRecord.Fidelity.PARTIAL)));
        }
        MindState rolled = MindReducer.reduce(full, input(subject, 65,
                receipt(subject, 65, 0, CausalRecord.Channel.TEXT, "new-a",
                        CausalRecord.Principal.unknown(), 1, CausalRecord.Fidelity.FULL),
                receipt(subject, 65, 1, CausalRecord.Channel.HAPTIC, "new-b",
                        CausalRecord.Principal.unknown(), 2, CausalRecord.Fidelity.FULL)));
        bounded(rolled.history().size() == MindState.MAX_HISTORY_V1, "bound-preserved");
        bounded(rolled.history().get(0).basis().id().tick() == 3, "oldest-prior-only-evicted");
        bounded(rolled.history().get(62).basis().id().tick() == 65
                && rolled.history().get(63).basis().id().tick() == 65,
                "whole-current-input-retained");

        try {
            Method[] methods = MindReducer.class.getDeclaredMethods();
            roster(methods.length == 1, "sole-method");
            Method reduce = methods[0];
            roster(reduce.getName().equals("reduce")
                    && reduce.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC)
                    && reduce.getReturnType() == MindState.class
                    && Arrays.equals(reduce.getParameterTypes(),
                            new Class<?>[]{MindState.class, PerceptInputs.MindInput.class}),
                    "exact-reducer-door");
            roster(MindReducer.class.getDeclaredFields().length == 0, "no-hidden-state");
            roster(Modifier.isPublic(MindReducer.class.getModifiers())
                    && Modifier.isFinal(MindReducer.class.getModifiers())
                    && MindReducer.class.getDeclaredConstructors().length == 1
                    && Modifier.isPrivate(MindReducer.class.getDeclaredConstructors()[0]
                    .getModifiers())
                    && MindReducer.class.getDeclaredConstructors()[0].getParameterCount() == 0,
                    "exact-class-and-private-constructor");
            roster(MindState.EpistemicStatus.values().length == 1
                    && MindState.EpistemicStatus.values()[0]
                    == MindState.EpistemicStatus.UNRESOLVED, "closed-v1-status");
        } catch (RuntimeException failure) {
            roster(false, "reflection-roster");
        }

        roster(sourceProof(java.nio.file.Path.of(".")), "attributed-closed-causal-inputs");
        roster(sourceMutant("public static MindState reduce(MindState prior, MindInput input) {",
                "public static MindState reduce(MindState prior, MindInput input) {\n"
                        + "        assert input.receipts().size() < 2;"), "assert-mutant-red");
        roster(sourceMutant("public static MindState reduce(MindState prior, MindInput input) {",
                "public static MindState reduce(MindState prior, MindInput input) {\n"
                        + "        synchronized (input) { }"), "synchronized-mutant-red");
        roster(sourceMutant("public static MindState reduce(MindState prior, MindInput input) {",
                "public static synchronized MindState reduce(MindState prior, MindInput input) {"),
                "synchronized-modifier-mutant-red");
        roster(sourceMutant("public final class MindReducer {",
                "public final class MindReducer {\n"
                        + "    static { synchronized (MindReducer.class) { } }"),
                "initializer-mutant-red");
        roster(sourceMutant("public static MindState reduce(MindState prior, MindInput input) {",
                "public static MindState reduce(MindState prior, MindInput input) {\n"
                        + "        System.getProperty(\"matrix.mind.hidden\", \"\");"),
                "property-mutant-red");
        roster(sourceMutant("public static MindState reduce(MindState prior, MindInput input) {",
                "public static MindState reduce(MindState prior, MindInput input) {\n"
                        + "        MindReducerEscape.hidden();",
                "\nfinal class MindReducerEscape { static void hidden() { } }\n"),
                "external-helper-mutant-red");

        System.out.println("MIND_REDUCER_CENSUS cases=" + cases
                + " transition_fail=" + transitionFail + " boundary_fail=" + boundaryFail
                + " bounded_fail=" + boundedFail + " deterministic_fail=" + deterministicFail
                + " roster_fail=" + rosterFail);
        Probes.leave("VERDICT MIND_REDUCER_HELD cases=" + cases
                + " cases_none=" + (cases == 0 ? 1 : 0)
                + " transition_fail=" + transitionFail + " boundary_fail=" + boundaryFail
                + " bounded_fail=" + boundedFail + " deterministic_fail=" + deterministicFail
                + " roster_fail=" + rosterFail,
                cases > 0 && transitionFail + boundaryFail + boundedFail
                        + deterministicFail + rosterFail == 0);
    }

    private static PerceptInputs.MindInput input(CausalRecord.Subject subject, long tick,
            CausalRecord.PerceptReceipt... receipts) {
        return new PerceptInputs.MindInput(tick, subject, List.of(receipts));
    }

    private static CausalRecord.PerceptReceipt receipt(CausalRecord.Subject subject, long tick,
            int sequence, CausalRecord.Channel channel, String content,
            CausalRecord.Principal source, int uncertainty, CausalRecord.Fidelity fidelity) {
        return new CausalRecord.PerceptReceipt(new CausalId.Percept(tick, sequence), subject,
                channel, new CausalRecord.Payload(content), structuredClaim(), source,
                uncertainty, fidelity);
    }

    private static CausalRecord.ReceiptAudit audit(CausalRecord.Subject subject,
            CausalRecord.Principal human, CausalRecord.Principal actual,
            CausalRecord.Principal declared, long tick, int deliverySequence,
            String hiddenTruth, String visible) {
        CausalRecord.Payload shown = new CausalRecord.Payload(visible);
        CausalRecord.TruthEntry truth = new CausalRecord.TruthEntry(tick, deliverySequence,
                human, new CausalRecord.Fact(new CausalRecord.Symbol("fixture.fact"),
                        new CausalRecord.Payload(hiddenTruth)), actual);
        CausalRecord.DeliveryAttempt attempt = new CausalRecord.DeliveryAttempt(
                tick, deliverySequence, subject, CausalRecord.Channel.TEXT, actual, declared,
                truth, CausalRecord.Fidelity.PARTIAL, CausalRecord.DeliveryOutcome.DEGRADED,
                Optional.of(shown), CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                CausalRecord.DisclosureClass.AUDIT_DIVERGED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        return new CausalRecord.ReceiptAudit(new CausalRecord.PerceptReceipt(
                new CausalId.Percept(tick, deliverySequence), subject,
                CausalRecord.Channel.TEXT, shown, structuredClaim(), declared, 777,
                CausalRecord.Fidelity.PARTIAL), attempt);
    }

    private static CausalRecord.PresentedClaim structuredClaim() {
        return CausalRecord.PresentedClaim.structured("fixture.claim", "affirmed");
    }

    private static void transition(boolean ok, String name) { cases++; if (!ok) transitionFail++; }
    private static void bounded(boolean ok, String name) { cases++; if (!ok) boundedFail++; }
    private static void deterministic(boolean ok, String name) { cases++; if (!ok) deterministicFail++; }
    private static void roster(boolean ok, String name) { cases++; if (!ok) rosterFail++; }
    private static void refused(Runnable action, String name) {
        cases++;
        try { action.run(); boundaryFail++; }
        catch (IllegalArgumentException | NullPointerException | ArithmeticException expected) { }
    }

    private static boolean sourceMutant(String needle, String replacement) {
        return sourceMutant(needle, replacement, "");
    }

    private static boolean sourceMutant(String needle, String replacement, String suffix) {
        try {
            java.nio.file.Path root = java.nio.file.Files.createTempDirectory(
                    "mind-reducer-mutant-");
            java.nio.file.Path target = root.resolve("src");
            try (java.util.stream.Stream<java.nio.file.Path> paths =
                         java.nio.file.Files.walk(java.nio.file.Path.of("src"))) {
                for (java.nio.file.Path source : paths.toList()) {
                    java.nio.file.Path relative = java.nio.file.Path.of("src").relativize(source);
                    java.nio.file.Path copy = target.resolve(relative);
                    if (java.nio.file.Files.isDirectory(source)) {
                        java.nio.file.Files.createDirectories(copy);
                    } else {
                        java.nio.file.Files.copy(source, copy);
                    }
                }
            }
            java.nio.file.Path reducer = target.resolve("matrix/realworld/MindReducer.java");
            String source = java.nio.file.Files.readString(reducer);
            if (!source.contains(needle)) return false;
            java.nio.file.Files.writeString(reducer,
                    source.replace(needle, replacement) + suffix);
            return !sourceProof(root);
        } catch (Exception failure) {
            return false;
        }
    }

    private static boolean sourceProof(java.nio.file.Path root) {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return false;
        try {
            java.nio.file.Path output = java.nio.file.Files.createTempDirectory("mind-reducer-proof-");
            List<java.io.File> files;
            try (java.util.stream.Stream<java.nio.file.Path> paths =
                         java.nio.file.Files.walk(root.resolve("src"))) {
                files = paths.filter(path -> path.toString().endsWith(".java"))
                        .map(java.nio.file.Path::toFile).toList();
            }
            javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics =
                    new javax.tools.DiagnosticCollector<>();
            try (javax.tools.StandardJavaFileManager manager = compiler.getStandardFileManager(
                    diagnostics, java.util.Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
                com.sun.source.util.JavacTask task = (com.sun.source.util.JavacTask)
                        compiler.getTask(null, manager, diagnostics,
                                List.of("-proc:none", "--release", "17", "-classpath", "",
                                        "-sourcepath", "", "-d", output.toString()),
                                null, manager.getJavaFileObjectsFromFiles(files));
                List<com.sun.source.tree.CompilationUnitTree> units = new ArrayList<>();
                task.parse().forEach(units::add);
                task.analyze();
                if (diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR)) return false;
                return inspectReducer(task, units);
            }
        } catch (Exception failure) {
            return false;
        }
    }

    private static boolean inspectReducer(com.sun.source.util.JavacTask task,
            List<com.sun.source.tree.CompilationUnitTree> units) {
        com.sun.source.util.Trees trees = com.sun.source.util.Trees.instance(task);
        javax.lang.model.util.Types types = task.getTypes();
        javax.lang.model.element.TypeElement reducer = task.getElements()
                .getTypeElement("matrix.realworld.MindReducer");
        if (reducer == null || !reducer.getModifiers().equals(Set.of(
                javax.lang.model.element.Modifier.PUBLIC,
                javax.lang.model.element.Modifier.FINAL))
                || reducer.getEnclosedElements().stream().anyMatch(element ->
                element.getKind().isField())) return false;
        List<javax.lang.model.element.ExecutableElement> executables = reducer
                .getEnclosedElements().stream().filter(element ->
                        element.getKind() == javax.lang.model.element.ElementKind.METHOD
                                || element.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR)
                .map(javax.lang.model.element.ExecutableElement.class::cast).toList();
        if (!executables.stream().map(method -> executableKey(method, types)).collect(
                java.util.stream.Collectors.toSet()).equals(Set.of(
                "matrix.realworld.MindReducer#reduce(matrix.realworld.MindState,matrix.causal.PerceptInputs.MindInput)",
                "matrix.realworld.MindReducer#<init>()"))) return false;
        javax.lang.model.element.ExecutableElement reduce = executables.stream()
                .filter(method -> method.getSimpleName().contentEquals("reduce"))
                .findFirst().orElse(null);
        if (reduce == null || !reduce.getModifiers().equals(Set.of(
                javax.lang.model.element.Modifier.PUBLIC,
                javax.lang.model.element.Modifier.STATIC))) return false;

        Set<String> allowedCalls = Set.of(
                "java.lang.Math#addExact(long,long)", "java.lang.Math#min(int,int)",
                "java.lang.Object#<init>()",
                "java.util.ArrayList#add(java.lang.Object)",
                "java.util.ArrayList#addAll(java.util.Collection)",
                "java.util.List#get(int)", "java.util.List#isEmpty()",
                "java.util.List#size()", "java.util.List#subList(int,int)",
                "java.util.Objects#requireNonNull(java.lang.Object,java.lang.String)",
                "matrix.causal.CausalId.Percept#tick()",
                "matrix.causal.CausalRecord.PerceptReceipt#channel()",
                "matrix.causal.CausalRecord.PerceptReceipt#content()",
                "matrix.causal.CausalRecord.PerceptReceipt#fidelity()",
                "matrix.causal.CausalRecord.PerceptReceipt#perceivedSource()",
                "matrix.causal.CausalRecord.PerceptReceipt#presentedClaim()",
                "matrix.causal.CausalRecord.PerceptReceipt#ref()",
                "matrix.causal.CausalRecord.PerceptReceipt#uncertaintyBasisPoints()",
                "matrix.causal.CausalRecord.PerceptRef#id()",
                "matrix.causal.CausalRecord.Subject#equals(java.lang.Object)",
                "matrix.causal.PerceptInputs.MindInput#receipts()",
                "matrix.causal.PerceptInputs.MindInput#subject()",
                "matrix.causal.PerceptInputs.MindInput#tick()",
                "matrix.realworld.MindState#history()", "matrix.realworld.MindState#revision()",
                "matrix.realworld.MindState#subject()",
                "matrix.realworld.MindState.MemoryTrace#basis()");
        Set<String> allowedConstructors = Set.of(
                "java.lang.IllegalArgumentException#<init>(java.lang.String)",
                "java.util.ArrayList#<init>(int)",
                "matrix.causal.CausalRecord.MemoryRef#<init>(matrix.causal.CausalRecord.Subject,long,int)",
                "matrix.realworld.MindState#<init>(matrix.causal.CausalRecord.Subject,long,java.util.List)",
                "matrix.realworld.MindState.InterpretationV1#<init>(matrix.causal.CausalRecord.Channel,matrix.causal.CausalRecord.Payload,matrix.causal.CausalRecord.Principal,int,matrix.causal.CausalRecord.Fidelity,matrix.realworld.MindState.EpistemicStatus,matrix.causal.CausalRecord.PresentedClaim)",
                "matrix.realworld.MindState.MemoryTrace#<init>(matrix.causal.CausalRecord.MemoryRef,matrix.causal.CausalRecord.PerceptRef,matrix.realworld.MindState.InterpretationV1)");
        Set<String> allowedFields = Set.of(
                "matrix.realworld.MindState#MAX_HISTORY_V1",
                "matrix.realworld.MindState.EpistemicStatus#UNRESOLVED");
        Set<String> violations = new java.util.LinkedHashSet<>();
        Set<String> observed = new java.util.LinkedHashSet<>();
        for (com.sun.source.tree.CompilationUnitTree unit : units) {
            new com.sun.source.util.TreePathScanner<Void, Void>() {
                private int depth;
                @Override public Void visitClass(com.sun.source.tree.ClassTree node, Void unused) {
                    javax.lang.model.element.Element element = trees.getElement(getCurrentPath());
                    boolean enters = element != null && element.equals(reducer);
                    if (enters) depth++;
                    super.visitClass(node, unused);
                    if (enters) depth--;
                    return null;
                }
                @Override public Void visitMethod(com.sun.source.tree.MethodTree node, Void unused) {
                    if (depth > 0 && (node.getBody() == null || node.getModifiers().getFlags()
                            .contains(javax.lang.model.element.Modifier.NATIVE))) {
                        violations.add("bodyless");
                    }
                    return super.visitMethod(node, unused);
                }
                @Override public Void visitMethodInvocation(
                        com.sun.source.tree.MethodInvocationTree node, Void unused) {
                    if (depth > 0) {
                        javax.lang.model.element.Element element = trees.getElement(
                                new com.sun.source.util.TreePath(getCurrentPath(), node.getMethodSelect()));
                        if (!(element instanceof javax.lang.model.element.ExecutableElement method)) {
                            violations.add("unresolved-call");
                        } else {
                            String key = executableKey(method, types); observed.add(key);
                            if (!key.startsWith("matrix.realworld.MindReducer#")
                                    && !allowedCalls.contains(key)) violations.add("call:" + key);
                        }
                    }
                    return super.visitMethodInvocation(node, unused);
                }
                @Override public Void visitNewClass(com.sun.source.tree.NewClassTree node,
                        Void unused) {
                    if (depth > 0) {
                        javax.lang.model.element.Element element = trees.getElement(getCurrentPath());
                        if (!(element instanceof javax.lang.model.element.ExecutableElement constructor)
                                || !allowedConstructors.contains(executableKey(constructor, types))) {
                            violations.add("new:" + element);
                        }
                    }
                    return super.visitNewClass(node, unused);
                }
                @Override public Void visitIdentifier(com.sun.source.tree.IdentifierTree node,
                        Void unused) { if (depth > 0) field(getCurrentPath()); return super.visitIdentifier(node, unused); }
                @Override public Void visitMemberSelect(com.sun.source.tree.MemberSelectTree node,
                        Void unused) { if (depth > 0) field(getCurrentPath()); return super.visitMemberSelect(node, unused); }
                private void field(com.sun.source.util.TreePath path) {
                    javax.lang.model.element.Element element = trees.getElement(path);
                    if (element != null && (element.getKind().isField()
                            || element.getKind() == javax.lang.model.element.ElementKind.ENUM_CONSTANT)
                            && element.getEnclosingElement() instanceof javax.lang.model.element.TypeElement owner) {
                        String key = owner.getQualifiedName() + "#" + element.getSimpleName();
                        if (!allowedFields.contains(key)) violations.add("field:" + key);
                    }
                }
                @Override public Void visitLambdaExpression(
                        com.sun.source.tree.LambdaExpressionTree node, Void unused) {
                    if (depth > 0) violations.add("lambda");
                    return super.visitLambdaExpression(node, unused);
                }
                @Override public Void visitMemberReference(
                        com.sun.source.tree.MemberReferenceTree node, Void unused) {
                    if (depth > 0) violations.add("reference");
                    return super.visitMemberReference(node, unused);
                }
                @Override public Void visitAssert(com.sun.source.tree.AssertTree node,
                        Void unused) {
                    if (depth > 0) violations.add("assert");
                    return super.visitAssert(node, unused);
                }
                @Override public Void visitSynchronized(
                        com.sun.source.tree.SynchronizedTree node, Void unused) {
                    if (depth > 0) violations.add("synchronized");
                    return super.visitSynchronized(node, unused);
                }
                @Override public Void visitBlock(com.sun.source.tree.BlockTree node,
                        Void unused) {
                    com.sun.source.util.TreePath parent = getCurrentPath().getParentPath();
                    if (depth > 0 && parent != null
                            && parent.getLeaf() instanceof com.sun.source.tree.ClassTree) {
                        violations.add("initializer");
                    }
                    return super.visitBlock(node, unused);
                }
            }.scan(unit, null);
        }
        return violations.isEmpty() && observed.containsAll(Set.of(
                "matrix.causal.PerceptInputs.MindInput#receipts()",
                "matrix.causal.CausalRecord.PerceptReceipt#channel()",
                "matrix.causal.CausalRecord.PerceptReceipt#content()",
                "matrix.causal.CausalRecord.PerceptReceipt#perceivedSource()",
                "matrix.causal.CausalRecord.PerceptReceipt#uncertaintyBasisPoints()",
                "matrix.causal.CausalRecord.PerceptReceipt#fidelity()"));
    }

    private static String executableKey(javax.lang.model.element.ExecutableElement executable,
            javax.lang.model.util.Types types) {
        javax.lang.model.element.TypeElement owner = (javax.lang.model.element.TypeElement)
                executable.getEnclosingElement();
        String name = executable.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR
                ? "<init>" : executable.getSimpleName().toString();
        String parameters = executable.getParameters().stream().map(parameter ->
                types.erasure(parameter.asType()).toString().replace('$', '.'))
                .reduce((left, right) -> left + "," + right).orElse("");
        return owner.getQualifiedName() + "#" + name + "(" + parameters + ")";
    }

    private MindReducers() {}
}
