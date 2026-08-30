import matrix.Simulation;
import matrix.causal.CausalRecord;
import matrix.causal.TruthSnapshot;
import matrix.entities.Pill;
import matrix.realworld.Human;
import matrix.realworld.RealWorld;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Probe: does phase two record one honest audit for every frozen V1 fact? (#1691) */
public final class DeliveryAttempts {
    private static final Map<String, Integer> CASES = new LinkedHashMap<>();
    private static final Map<String, Integer> FAILURES = new LinkedHashMap<>();
    private static final List<String> BREAKS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        Path root = args.length == 0 ? Path.of("src") : Path.of(args[0]);
        if (args.length > 1 || !Files.isDirectory(root)) {
            System.out.println("FATAL DELIVERY_ATTEMPT_ROOT_UNREADABLE root=" + root);
            System.exit(Probes.Outcome.REFUSED.code());
        }
        TruthSnapshot empty = TruthSnapshot.empty(9);
        List<CausalRecord.DeliveryAttempt> none =
                matrix.causal.DeliveryAttempts.connectedResidentSelfV1(empty);
        check("bijection", "explicit-empty", none.isEmpty());
        check("immutable", "empty-random-access", none instanceof RandomAccess);

        TruthSnapshot first = snapshot(9);
        List<CausalRecord.DeliveryAttempt> attempts =
                matrix.causal.DeliveryAttempts.connectedResidentSelfV1(first);
        check("bijection", "one-per-entry", attempts.size() == first.entries().size());
        check("immutable", "random-access", attempts instanceof RandomAccess);
        rejects("immutable", "empty-add", () -> none.add(attempts.get(0)));
        rejects("immutable", "add", () -> attempts.add(attempts.get(0)));
        rejects("immutable", "set", () -> attempts.set(0, attempts.get(0)));
        rejects("immutable", "remove", () -> attempts.remove(0));
        rejects("immutable", "clear", attempts::clear);
        rejects("immutable", "iterator-remove", () -> {
            var iterator = attempts.iterator();
            iterator.next();
            iterator.remove();
        });
        rejects("immutable", "list-iterator-set", () -> {
            var iterator = attempts.listIterator();
            iterator.next();
            iterator.set(attempts.get(0));
        });
        rejects("immutable", "sublist-clear", () -> attempts.subList(0, 1).clear());

        List<CausalRecord.Channel> channels = List.of(
                CausalRecord.Channel.INTERNAL,
                CausalRecord.Channel.INTERNAL,
                CausalRecord.Channel.VISION,
                CausalRecord.Channel.INTERNAL,
                CausalRecord.Channel.INTERNAL,
                CausalRecord.Channel.VISION);
        for (int index = 0; index < attempts.size(); index++) {
            CausalRecord.TruthEntry truth = first.entries().get(index);
            CausalRecord.DeliveryAttempt attempt = attempts.get(index);
            check("bijection", "order-" + index,
                    attempt.tick() == truth.tick()
                            && attempt.sequence() == truth.sequence()
                            && attempt.truth().equals(truth)
                            && attempt.subject().key().equals(truth.subject().key()));
            check("channel", "channel-" + index, attempt.channel() == channels.get(index));
            check("policy", "exact-tuple-" + index,
                    attempt.actualSource().equals(truth.provenance())
                            && attempt.declaredSource().equals(truth.provenance())
                            && attempt.presentedContent().equals(Optional.of(truth.fact().value()))
                            && attempt.fidelity() == CausalRecord.Fidelity.FULL
                            && attempt.outcome() == CausalRecord.DeliveryOutcome.DELIVERED
                            && attempt.rule()
                            == CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1
                            && attempt.authority() == CausalRecord.AuthorityClass.UNESTABLISHED
                            && attempt.consent() == CausalRecord.ConsentClass.UNESTABLISHED
                            && attempt.disclosure()
                            == CausalRecord.DisclosureClass.AUDIT_MATCHED
                            && attempt.constraint() == CausalRecord.ConstraintClass.NO_EVIDENCE
                            && attempt.obligation() == CausalRecord.ObligationClass.NONE_CITED);
        }

        List<CausalRecord.DeliveryAttempt> replay =
                matrix.causal.DeliveryAttempts.connectedResidentSelfV1(snapshot(9));
        check("replay", "equal-input-equal-output", attempts.equals(replay));
        check("replay", "equal-hash", attempts.hashCode() == replay.hashCode());
        check("replay", "tick-remains-causal",
                !attempts.equals(matrix.causal.DeliveryAttempts
                        .connectedResidentSelfV1(snapshot(10))));
        check("replay", "ordinal-remains-causal", !attempts.equals(project(
                snapshot(9, 8, TruthSnapshot.ResidentPill.RED, 30, 40))));
        check("replay", "pill-remains-causal", !attempts.equals(project(
                snapshot(9, 7, TruthSnapshot.ResidentPill.BLUE, 30, 40))));
        check("replay", "x-remains-causal", !attempts.equals(project(
                snapshot(9, 7, TruthSnapshot.ResidentPill.RED, 31, 40))));
        check("replay", "y-remains-causal", !attempts.equals(project(
                snapshot(9, 7, TruthSnapshot.ResidentPill.RED, 30, 41))));
        check("immutable", "lazy-equal-not-cached",
                attempts.get(0).equals(attempts.get(0)) && attempts.get(0) != attempts.get(0));

        productionHandoff();

        constructorAttacks(attempts.get(0));
        nullComponentContract(attempts.get(0));
        roster("rule", CausalRecord.DeliveryRule.values(), 1);
        roster("authority", CausalRecord.AuthorityClass.values(), 1);
        roster("consent", CausalRecord.ConsentClass.values(), 1);
        roster("disclosure", CausalRecord.DisclosureClass.values(), 3);
        roster("constraint", CausalRecord.ConstraintClass.values(), 1);
        roster("obligation", CausalRecord.ObligationClass.values(), 1);
        check("roster", "exact-disclosure-names", java.util.Arrays.stream(
                CausalRecord.DisclosureClass.values()).map(Enum::name).toList().equals(List.of(
                        "AUDIT_MATCHED", "AUDIT_DIVERGED", "NOT_PRESENTED")));
        check("roster", "exact-epistemic-names",
                CausalRecord.DeliveryRule.values()[0].name()
                        .equals("CONNECTED_RESIDENT_SELF_V1")
                        && CausalRecord.AuthorityClass.values()[0].name()
                        .equals("UNESTABLISHED")
                        && CausalRecord.ConsentClass.values()[0].name()
                        .equals("UNESTABLISHED")
                        && CausalRecord.ConstraintClass.values()[0].name()
                        .equals("NO_EVIDENCE")
                        && CausalRecord.ObligationClass.values()[0].name()
                        .equals("NONE_CITED"));
        structuralContract();
        sourceContract(root.resolve(Path.of("matrix", "causal", "DeliveryAttempts.java")));

        for (String broken : BREAKS) {
            System.out.println("DELIVERY_ATTEMPT_BREAK " + broken);
        }
        int cases = CASES.values().stream().mapToInt(Integer::intValue).sum();
        boolean held = cases > 0
                && FAILURES.values().stream().mapToInt(Integer::intValue).sum() == 0;
        Probes.leave("VERDICT DELIVERY_ATTEMPTS_" + (held ? "HELD" : "BROKEN")
                + " cases=" + cases + " cases_none=" + (cases == 0 ? 1 : 0)
                + " bijection_fail=" + failures("bijection")
                + " channel_fail=" + failures("channel")
                + " policy_fail=" + failures("policy")
                + " immutable_fail=" + failures("immutable")
                + " replay_fail=" + failures("replay")
                + " constructor_fail=" + failures("constructor")
                + " roster_fail=" + failures("roster")
                + " source_fail=" + failures("source"), held);
    }

    private static TruthSnapshot snapshot(long tick) {
        return snapshot(tick, 7, TruthSnapshot.ResidentPill.RED, 30, 40);
    }

    private static TruthSnapshot snapshot(long tick, int ordinal,
            TruthSnapshot.ResidentPill pill, int x, int y) {
        TruthSnapshot.Builder builder = new TruthSnapshot.Builder();
        builder.begin();
        builder.add(ordinal, pill, x, y);
        builder.add(2, TruthSnapshot.ResidentPill.BLUE, 10, 20);
        return builder.build(tick);
    }

    private static List<CausalRecord.DeliveryAttempt> project(TruthSnapshot snapshot) {
        return matrix.causal.DeliveryAttempts.connectedResidentSelfV1(snapshot);
    }

    private static void productionHandoff() throws Exception {
        Simulation simulation = new Simulation(42, null, null);
        invoke(simulation, "beginCausalTick");
        invoke(simulation, "snapshotTruth");
        TruthSnapshot frozen = field(simulation, "tickTruth", TruthSnapshot.class);
        List<CausalRecord.TruthEntry> before = List.copyOf(frozen.entries());
        RealWorld real = field(simulation, "realWorld", RealWorld.class);
        int frozenOrdinal = Integer.parseInt(before.get(0).subject().key().value()
                .substring("human-".length()));
        Human human = real.humans().stream().filter(candidate -> candidate.id == frozenOrdinal)
                .findFirst().orElseThrow();
        int oldX = human.link().avatar.xCm();
        Pill oldPill = human.link().avatar.pill;
        human.link().avatar.placeAt(oldX == 0 ? 1 : 0, human.link().avatar.yCm());
        human.link().avatar.pill = oldPill == Pill.BLUE ? Pill.RED : Pill.BLUE;
        human.brain.flatline();
        invoke(simulation, "deliverPercepts");
        List<?> delivered = field(simulation, "deliveryAttempts", List.class);
        check("replay", "post-freeze-mutation-cannot-enter-attempts",
                frozen.entries().equals(before) && delivered.size() == before.size()
                        && java.util.stream.IntStream.range(0, before.size()).allMatch(index ->
                                matchesFrozenAttempt(
                                        (CausalRecord.DeliveryAttempt) delivered.get(index),
                                        before.get(index))));

        Simulation ordinary = new Simulation(42, null, null);
        ordinary.tickOnce();
        List<?> tickOne = field(ordinary, "deliveryAttempts", List.class);
        ordinary.tickOnce();
        List<?> tickTwo = field(ordinary, "deliveryAttempts", List.class);
        check("replay", "consecutive-ticks-rederive-attempts",
                !tickOne.isEmpty() && !tickTwo.isEmpty()
                        && ((CausalRecord.DeliveryAttempt) tickOne.get(0)).tick() == 1
                        && ((CausalRecord.DeliveryAttempt) tickTwo.get(0)).tick() == 2
                        && !tickOne.equals(tickTwo));
    }

    private static void invoke(Simulation simulation, String name) throws Exception {
        Method method = Simulation.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(simulation);
    }

    private static boolean matchesFrozenAttempt(CausalRecord.DeliveryAttempt attempt,
            CausalRecord.TruthEntry truth) {
        CausalRecord.Channel expectedChannel = switch (truth.fact().predicate().value()) {
            case "brain.alive", "avatar.pill" -> CausalRecord.Channel.INTERNAL;
            case "avatar.position_cm" -> CausalRecord.Channel.VISION;
            default -> null;
        };
        return expectedChannel != null && attempt.tick() == truth.tick()
                && attempt.sequence() == truth.sequence()
                && attempt.subject().key().equals(truth.subject().key())
                && attempt.channel() == expectedChannel
                && attempt.actualSource().equals(truth.provenance())
                && attempt.declaredSource().equals(truth.provenance())
                && attempt.truth().equals(truth)
                && attempt.presentedContent().equals(Optional.of(truth.fact().value()))
                && attempt.fidelity() == CausalRecord.Fidelity.FULL
                && attempt.outcome() == CausalRecord.DeliveryOutcome.DELIVERED
                && attempt.rule()
                == CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1
                && attempt.disclosure() == CausalRecord.DisclosureClass.AUDIT_MATCHED
                && attempt.authority() == CausalRecord.AuthorityClass.UNESTABLISHED
                && attempt.consent() == CausalRecord.ConsentClass.UNESTABLISHED
                && attempt.constraint() == CausalRecord.ConstraintClass.NO_EVIDENCE
                && attempt.obligation() == CausalRecord.ObligationClass.NONE_CITED;
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static void constructorAttacks(CausalRecord.DeliveryAttempt base) {
        CausalRecord.Principal other = new CausalRecord.Principal(
                CausalRecord.PrincipalKind.SYSTEM, "other");
        CausalRecord.DeliveryAttempt degradedContent = copy(base,
                CausalRecord.DeliveryOutcome.DEGRADED, CausalRecord.Fidelity.PARTIAL,
                Optional.of(new CausalRecord.Payload("different")),
                CausalRecord.DisclosureClass.AUDIT_DIVERGED, base.declaredSource());
        check("constructor", "degraded-content-valid",
                degradedContent.disclosure() == CausalRecord.DisclosureClass.AUDIT_DIVERGED);
        CausalRecord.DeliveryAttempt degradedSource = copy(base,
                CausalRecord.DeliveryOutcome.DEGRADED, CausalRecord.Fidelity.PARTIAL,
                base.presentedContent(), CausalRecord.DisclosureClass.AUDIT_DIVERGED, other);
        check("constructor", "degraded-source-valid",
                degradedSource.declaredSource().equals(other));
        CausalRecord.DeliveryAttempt occluded = copy(base,
                CausalRecord.DeliveryOutcome.OCCLUDED, CausalRecord.Fidelity.NONE,
                Optional.empty(), CausalRecord.DisclosureClass.NOT_PRESENTED,
                base.declaredSource());
        check("constructor", "occluded-valid", occluded.presentedContent().isEmpty());
        rejects("constructor", "delivered-content-diverged", () -> copy(base,
                CausalRecord.DeliveryOutcome.DELIVERED, CausalRecord.Fidelity.FULL,
                Optional.of(new CausalRecord.Payload("different")),
                CausalRecord.DisclosureClass.AUDIT_MATCHED, base.declaredSource()));
        rejects("constructor", "delivered-source-diverged", () -> copy(base,
                CausalRecord.DeliveryOutcome.DELIVERED, CausalRecord.Fidelity.FULL,
                base.presentedContent(), CausalRecord.DisclosureClass.AUDIT_MATCHED,
                other));
        rejects("constructor", "degraded-needs-divergence", () -> copy(base,
                CausalRecord.DeliveryOutcome.DEGRADED, CausalRecord.Fidelity.PARTIAL,
                base.presentedContent(), CausalRecord.DisclosureClass.AUDIT_DIVERGED,
                base.declaredSource()));
        rejects("constructor", "occluded-not-presented", () -> copy(base,
                CausalRecord.DeliveryOutcome.OCCLUDED, CausalRecord.Fidelity.NONE,
                Optional.empty(), CausalRecord.DisclosureClass.AUDIT_MATCHED,
                base.declaredSource()));
        rejects("constructor", "actual-not-provenance", () -> new CausalRecord.DeliveryAttempt(
                base.tick(), base.sequence(), base.subject(), base.channel(), other,
                base.declaredSource(), base.truth(), base.fidelity(), base.outcome(),
                base.presentedContent(), base.rule(), base.authority(), base.consent(),
                base.disclosure(), base.constraint(), base.obligation()));
        rejects("constructor", "sequence-not-truth", () -> new CausalRecord.DeliveryAttempt(
                base.tick(), base.sequence() + 1, base.subject(), base.channel(),
                base.actualSource(), base.declaredSource(), base.truth(), base.fidelity(),
                base.outcome(), base.presentedContent(), base.rule(), base.authority(),
                base.consent(), base.disclosure(), base.constraint(), base.obligation()));
    }

    private static CausalRecord.DeliveryAttempt copy(CausalRecord.DeliveryAttempt base,
            CausalRecord.DeliveryOutcome outcome, CausalRecord.Fidelity fidelity,
            Optional<CausalRecord.Payload> content,
            CausalRecord.DisclosureClass disclosure, CausalRecord.Principal declared) {
        return new CausalRecord.DeliveryAttempt(base.tick(), base.sequence(), base.subject(),
                base.channel(), base.actualSource(), declared, base.truth(), fidelity, outcome,
                content, base.rule(), base.authority(), base.consent(), disclosure,
                base.constraint(), base.obligation());
    }

    private static void nullComponentContract(CausalRecord.DeliveryAttempt base)
            throws Exception {
        var components = CausalRecord.DeliveryAttempt.class.getRecordComponents();
        var constructor = CausalRecord.DeliveryAttempt.class.getDeclaredConstructors()[0];
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            values[index] = components[index].getAccessor().invoke(base);
        }
        for (int index = 0; index < components.length; index++) {
            if (components[index].getType().isPrimitive()) continue;
            Object saved = values[index];
            values[index] = null;
            boolean rejected = false;
            try {
                constructor.newInstance(values);
            } catch (java.lang.reflect.InvocationTargetException expected) {
                rejected = expected.getCause() instanceof NullPointerException;
            }
            check("constructor", "null-" + components[index].getName(), rejected);
            values[index] = saved;
        }
    }

    private static void roster(String name, Object[] values, int expected) {
        check("roster", name, values.length == expected);
    }

    private static void structuralContract() {
        check("roster", "exact-attempt-components", java.util.Arrays.stream(
                CausalRecord.DeliveryAttempt.class.getRecordComponents())
                .map(component -> component.getName()).toList().equals(List.of(
                        "tick", "sequence", "subject", "channel", "actualSource",
                        "declaredSource", "truth", "fidelity", "outcome",
                        "presentedContent", "rule", "authority", "consent",
                        "disclosure", "constraint", "obligation")));
        Class<?> mapper = matrix.causal.DeliveryAttempts.class;
        check("immutable", "mapper-final", Modifier.isFinal(mapper.getModifiers()));
        check("immutable", "mapper-three-symbol-fields",
                mapper.getDeclaredFields().length == 3
                        && java.util.Arrays.stream(mapper.getDeclaredFields()).allMatch(field ->
                                Modifier.isPrivate(field.getModifiers())
                                        && Modifier.isStatic(field.getModifiers())
                                        && Modifier.isFinal(field.getModifiers())
                                        && field.getType() == CausalRecord.Symbol.class));
        check("roster", "one-public-mapper", java.util.Arrays.stream(
                        mapper.getDeclaredMethods()).filter(method ->
                                Modifier.isPublic(method.getModifiers())).count() == 1
                        && java.util.Arrays.stream(mapper.getDeclaredMethods()).anyMatch(method ->
                                method.getName().equals("connectedResidentSelfV1")
                                        && Modifier.isPublic(method.getModifiers())
                                        && Modifier.isStatic(method.getModifiers())
                                        && method.getParameterCount() == 1
                                        && method.getParameterTypes()[0] == TruthSnapshot.class
                                        && method.getReturnType() == List.class));
        Class<?> view = java.util.Arrays.stream(mapper.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("AttemptView"))
                .findFirst().orElse(null);
        check("immutable", "view-one-final-entry-field", view != null
                && Modifier.isPrivate(view.getModifiers()) && Modifier.isFinal(view.getModifiers())
                && view.getDeclaredFields().length == 1
                && view.getDeclaredFields()[0].getName().equals("truth")
                && view.getDeclaredFields()[0].getType() == List.class
                && Modifier.isPrivate(view.getDeclaredFields()[0].getModifiers())
                && Modifier.isFinal(view.getDeclaredFields()[0].getModifiers()));
    }

    /**
     * Ask javac what every field, call and construction in the mapper means.
     * The class has a closed capability roster: private helpers are welcome,
     * but no spelling can smuggle in a world, callback, clock, RNG or reflector.
     */
    private static void sourceContract(Path source) throws IOException {
        Path sourceRoot = source.getParent().getParent().getParent();
        List<String> production = attributedViolations(source, sourceRoot);
        if (!production.isEmpty()) {
            BREAKS.addAll(production.stream().map(value -> "source " + value).toList());
        }
        check("source", "closed-attributed-capabilities", production.isEmpty());

        String canonical = Files.readString(source);
        Path scratch = Files.createTempDirectory("delivery-attempt-source-");
        Path mutant = scratch.resolve("DeliveryAttempts.java");
        Map<String, String> mutants = Map.of(
                "direct-live-world",
                canonical.replace("Objects.requireNonNull(snapshot, \"delivery truth snapshot\");",
                        "Objects.requireNonNull(snapshot, \"delivery truth snapshot\");"
                        + " matrix.World.class.getDeclaredMethods();"),
                "rng-read",
                canonical.replace("Objects.requireNonNull(snapshot, \"delivery truth snapshot\");",
                        "Objects.requireNonNull(snapshot, \"delivery truth snapshot\");"
                        + " new java.util.Random().nextInt();"),
                "reflection-read",
                canonical.replace("Objects.requireNonNull(snapshot, \"delivery truth snapshot\");",
                        "Objects.requireNonNull(snapshot, \"delivery truth snapshot\");"
                        + " DeliveryAttempts.class.getDeclaredFields();"),
                "callback",
                canonical.replace("Objects.requireNonNull(snapshot, \"delivery truth snapshot\");",
                        "Objects.requireNonNull(snapshot, \"delivery truth snapshot\");"
                        + " java.util.List.of(1).forEach(x -> x.toString());"),
                "same-class-native",
                canonical.replace("Objects.requireNonNull(snapshot, \"delivery truth snapshot\");",
                        "Objects.requireNonNull(snapshot, \"delivery truth snapshot\"); leak();")
                        .replace("private DeliveryAttempts() {}",
                                "private static native long leak();\n"
                                + "    private DeliveryAttempts() {}"),
                "prefix-native",
                canonical.replace("Objects.requireNonNull(snapshot, \"delivery truth snapshot\");",
                        "Objects.requireNonNull(snapshot, \"delivery truth snapshot\");"
                        + " DeliveryAttemptsEscape.leak();")
                        + "\nclass DeliveryAttemptsEscape { static native long leak(); }\n");
        for (Map.Entry<String, String> attack : mutants.entrySet()) {
            Files.writeString(mutant, attack.getValue());
            check("source", "rejects-" + attack.getKey(),
                    !attributedViolations(mutant, sourceRoot).isEmpty());
        }
    }

    private static List<String> attributedViolations(Path source, Path sourceRoot)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || !Files.isRegularFile(source)) {
            return List.of("unreadable-source=" + source);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Set<String> allowedFields = Set.of(
                "matrix.causal.DeliveryAttempts#BRAIN_ALIVE",
                "matrix.causal.DeliveryAttempts#AVATAR_PILL",
                "matrix.causal.DeliveryAttempts#AVATAR_POSITION_CM",
                "matrix.causal.DeliveryAttempts.AttemptView#truth",
                "matrix.causal.TruthSnapshot.EligibilityRule#CONNECTED_RESIDENT_SELF_V1",
                "matrix.causal.CausalRecord.Channel#INTERNAL",
                "matrix.causal.CausalRecord.Channel#VISION",
                "matrix.causal.CausalRecord.Fidelity#FULL",
                "matrix.causal.CausalRecord.DeliveryOutcome#DELIVERED",
                "matrix.causal.CausalRecord.DeliveryRule#CONNECTED_RESIDENT_SELF_V1",
                "matrix.causal.CausalRecord.AuthorityClass#UNESTABLISHED",
                "matrix.causal.CausalRecord.ConsentClass#UNESTABLISHED",
                "matrix.causal.CausalRecord.DisclosureClass#AUDIT_MATCHED",
                "matrix.causal.CausalRecord.ConstraintClass#NO_EVIDENCE",
                "matrix.causal.CausalRecord.ObligationClass#NONE_CITED");
        Set<String> allowedCalls = Set.of(
                "java.util.Objects#requireNonNull(java.lang.Object,java.lang.String)",
                "java.lang.Object#<init>()",
                "java.util.AbstractList#<init>()",
                "matrix.causal.TruthSnapshot#eligibility()",
                "matrix.causal.TruthSnapshot#entries()",
                "java.lang.IllegalArgumentException#<init>(java.lang.String)",
                "java.util.List#get(int)",
                "java.util.List#size()",
                "matrix.causal.CausalRecord.Principal#key()",
                "matrix.causal.CausalRecord.TruthEntry#tick()",
                "matrix.causal.CausalRecord.TruthEntry#sequence()",
                "matrix.causal.CausalRecord.TruthEntry#subject()",
                "matrix.causal.CausalRecord.TruthEntry#fact()",
                "matrix.causal.CausalRecord.TruthEntry#provenance()",
                "matrix.causal.CausalRecord.Fact#predicate()",
                "matrix.causal.CausalRecord.Fact#value()",
                "matrix.causal.CausalRecord.Subject#<init>(matrix.causal.CausalRecord.Symbol)",
                "matrix.causal.CausalRecord.DeliveryAttempt#<init>(long,int,matrix.causal.CausalRecord.Subject,matrix.causal.CausalRecord.Channel,matrix.causal.CausalRecord.Principal,matrix.causal.CausalRecord.Principal,matrix.causal.CausalRecord.TruthEntry,matrix.causal.CausalRecord.Fidelity,matrix.causal.CausalRecord.DeliveryOutcome,java.util.Optional,matrix.causal.CausalRecord.DeliveryRule,matrix.causal.CausalRecord.AuthorityClass,matrix.causal.CausalRecord.ConsentClass,matrix.causal.CausalRecord.DisclosureClass,matrix.causal.CausalRecord.ConstraintClass,matrix.causal.CausalRecord.ObligationClass)",
                "java.util.Optional#of(java.lang.Object)",
                "matrix.causal.CausalRecord.Symbol#equals(java.lang.Object)",
                "matrix.causal.CausalRecord.Symbol#value()",
                "matrix.causal.CausalRecord.Symbol#<init>(java.lang.String)");
        List<String> violations = new ArrayList<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            List<Path> sourcePaths;
            try (var walk = Files.walk(sourceRoot)) {
                sourcePaths = walk.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.endsWith(
                                Path.of("matrix", "causal", "DeliveryAttempts.java")))
                        .toList();
            }
            sourcePaths = new ArrayList<>(sourcePaths);
            sourcePaths.add(source);
            Iterable<? extends JavaFileObject> units =
                    files.getJavaFileObjectsFromPaths(sourcePaths);
            Path emptyClasspath = Files.createTempDirectory("delivery-attempt-classpath-");
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("--release", "17", "-proc:none", "-classpath",
                            emptyClasspath.toString()), null, units);
            List<com.sun.source.tree.CompilationUnitTree> parsed = new ArrayList<>();
            task.parse().forEach(parsed::add);
            task.analyze();
            Trees trees = Trees.instance(task);
            javax.lang.model.util.Types types = task.getTypes();
            Set<String> seenMapperTypes = new java.util.HashSet<>();
            TreePathScanner<Void, Void> scanner = new TreePathScanner<Void, Void>() {
                private boolean insideMapper;

                @Override public Void visitClass(com.sun.source.tree.ClassTree node,
                        Void unused) {
                    Element element = trees.getElement(getCurrentPath());
                    boolean previous = insideMapper;
                    if (element instanceof TypeElement type) {
                        String name = type.getQualifiedName().toString();
                        boolean owned = name.equals("matrix.causal.DeliveryAttempts")
                                || name.startsWith("matrix.causal.DeliveryAttempts.");
                        insideMapper = owned;
                        if (owned) seenMapperTypes.add(name);
                        if (owned && !Set.of("matrix.causal.DeliveryAttempts",
                                "matrix.causal.DeliveryAttempts.AttemptView").contains(name)) {
                            violations.add("type=" + name);
                        }
                        if (owned) {
                            String superclass = types.erasure(type.getSuperclass()).toString();
                            String expected = name.equals("matrix.causal.DeliveryAttempts")
                                    ? "java.lang.Object" : "java.util.AbstractList";
                            if (!superclass.equals(expected)) {
                                violations.add("superclass=" + name + "->" + superclass);
                            }
                        }
                    }
                    Void result = super.visitClass(node, unused);
                    insideMapper = previous;
                    return result;
                }

                @Override public Void visitMethod(com.sun.source.tree.MethodTree node,
                        Void unused) {
                    if (insideMapper && node.getBody() == null) {
                        violations.add("bodyless=" + node.getName());
                    }
                    return super.visitMethod(node, unused);
                }

                @Override public Void visitIdentifier(
                        com.sun.source.tree.IdentifierTree node, Void unused) {
                    recordField(trees.getElement(getCurrentPath()));
                    return super.visitIdentifier(node, unused);
                }

                @Override public Void visitMemberSelect(
                        com.sun.source.tree.MemberSelectTree node, Void unused) {
                    recordField(trees.getElement(getCurrentPath()));
                    return super.visitMemberSelect(node, unused);
                }

                @Override public Void visitMethodInvocation(MethodInvocationTree node,
                        Void unused) {
                    recordCall(trees.getElement(getCurrentPath()));
                    return super.visitMethodInvocation(node, unused);
                }

                @Override public Void visitNewClass(NewClassTree node, Void unused) {
                    recordCall(trees.getElement(getCurrentPath()));
                    return super.visitNewClass(node, unused);
                }

                @Override public Void visitLambdaExpression(LambdaExpressionTree node,
                        Void unused) {
                    if (insideMapper) violations.add("callback=lambda");
                    return super.visitLambdaExpression(node, unused);
                }

                @Override public Void visitMemberReference(MemberReferenceTree node,
                        Void unused) {
                    if (insideMapper) violations.add("callback=member-reference");
                    return super.visitMemberReference(node, unused);
                }

                private void recordField(Element element) {
                    if (!insideMapper || !(element instanceof VariableElement field)
                            || (!field.getKind().isField()
                            && field.getKind()
                            != javax.lang.model.element.ElementKind.ENUM_CONSTANT)) return;
                    Element owner = field.getEnclosingElement();
                    if (!(owner instanceof TypeElement type)) return;
                    if (field.getSimpleName().contentEquals("this")) return;
                    String key = type.getQualifiedName() + "#" + field.getSimpleName();
                    if (!allowedFields.contains(key)) violations.add("field=" + key);
                }

                private void recordCall(Element element) {
                    if (!insideMapper || !(element instanceof ExecutableElement method)) return;
                    Element owner = method.getEnclosingElement();
                    if (!(owner instanceof TypeElement type)) return;
                    String ownerName = type.getQualifiedName().toString();
                    if (ownerName.equals("matrix.causal.DeliveryAttempts")
                            || ownerName.startsWith("matrix.causal.DeliveryAttempts.")) return;
                    String params = method.getParameters().stream()
                            .map(parameter -> types.erasure(parameter.asType()).toString())
                            .reduce((left, right) -> left + "," + right).orElse("");
                    String key = ownerName + "#" + method.getSimpleName()
                            + "(" + params + ")";
                    if (!allowedCalls.contains(key)) violations.add("call=" + key);
                }
            };
            for (com.sun.source.tree.CompilationUnitTree unit : parsed) {
                scanner.scan(unit, null);
            }
            Set<String> expectedTypes = Set.of("matrix.causal.DeliveryAttempts",
                    "matrix.causal.DeliveryAttempts.AttemptView");
            if (!seenMapperTypes.equals(expectedTypes)) {
                violations.add("types=" + seenMapperTypes);
            }
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                violations.add("compiler=" + diagnostic.getMessage(null));
            }
        }
        return violations;
    }

    private static void rejects(String family, String name, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (RuntimeException expected) { rejected = true; }
        check(family, name, rejected);
    }

    private static void check(String family, String name, boolean held) {
        CASES.merge(family, 1, Integer::sum);
        if (!held) {
            FAILURES.merge(family, 1, Integer::sum);
            BREAKS.add(family + " case=" + name);
        }
    }

    private static int failures(String family) { return FAILURES.getOrDefault(family, 0); }

    private DeliveryAttempts() {}
}
