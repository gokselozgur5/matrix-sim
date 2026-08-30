import matrix.Simulation;
import matrix.causal.CausalRecord;
import matrix.causal.TruthSnapshot;
import matrix.core.World;
import matrix.entities.Pill;
import matrix.realworld.Human;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Probe: does phase one freeze only the declared tick-start truth? (#1690)
 *
 * <p>The runtime half reads the private root through reflection without
 * installing behavior. It proves the normal seed has a complete three-fact
 * group for every eligible resident; reversing the candidate registry and
 * changing a hidden ledger leave the value identical; changing world and
 * real-side state after capture cannot change the object handed to delivery;
 * and making the same change before capture does change the result.
 *
 * <p>The aggregate half attacks the compact door rather than trusting its
 * private arrays. Only ordinal, pill, and coordinates can enter the reusable
 * builder; numeric ordering is independent of insertion order; published
 * arrays cannot be reached or moved by builder reuse; typed entries are a
 * read-only derivation; malformed scalar and builder states are refused. Empty
 * is a first-class value with its tick and rule intact.
 *
 * <p>The source half keeps #1691's door narrow before #1691 exists: the
 * production delivery hook must hand over {@code tickTruth} and its reachable
 * helper graph may not read {@code world} or {@code realWorld}. Direct reads,
 * same-class helpers, accessor aliases, and static or instance external helpers
 * are retained
 * mutants: a green count therefore means the scanner followed each supported
 * escape route, not merely that one method body avoided two field spellings.
 *
 * <p>Usage: {@code java -cp out:probes/out TruthSnapshots [--root src]}
 */
public final class TruthSnapshots {

    private static final List<TruthSnapshot.Predicate> PREDICATES = List.of(
            TruthSnapshot.Predicate.BRAIN_ALIVE,
            TruthSnapshot.Predicate.AVATAR_PILL,
            TruthSnapshot.Predicate.AVATAR_POSITION_CM);

    private static final Map<String, Integer> CASES = new LinkedHashMap<>();
    private static final Map<String, Integer> FAILURES = new LinkedHashMap<>();
    private static final List<String> BREAKS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        Path root = arguments(args);
        if (!Files.isDirectory(root)) {
            System.out.println("FATAL TRUTH_SNAPSHOT_ROOT_UNREADABLE root=" + root);
            System.exit(Probes.Outcome.REFUSED.code());
        }

        aggregateContract();
        Production production = productionContract();
        sourceContract(root);

        for (String broken : BREAKS) {
            System.out.println("TRUTH_SNAPSHOT_BREAK " + broken);
        }
        System.out.println("TRUTH_SNAPSHOT_CENSUS tick=" + production.tick()
                + " subjects=" + production.subjects()
                + " entries=" + production.entries()
                + " facts_per_subject=" + PREDICATES.size());

        int cases = CASES.values().stream().mapToInt(Integer::intValue).sum();
        boolean held = cases > 0
                && FAILURES.values().stream().mapToInt(Integer::intValue).sum() == 0;
        Probes.leave("VERDICT TRUTH_SNAPSHOT_" + (held ? "HELD" : "BROKEN")
                + " cases=" + cases
                + " cases_none=" + (cases == 0 ? 1 : 0)
                + " immutable_fail=" + failures("immutable")
                + " eligibility_fail=" + failures("eligibility")
                + " order_fail=" + failures("order")
                + " freeze_fail=" + failures("freeze")
                + " delivery_live=" + failures("delivery"), held);
    }

    /** The compact aggregate exposes no generic door through which hidden facts fit. */
    private static void aggregateContract() {
        TruthSnapshot empty = TruthSnapshot.empty(7);
        check("eligibility", "empty-explicit",
                empty.tick() == 7 && empty.isEmpty() && empty.subjects() == 0
                        && empty.eligibility()
                        == TruthSnapshot.EligibilityRule.CONNECTED_RESIDENT_SELF_V1);
        check("eligibility", "closed-rule-roster",
                empty.eligibility().predicates().equals(PREDICATES));

        TruthSnapshot.Builder builder = begun();
        builder.add(10, TruthSnapshot.ResidentPill.RED, 30, 40);
        builder.add(2, TruthSnapshot.ResidentPill.BLUE, 10, 20);
        TruthSnapshot snapshot = builder.build(7);
        rejectsListMutation("returned-list-add", snapshot.entries(), true);
        rejectsListMutation("returned-list-set", snapshot.entries(), false);
        rejectsPredicateMutation("rule-list-add", empty.eligibility().predicates(), true);
        rejectsPredicateMutation("rule-list-set", empty.eligibility().predicates(), false);

        List<CausalRecord.TruthEntry> entries = snapshot.entries();
        check("order", "subject-order-is-numeric",
                entries.get(0).subject().key().value().equals("human-2")
                        && entries.get(3).subject().key().value().equals("human-10"));
        boolean dense = true;
        for (int i = 0; i < entries.size(); i++) {
            dense &= entries.get(i).tick() == 7 && entries.get(i).sequence() == i;
        }
        check("order", "tick-and-sequence-dense", dense);
        check("eligibility", "first-group-values",
                fact(entries.get(0)).equals("brain.alive=true")
                        && fact(entries.get(1)).equals("avatar.pill=blue")
                        && fact(entries.get(2)).equals("avatar.position_cm=10,20"));
        check("eligibility", "second-group-values",
                fact(entries.get(3)).equals("brain.alive=true")
                        && fact(entries.get(4)).equals("avatar.pill=red")
                        && fact(entries.get(5)).equals("avatar.position_cm=30,40"));
        check("eligibility", "provenance-closed",
                entries.get(0).provenance().key().value().equals("real-world")
                        && entries.get(1).provenance().key().value().equals("matrix-world")
                        && entries.get(2).provenance().key().value().equals("matrix-world"));

        List<CausalRecord.TruthEntry> before = List.copyOf(entries);
        builder.begin();
        builder.add(2, TruthSnapshot.ResidentPill.RED, 99, 100);
        TruthSnapshot next = builder.build(8);
        check("immutable", "builder-reuse-cannot-move-published-snapshot",
                snapshot.entries().equals(before) && !snapshot.equals(next));
        check("immutable", "equal-values-share-hash",
                snapshot.equals(rebuildSame()) && snapshot.hashCode() == rebuildSame().hashCode());

        TruthSnapshot base = oneSubject(7, 5, TruthSnapshot.ResidentPill.BLUE, 10, 20);
        check("immutable", "equality-separates-tick",
                !base.equals(oneSubject(8, 5, TruthSnapshot.ResidentPill.BLUE, 10, 20)));
        check("immutable", "equality-separates-ordinal",
                !base.equals(oneSubject(7, 6, TruthSnapshot.ResidentPill.BLUE, 10, 20)));
        check("immutable", "equality-separates-pill",
                !base.equals(oneSubject(7, 5, TruthSnapshot.ResidentPill.RED, 10, 20)));
        check("immutable", "equality-separates-x",
                !base.equals(oneSubject(7, 5, TruthSnapshot.ResidentPill.BLUE, 11, 20)));
        check("immutable", "equality-separates-y",
                !base.equals(oneSubject(7, 5, TruthSnapshot.ResidentPill.BLUE, 10, 21)));

        rejectsState("add-before-begin", () -> new TruthSnapshot.Builder().add(
                1, TruthSnapshot.ResidentPill.BLUE, 0, 0));
        rejectsState("build-before-begin", () -> new TruthSnapshot.Builder().build(0));
        TruthSnapshot.Builder alreadyBegun = begun();
        rejectsState("begin-twice", alreadyBegun::begin);
        rejects("eligibility", "negative-ordinal", () -> begun().add(
                -1, TruthSnapshot.ResidentPill.BLUE, 0, 0));
        rejects("eligibility", "negative-x", () -> begun().add(
                1, TruthSnapshot.ResidentPill.BLUE, -1, 0));
        rejects("eligibility", "negative-y", () -> begun().add(
                1, TruthSnapshot.ResidentPill.BLUE, 0, -1));
        rejectsNull("null-pill", () -> begun().add(1, null, 0, 0));

        TruthSnapshot.Builder duplicate = begun();
        duplicate.add(1, TruthSnapshot.ResidentPill.BLUE, 0, 0);
        rejects("order", "duplicate-subject", () -> duplicate.add(
                1, TruthSnapshot.ResidentPill.RED, 1, 1));
        rejects("order", "negative-tick", () -> begun().build(-1));
        TruthSnapshot.Builder largest = begun();
        largest.add(Integer.MAX_VALUE, TruthSnapshot.ResidentPill.BLUE, 0, 0);
        check("order", "max-ordinal-is-value-not-capacity",
                largest.build(0).entries().get(0).subject().key().value()
                        .equals("human-2147483647"));

        TruthSnapshot.Builder closed = begun();
        closed.build(0);
        rejectsState("add-after-build", () -> closed.add(
                1, TruthSnapshot.ResidentPill.BLUE, 0, 0));
        rejectsState("build-twice", () -> closed.build(0));

        boolean constructorsPrivate = true;
        for (Constructor<?> constructor : TruthSnapshot.class.getDeclaredConstructors()) {
            constructorsPrivate &= Modifier.isPrivate(constructor.getModifiers());
        }
        check("eligibility", "no-public-generic-constructor", constructorsPrivate);
        check("eligibility", "builder-constructor-is-sole-no-arg", soleNoArgConstructor());
        check("eligibility", "builder-public-roster-exact", exactBuilderRoster());
        check("immutable", "owned-arrays-are-private-final", ownedArraysPrivateFinal());
    }

    /** Drive the actual root and both named post-capture mutation domains. */
    private static Production productionContract() throws Exception {
        Simulation canonical = new Simulation(42, null, null);
        invoke(canonical, "beginCausalTick");
        invoke(canonical, "snapshotTruth");
        TruthSnapshot frozen = truth(canonical, "tickTruth");
        RealWorld canonicalReal = field(canonical, "realWorld", RealWorld.class);
        World canonicalWorld = field(canonical, "world", World.class);
        check("freeze", "normal-nonempty", !frozen.isEmpty());
        check("freeze", "source-tick", frozen.tick() == 1);
        check("eligibility", "eligible-subject-census-complete",
                frozen.subjects() == eligibleSubjects(canonicalReal, canonicalWorld));
        check("freeze", "complete-groups",
                frozen.entries().size() == frozen.subjects() * PREDICATES.size());

        ineligibleWhen("ineligible-null-link", frozen,
                (human, world) -> clearLink(human));
        ineligibleWhen("ineligible-closed-link", frozen,
                (human, world) -> closeLinkOnly(human));
        ineligibleWhen("ineligible-dead-avatar", frozen,
                (human, world) -> human.link().avatar.alive = false);
        ineligibleWhen("ineligible-world-absent-avatar", frozen,
                (human, world) -> world.entities().remove(human.link().avatar));
        ineligibleWhen("ineligible-dead-brain", frozen,
                (human, world) -> human.brain.flatline());

        Simulation reordered = new Simulation(42, null, null);
        RealWorld reorderedReal = field(reordered, "realWorld", RealWorld.class);
        // The public census is immutable since #1694. This private-universe
        // mutation remains the measured hostile registry permutation.
        @SuppressWarnings("unchecked")
        List<Human> reorderedRegistry = field(reorderedReal, "humans", List.class);
        Collections.reverse(reorderedRegistry);
        invoke(reordered, "beginCausalTick");
        invoke(reordered, "snapshotTruth");
        check("order", "registry-order-independent",
                frozen.equals(truth(reordered, "tickTruth")));

        Simulation ledgered = new Simulation(42, null, null);
        field(ledgered, "world", World.class).ledger().accrue(9_999);
        invoke(ledgered, "beginCausalTick");
        invoke(ledgered, "snapshotTruth");
        check("eligibility", "hidden-ledger-ineligible",
                frozen.equals(truth(ledgered, "tickTruth")));

        List<CausalRecord.TruthEntry> before = List.copyOf(frozen.entries());
        RealWorld real = field(canonical, "realWorld", RealWorld.class);
        Human first = real.humans().get(0);
        NeuralLink link = first.link();
        int oldX = link.avatar.xCm();
        Pill oldPill = link.avatar.pill;
        link.avatar.placeAt(oldX == 0 ? 1 : 0, link.avatar.yCm());
        link.avatar.pill = oldPill == Pill.BLUE ? Pill.RED : Pill.BLUE;
        first.brain.flatline();
        check("freeze", "mutant-really-moved",
                link.avatar.xCm() != oldX && link.avatar.pill != oldPill && !first.alive());

        invoke(canonical, "deliverPercepts");
        TruthSnapshot delivered = truth(canonical, "deliveryTruth");
        check("freeze", "post-capture-world-and-real-mutation-frozen",
                frozen.entries().equals(before));
        check("delivery", "exact-object-handover", delivered == frozen);

        Simulation changedBeforeCapture = new Simulation(42, null, null);
        RealWorld changedReal = field(changedBeforeCapture, "realWorld", RealWorld.class);
        Human changed = changedReal.humans().get(0);
        changed.brain.flatline();
        invoke(changedBeforeCapture, "beginCausalTick");
        invoke(changedBeforeCapture, "snapshotTruth");
        check("freeze", "pre-capture-mutation-is-observable",
                !frozen.equals(truth(changedBeforeCapture, "tickTruth")));

        Simulation ordinary = new Simulation(42, null, null);
        ordinary.tickOnce();
        TruthSnapshot firstTickTruth = truth(ordinary, "tickTruth");
        check("delivery", "ordinary-tick-handover",
                firstTickTruth == truth(ordinary, "deliveryTruth")
                        && firstTickTruth.tick() == 1);
        ordinary.tickOnce();
        TruthSnapshot secondTickTruth = truth(ordinary, "tickTruth");
        check("freeze", "consecutive-source-ticks",
                secondTickTruth.tick() == 2
                        && secondTickTruth == truth(ordinary, "deliveryTruth")
                        && secondTickTruth != firstTickTruth);

        return new Production(frozen.tick(), frozen.subjects(), frozen.entries().size());
    }

    /**
     * Pin the real delivery call graph and retain every known live-read escape.
     *
     * <p>{@link #inspect} starts at {@code Simulation.deliverPercepts}, follows
     * unqualified calls into the same class and class-qualified calls into
     * sibling source files, then counts forbidden world fields over the entire
     * reachable graph. The exact phase-two mapper signature is a jurisdiction
     * transfer to {@code DeliveryAttempts}' complete-source attributed proof;
     * no overload is admitted. Each fixture below changes only the route to the same
     * forbidden read. That makes the fixtures executable evidence for the
     * scanner's reach rather than examples which merely resemble production.
     */
    private static void sourceContract(Path root) throws IOException {
        SourceReading production = inspect(root.resolve(Path.of("matrix", "Simulation.java")));
        check("delivery", "production-source-read", production.swept() == 1);
        check("delivery", "no-live-delivery-read",
                production.liveReads() == 0 && production.callbacks() == 0);
        check("delivery", "one-frozen-handover", production.handovers() == 1);
        Path scratch = Files.createTempDirectory("truth-snapshot-source-");
        Path file = scratch.resolve("Simulation.java");
        Path external = scratch.resolve("DeliveryEscape.java");
        try {
            Files.writeString(file, fixture("deliveryTruth = tickTruth;"));
            SourceReading clean = inspect(file);
            check("delivery", "fixture-clean", clean.liveReads() == 0
                    && clean.handovers() == 1);
            Files.writeString(file, fixture("this.deliveryTruth = this.tickTruth;"));
            SourceReading explicitHandoff = inspect(file);
            check("delivery", "fixture-explicit-this-handoff",
                    explicitHandoff.liveReads() == 0 && explicitHandoff.handovers() == 1);
            Files.writeString(file, fixture("Class<?> type = getClass();"
                    + " deliveryTruth = tickTruth;"));
            SourceReading harmlessClass = inspect(file);
            check("delivery", "fixture-harmless-get-class",
                    harmlessClass.liveReads() == 0 && harmlessClass.callbacks() == 0
                            && harmlessClass.handovers() == 1);

            assertLiveFixture(file, "fixture-direct-live-read-red",
                    fixture("world.entities(); deliveryTruth = tickTruth;"));
            assertLiveFixture(file, "fixture-helper-live-read-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private void leak() { realWorld.humans(); }"));
            assertLiveFixture(file, "fixture-this-helper-live-read-red",
                    fixture("this.leak(); deliveryTruth = tickTruth;",
                            "private void leak() { realWorld.humans(); }"));
            assertLiveFixture(file, "fixture-accessor-alias-red",
                    fixture("liveWorld().entities(); deliveryTruth = tickTruth;",
                            "private World liveWorld() { return world; }"));
            assertLiveFixture(file, "fixture-renamed-field-alias-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private final World live = new World();"
                                    + " private void leak() { live.entities(); }"));
            assertLiveFixture(file, "fixture-cached-live-view-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private final Object liveEntities = world.entities();"
                                    + " private void leak() { liveEntities.toString(); }"));
            assertLiveFixture(file, "fixture-unrelated-field-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private final int unrelated = 1;"
                                    + " private void leak() { System.out.println(unrelated); }"));
            Files.writeString(file, fixture("leak(); deliveryTruth = tickTruth;",
                    "private void leak() {}"
                            + " private void leak(int ignored) { world.entities(); }"));
            SourceReading overload = inspect(file);
            check("delivery", "fixture-uncalled-overload-is-not-reachable",
                    overload.liveReads() == 0 && overload.handovers() == 1);
            Files.writeString(file, fixture("deliveryTruth = tickTruth;",
                    "private void deliverPercepts(int ignored) { world.entities(); }"));
            SourceReading entryOverload = inspect(file);
            check("delivery", "fixture-exact-no-arg-entry",
                    entryOverload.liveReads() == 0 && entryOverload.handovers() == 1);
            assertLiveFixture(file, "fixture-string-brace-helper-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private void leak() { String brace = \"}\"; world.entities(); }"));
            assertLiveFixture(file, "fixture-generic-helper-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private Map<String, Integer> leak() {"
                                    + " world.entities(); return Map.of(); }"));
            assertLiveFixture(file, "fixture-annotated-array-throws-helper-red",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "@Deprecated private Object[] leak() throws Exception {"
                                    + " world.entities(); return new Object[0]; }"));
            Files.writeString(external, "class DeliveryEscape {\n"
                    + "  private static final RealWorld realWorld = new RealWorld();\n"
                    + "  static void leak() { realWorld.humans(); }\n"
                    + "  private final World world = new World();\n"
                    + "  void instanceLeak() { world.entities(); }\n"
                    + "}\n");
            assertLiveFixture(file, "fixture-external-helper-red",
                    fixture("DeliveryEscape.leak(); deliveryTruth = tickTruth;"));
            assertLiveFixture(file, "fixture-instance-helper-red",
                    fixture("escape.instanceLeak(); deliveryTruth = tickTruth;",
                            "private final DeliveryEscape escape = new DeliveryEscape();"));
            Files.writeString(external, "interface Escape { void leak(); }\n"
                    + "final class DeliveryEscape implements Escape {\n"
                    + "  private final World world = new World();\n"
                    + "  public void leak() { world.entities(); }\n"
                    + "}\n");
            assertLiveFixture(file, "fixture-interface-dispatch-red",
                    fixture("escape.leak(); deliveryTruth = tickTruth;",
                            "private final Escape escape = new DeliveryEscape();"));
            assertLiveFixture(file, "fixture-local-interface-dispatch-red",
                    fixture("Escape local = implementation(); local.leak();"
                                    + " deliveryTruth = tickTruth;",
                            "private Escape implementation() { return null; }"));
            Files.writeString(external, "final class ConstructedEscape {\n"
                    + "  private final World world = new World();\n"
                    + "  ConstructedEscape() { world.entities(); }\n"
                    + "}\n");
            assertBoundaryFixture(file, "fixture-source-construction-refused",
                    fixture("Object local = new ConstructedEscape();"
                            + " deliveryTruth = tickTruth;"));
            assertBoundaryFixture(file, "fixture-callback-constructs-refused",
                    fixture("Runnable first = () -> {}; Runnable second = this::noop;"
                                    + " deliveryTruth = tickTruth;",
                            "private void noop() {}"));
            assertBoundaryFixture(file, "fixture-reflective-field-access-refused",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private void leak() throws Exception {"
                                    + " java.lang.reflect.Field found = getClass()"
                                    + ".getDeclaredField(\"world\");"
                                    + " found.setAccessible(true); found.get(this); }"));
            assertBoundaryFixture(file, "fixture-jdk-callback-refused",
                    fixture("java.util.Objects.toString(this);"
                            + " deliveryTruth = tickTruth;"));
            assertCustomCanonicalRefused(scratch);
            assertUnreadableFixture(file, "fixture-malformed-source-refused",
                    fixture("leak(); deliveryTruth = tickTruth;",
                            "private void leak( { world.entities(); }"));
        } finally {
            Files.deleteIfExists(external);
            Files.deleteIfExists(file);
            Files.deleteIfExists(scratch);
        }
    }

    private static String fixture(String deliveryBody) {
        return fixture(deliveryBody, "");
    }

    /** Build one scanner fixture; {@code helpers} remain inside Simulation. */
    private static String fixture(String deliveryBody, String helpers) {
        return "import java.util.Map;\n"
                + "class Simulation {\n"
                + "  private TruthSnapshot tickTruth;\n"
                + "  private TruthSnapshot deliveryTruth;\n"
                + "  private final World world = new World();\n"
                + "  private final RealWorld realWorld = new RealWorld();\n"
                + "  private void deliverPercepts() throws Exception { "
                + deliveryBody + " }\n"
                + "  " + helpers + "\n"
                + "}\n"
                + "class TruthSnapshot {}\n"
                + "class World { Object entities() { return new Object(); } }\n"
                + "class RealWorld { void humans() {} }\n";
    }

    /** Write one mutant and require its reachable forbidden read to be counted. */
    private static void assertLiveFixture(Path file, String name, String source)
            throws IOException {
        Files.writeString(file, source);
        SourceReading escaped = inspect(file);
        check("delivery", name, escaped.liveReads() > 0 && escaped.handovers() == 1);
    }

    /** Retain a deliberately unsupported boundary without calling it a live read. */
    private static void assertBoundaryFixture(Path file, String name, String source)
            throws IOException {
        Files.writeString(file, source);
        SourceReading escaped = inspect(file);
        check("delivery", name, escaped.callbacks() > 0 && escaped.handovers() == 1);
    }

    /** A syntax-broken graph must be refused, never reinterpreted as empty. */
    private static void assertUnreadableFixture(Path file, String name, String source)
            throws IOException {
        Files.writeString(file, source);
        boolean refused = false;
        try {
            inspect(file);
        } catch (IOException expected) {
            refused = true;
        }
        check("delivery", name, refused);
    }

    /** A source-defined List behind CANONICAL must break its declaration seal. */
    private static void assertCustomCanonicalRefused(Path scratch) throws IOException {
        Path matrix = scratch.resolve("matrix");
        Path causal = matrix.resolve("causal");
        Files.createDirectories(causal);
        Path simulation = matrix.resolve("Simulation.java");
        Path phase = causal.resolve("CausalPhase.java");
        try {
            Files.writeString(simulation, "package matrix;\n"
                    + "import java.util.List; import matrix.causal.CausalPhase;\n"
                    + "class Simulation {\n"
                    + " private TruthSnapshot tickTruth; private TruthSnapshot deliveryTruth;"
                    + " private int causalPhaseCursor;\n"
                    + " private void deliverPercepts() { enterCausalPhase("
                    + "CausalPhase.DELIVER_PERCEPTS); deliveryTruth=tickTruth; }\n"
                    + " private void enterCausalPhase(CausalPhase phase) {"
                    + " List<CausalPhase> order=CausalPhase.canonicalOrder();"
                    + " if(causalPhaseCursor>=order.size()"
                    + " || phase!=order.get(causalPhaseCursor))"
                    + " throw new IllegalStateException(\"bad\"); causalPhaseCursor++; } }\n"
                    + "class TruthSnapshot {}\n");
            Files.writeString(phase, "package matrix.causal;\n"
                    + "import java.util.*;\n"
                    + "public enum CausalPhase { DELIVER_PERCEPTS;\n"
                    + " private static final List<CausalPhase> CANONICAL="
                    + " new EscapeList();\n"
                    + " public static List<CausalPhase> canonicalOrder()"
                    + " { return CANONICAL; }\n"
                    + " private static final class EscapeList"
                    + " extends AbstractList<CausalPhase> {"
                    + " private final Object live=new Object();"
                    + " public int size(){ live.toString(); return 1; }"
                    + " public CausalPhase get(int i){ return DELIVER_PERCEPTS; } } }\n");
            boolean refused = false;
            try {
                inspect(simulation);
            } catch (IOException expected) {
                refused = true;
            }
            check("delivery", "fixture-custom-canonical-list-refused", refused);
        } finally {
            Files.deleteIfExists(phase);
            Files.deleteIfExists(simulation);
            Files.deleteIfExists(causal);
            Files.deleteIfExists(matrix);
        }
    }

    /**
     * Read and attribute the source tree containing {@code Simulation}, then
     * walk every invocation which resolves to another supplied source method.
     * JDK/library calls resolve too but have no source-owned facts and stop.
     */
    private static SourceReading inspect(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            return new SourceReading(0, 0, 0);
        }
        SourceGraph graph = sourceMethods(source.getParent());
        Map<MethodKey, MethodFacts> methods = graph.methods();
        List<MethodKey> entries = methods.keySet().stream()
                .filter(key -> key.className().equals("Simulation")
                        || key.className().endsWith(".Simulation"))
                .filter(key -> key.methodName().equals("deliverPercepts"))
                .filter(key -> key.parameters().isEmpty()).toList();
        if (entries.size() != 1) {
            throw new IOException("truth delivery entry must be one exact no-arg method: "
                    + entries);
        }
        MethodKey entry = entries.get(0);
        MethodFacts entryFacts = methods.getOrDefault(entry, MethodFacts.empty());
        Reachable reachable = reachableFacts(entry, methods, new HashSet<>());
        if (!reachable.allowedFields().equals(graph.allowedFields())) {
            throw new IOException("truth field allowlist reach disagrees: expected="
                    + graph.allowedFields() + " reached=" + reachable.allowedFields());
        }
        if (graph.requireAllExternalCalls()
                && !reachable.allowedExternalCalls().equals(graph.allowedExternalCalls())) {
            throw new IOException("truth external-call allowlist reach disagrees: expected="
                    + graph.allowedExternalCalls() + " reached="
                    + reachable.allowedExternalCalls());
        }
        return new SourceReading(1, reachable.liveReads(), entryFacts.handovers(),
                reachable.callbacks());
    }

    /**
     * Ask the JDK 17 compiler to parse and attribute the complete supplied
     * source tree. Attribution binds implicit, explicit-this, static and
     * instance calls to their actual {@link ExecutableElement}; no receiver
     * spelling heuristic remains. Any syntax or semantic diagnostic is refused
     * instead of turning an unresolved project helper into an empty graph.
     */
    private static SourceGraph sourceMethods(Path directory)
            throws IOException {
        List<Path> sources;
        try (var files = Files.walk(directory)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("JDK compiler unavailable for truth source fence");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<MethodKey, MethodFacts> methods = new LinkedHashMap<>();
        Set<FieldKey> allowedFields;
        Set<MethodKey> allowedExternalCalls;
        boolean requireAllExternalCalls;
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("--release", "17", "-proc:none", "-classpath",
                            System.getProperty("java.class.path")), null, units);
            List<CompilationUnitTree> parsed = new ArrayList<>();
            task.parse().forEach(parsed::add);
            task.analyze();
            Trees trees = Trees.instance(task);
            List<ExecutableElement> sourceExecutables = new ArrayList<>();
            List<TypeElement> sourceTypes = new ArrayList<>();
            List<VariableElement> sourceFields = new ArrayList<>();
            MethodElements elementReader = new MethodElements(
                    trees, sourceExecutables, sourceTypes, sourceFields);
            for (CompilationUnitTree unit : parsed) {
                elementReader.scan(unit, null);
            }
            boolean production = sourceTypes.stream().anyMatch(type ->
                    type.getQualifiedName().contentEquals("matrix.Simulation"));
            allowedFields = production ? Set.of(
                    new FieldKey("matrix.Simulation", "tickTruth"),
                    new FieldKey("matrix.Simulation", "deliveryTruth"),
                    new FieldKey("matrix.Simulation", "deliveryAttempts"),
                    new FieldKey("matrix.Simulation", "causalPhaseCursor"),
                    new FieldKey("matrix.causal.CausalPhase", "DELIVER_PERCEPTS"),
                    new FieldKey("matrix.causal.CausalPhase", "CANONICAL")) : Set.of(
                    new FieldKey("Simulation", "tickTruth"),
                    new FieldKey("Simulation", "deliveryTruth"));
            allowedExternalCalls = production ? Set.of(
                    new MethodKey("java.util.List", "size", List.of()),
                    new MethodKey("java.util.List", "get", List.of("int")),
                    new MethodKey("matrix.causal.DeliveryAttempts",
                            "connectedResidentSelfV1",
                            List.of("matrix.causal.TruthSnapshot")),
                    new MethodKey("java.lang.IllegalStateException", "<init>",
                            List.of("java.lang.String"))) : Set.of(
                    new MethodKey("java.lang.Object", "getClass", List.of()));
            requireAllExternalCalls = production;
            Set<FieldKey> resolvedFields = new HashSet<>();
            for (VariableElement field : sourceFields) {
                resolvedFields.add(fieldKey(field));
            }
            if (allowedFields.isEmpty() || !resolvedFields.containsAll(allowedFields)) {
                throw new IOException("truth field allowlist unresolved: allowed="
                        + allowedFields + " resolved=" + resolvedFields);
            }
            if (production) {
                certifyProductionFields(trees, sourceFields);
            }
            Set<MethodKey> sourceMethodKeys = new HashSet<>();
            for (ExecutableElement method : sourceExecutables) {
                sourceMethodKeys.add(SourceFacts.methodKey(method));
            }
            Set<String> sourceTypeNames = new HashSet<>();
            for (TypeElement type : sourceTypes) {
                sourceTypeNames.add(type.getQualifiedName().toString());
            }
            SourceFacts reader = new SourceFacts(methods, trees, task.getElements(),
                    sourceExecutables, sourceMethodKeys, sourceTypeNames, allowedFields,
                    allowedExternalCalls);
            for (CompilationUnitTree unit : parsed) {
                reader.scan(unit, null);
            }
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new IOException("truth source graph unreadable: "
                        + diagnostic.getMessage(null));
            }
        }
        return new SourceGraph(methods, allowedFields, allowedExternalCalls,
                requireAllExternalCalls);
    }

    /**
     * Count forbidden fields in one method and every newly reached helper.
     * {@code visited} terminates recursion and also ensures a shared helper is
     * judged once, independent of how many call paths reach it.
     */
    private static Reachable reachableFacts(MethodKey key, Map<MethodKey, MethodFacts> methods,
            Set<MethodKey> visited) {
        if (!visited.add(key)) {
            return new Reachable(0, 0, Set.of(), Set.of());
        }
        MethodFacts facts = methods.get(key);
        if (facts == null) {
            return new Reachable(0, 0, Set.of(), Set.of());
        }
        int reads = facts.liveReads();
        int callbacks = facts.callbacks();
        Set<FieldKey> allowed = new HashSet<>(facts.allowedFields());
        Set<MethodKey> external = new HashSet<>(facts.allowedExternalCalls());
        for (MethodKey called : facts.calls()) {
            Reachable nested = reachableFacts(called, methods, visited);
            reads += nested.liveReads();
            callbacks += nested.callbacks();
            allowed.addAll(nested.allowedFields());
            external.addAll(nested.allowedExternalCalls());
        }
        return new Reachable(reads, callbacks, allowed, external);
    }

    /** Exact attributed field identity; local variables and parameters have no owner type. */
    private static FieldKey fieldKey(VariableElement field) {
        Element owner = field.getEnclosingElement();
        String className = owner instanceof TypeElement type
                ? type.getQualifiedName().toString() : owner.toString();
        return new FieldKey(className, field.getSimpleName().toString());
    }

    /**
     * Seal the declarations which justify production's field and List-call
     * admissions. In particular CANONICAL is proven to be the JDK List.of
     * value built directly from this enum's values array, so unrelated source
     * List overrides are not possible receivers of its size/get calls.
     */
    private static void certifyProductionFields(Trees trees,
            List<VariableElement> fields) throws IOException {
        Map<FieldKey, VariableElement> byKey = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            byKey.put(fieldKey(field), field);
        }
        requireFieldCertificate(byKey, "matrix.Simulation", "tickTruth",
                "matrix.causal.TruthSnapshot", Set.of(javax.lang.model.element.Modifier.PRIVATE));
        requireFieldCertificate(byKey, "matrix.Simulation", "deliveryTruth",
                "matrix.causal.TruthSnapshot", Set.of(javax.lang.model.element.Modifier.PRIVATE));
        requireFieldCertificate(byKey, "matrix.Simulation", "deliveryAttempts",
                "java.util.List<matrix.causal.CausalRecord.DeliveryAttempt>",
                Set.of(javax.lang.model.element.Modifier.PRIVATE));
        requireFieldCertificate(byKey, "matrix.Simulation", "causalPhaseCursor", "int",
                Set.of(javax.lang.model.element.Modifier.PRIVATE));
        VariableElement phase = byKey.get(new FieldKey(
                "matrix.causal.CausalPhase", "DELIVER_PERCEPTS"));
        if (phase == null || phase.getKind()
                != javax.lang.model.element.ElementKind.ENUM_CONSTANT) {
            throw new IOException("truth phase enum certificate failed");
        }
        VariableElement canonical = requireFieldCertificate(byKey,
                "matrix.causal.CausalPhase", "CANONICAL",
                "java.util.List<matrix.causal.CausalPhase>", Set.of(
                        javax.lang.model.element.Modifier.PRIVATE,
                        javax.lang.model.element.Modifier.STATIC,
                        javax.lang.model.element.Modifier.FINAL));
        TreePath fieldPath = trees.getPath(canonical);
        if (fieldPath == null || !(fieldPath.getLeaf()
                instanceof com.sun.source.tree.VariableTree declaration)
                || !(declaration.getInitializer() instanceof MethodInvocationTree listOf)) {
            throw new IOException("truth CANONICAL initializer certificate failed");
        }
        Element listElement = trees.getElement(new TreePath(fieldPath, listOf));
        if (!(listElement instanceof ExecutableElement listMethod)
                || !SourceFacts.methodKey(listMethod).className().equals("java.util.List")
                || !listMethod.getSimpleName().contentEquals("of")
                || listOf.getArguments().size() != 1
                || !(listOf.getArguments().get(0) instanceof MethodInvocationTree values)) {
            throw new IOException("truth CANONICAL List.of certificate failed");
        }
        Element valuesElement = trees.getElement(new TreePath(
                new TreePath(fieldPath, listOf), values));
        if (!(valuesElement instanceof ExecutableElement valuesMethod)
                || !SourceFacts.methodKey(valuesMethod).className()
                        .equals("matrix.causal.CausalPhase")
                || !valuesMethod.getSimpleName().contentEquals("values")
                || !valuesMethod.getParameters().isEmpty()) {
            throw new IOException("truth CANONICAL values certificate failed");
        }
    }

    private static VariableElement requireFieldCertificate(
            Map<FieldKey, VariableElement> fields, String owner, String name,
            String type, Set<javax.lang.model.element.Modifier> modifiers)
            throws IOException {
        VariableElement field = fields.get(new FieldKey(owner, name));
        if (field == null || !field.asType().toString().equals(type)
                || !field.getModifiers().equals(modifiers)) {
            throw new IOException("truth field declaration certificate failed: "
                    + owner + "." + name);
        }
        return field;
    }

    /** First attributed pass: retain every source method as an override candidate. */
    private static final class MethodElements extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final List<ExecutableElement> methods;
        private final List<TypeElement> types;
        private final List<VariableElement> fields;

        private MethodElements(Trees trees, List<ExecutableElement> methods,
                List<TypeElement> types, List<VariableElement> fields) {
            this.trees = trees;
            this.methods = methods;
            this.types = types;
            this.fields = fields;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type) {
                types.add(type);
            }
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof ExecutableElement executable) {
                methods.add(executable);
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitVariable(com.sun.source.tree.VariableTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof VariableElement variable
                    && (variable.getKind() == javax.lang.model.element.ElementKind.FIELD
                            || variable.getKind()
                                    == javax.lang.model.element.ElementKind.ENUM_CONSTANT)) {
                fields.add(variable);
            }
            return super.visitVariable(node, unused);
        }
    }

    /**
     * One attributed-tree reader. Executable elements supply declaring owners
     * for both method bodies and invocations; a method-local accumulator records
     * identifiers, exact handoff assignments and resolved call edges.
     */
    private static final class SourceFacts extends TreePathScanner<Void, Void> {
        private final Map<MethodKey, MethodFacts> methods;
        private final Trees trees;
        private final Elements elements;
        private final List<ExecutableElement> sourceExecutables;
        private final Set<MethodKey> sourceMethodKeys;
        private final Set<String> sourceTypeNames;
        private final Set<FieldKey> allowedFields;
        private final Set<MethodKey> allowedExternalCalls;
        private MethodFacts facts;

        private SourceFacts(Map<MethodKey, MethodFacts> methods, Trees trees,
                Elements elements, List<ExecutableElement> sourceExecutables,
                Set<MethodKey> sourceMethodKeys, Set<String> sourceTypeNames,
                Set<FieldKey> allowedFields, Set<MethodKey> allowedExternalCalls) {
            this.methods = methods;
            this.trees = trees;
            this.elements = elements;
            this.sourceExecutables = sourceExecutables;
            this.sourceMethodKeys = sourceMethodKeys;
            this.sourceTypeNames = sourceTypeNames;
            this.allowedFields = allowedFields;
            this.allowedExternalCalls = allowedExternalCalls;
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof ExecutableElement executable) || node.getBody() == null) {
                return null;
            }
            MethodFacts outerFacts = facts;
            facts = MethodFacts.empty();
            scan(node.getBody(), unused);
            MethodKey key = methodKey(executable);
            methods.merge(key, facts, MethodFacts::merge);
            facts = outerFacts;
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            if (facts != null) {
                facts = recordField(trees.getElement(getCurrentPath()), facts);
            }
            return super.visitIdentifier(node, unused);
        }

        @Override
        public Void visitMemberSelect(com.sun.source.tree.MemberSelectTree node, Void unused) {
            if (facts != null) {
                facts = recordField(trees.getElement(getCurrentPath()), facts);
            }
            return super.visitMemberSelect(node, unused);
        }

        @Override
        public Void visitAssignment(AssignmentTree node, Void unused) {
            Element target = trees.getElement(new TreePath(
                    getCurrentPath(), node.getVariable()));
            Element source = trees.getElement(new TreePath(
                    getCurrentPath(), node.getExpression()));
            if (facts != null && target instanceof VariableElement targetField
                    && source instanceof VariableElement sourceField
                    && fieldKey(targetField).fieldName().equals("deliveryTruth")
                    && fieldKey(sourceField).fieldName().equals("tickTruth")
                    && fieldKey(targetField).className().equals(
                            fieldKey(sourceField).className())) {
                facts = facts.withHandover();
            }
            return super.visitAssignment(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            if (facts != null) {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof ExecutableElement executable) {
                    MethodKey target = methodKey(executable);
                    boolean sourceTarget = sourceMethodKeys.contains(target);
                    boolean admittedExternal = allowedExternalCalls.contains(target);
                    if (admittedExternal) {
                        facts = facts.withAllowedExternalCall(target);
                    } else if (sourceTarget) {
                        facts = facts.withCall(target);
                        for (ExecutableElement candidate : sourceExecutables) {
                            Element owner = candidate.getEnclosingElement();
                            if (owner instanceof TypeElement candidateOwner
                                    && elements.overrides(
                                            candidate, executable, candidateOwner)) {
                                facts = facts.withCall(methodKey(candidate));
                            }
                        }
                    } else {
                        facts = facts.withCallback();
                    }
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        private MethodFacts recordField(Element element, MethodFacts current) {
            if (!(element instanceof VariableElement field)
                    || (field.getKind() != javax.lang.model.element.ElementKind.FIELD
                            && field.getKind()
                                    != javax.lang.model.element.ElementKind.ENUM_CONSTANT)
                    || field.getSimpleName().contentEquals("this")) {
                return current;
            }
            FieldKey key = fieldKey(field);
            return allowedFields.contains(key)
                    ? current.withAllowedField(key) : current.withLiveRead();
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            if (facts != null) {
                facts = facts.withCallback();
            }
            return super.visitLambdaExpression(node, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree node, Void unused) {
            if (facts != null) {
                facts = facts.withCallback();
            }
            return super.visitMemberReference(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            if (facts != null) {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof ExecutableElement constructor
                        && constructor.getEnclosingElement() instanceof TypeElement owner
                        && sourceTypeNames.contains(owner.getQualifiedName().toString())) {
                    facts = facts.withCallback();
                } else if (element instanceof ExecutableElement constructor
                        && allowedExternalCalls.contains(methodKey(constructor))) {
                    facts = facts.withAllowedExternalCall(methodKey(constructor));
                } else if (element instanceof ExecutableElement) {
                    facts = facts.withCallback();
                }
            }
            return super.visitNewClass(node, unused);
        }

        private static MethodKey methodKey(ExecutableElement method) {
            Element owner = method.getEnclosingElement();
            String className = owner instanceof TypeElement type
                    ? type.getQualifiedName().toString() : owner.toString();
            List<String> parameters = method.getParameters().stream()
                    .map(parameter -> parameter.asType().toString()).toList();
            return new MethodKey(className, method.getSimpleName().toString(), parameters);
        }

    }

    private static TruthSnapshot.Builder begun() {
        TruthSnapshot.Builder builder = new TruthSnapshot.Builder();
        builder.begin();
        return builder;
    }

    /**
     * One fixture, one cause: the named clause alone must drop exactly that
     * resident out of the frozen census and carry no entry for the ordinal.
     */
    private static void ineligibleWhen(String name, TruthSnapshot baseline, Ineligible cause)
            throws Exception {
        Simulation fixture = new Simulation(42, null, null);
        RealWorld real = field(fixture, "realWorld", RealWorld.class);
        World world = field(fixture, "world", World.class);
        Human target = firstEligible(real, world);
        if (target == null) {
            check("eligibility", name, false);
            return;
        }
        cause.apply(target, world);
        TruthSnapshot truth;
        try {
            invoke(fixture, "beginCausalTick");
            invoke(fixture, "snapshotTruth");
            truth = truth(fixture, "tickTruth");
        } catch (Exception thrown) {
            // A root that cannot capture this state has not judged it ineligible:
            // that is this case's failure, reported by name rather than as a crash.
            check("eligibility", name, false);
            return;
        }
        check("eligibility", name, sameExceptSubject(baseline, truth, target.id));
    }

    private static Human firstEligible(RealWorld real, World world) {
        for (Human human : real.humans()) {
            NeuralLink link = human.link();
            if (link != null && !link.closed() && human.alive()
                    && link.avatar.alive && world.isPresent(link.avatar)) {
                return human;
            }
        }
        return null;
    }

    /** The package-private attachment the root reads through {@code link()}. */
    private static void clearLink(Human human) throws Exception {
        Field attachment = Human.class.getDeclaredField("link");
        attachment.setAccessible(true);
        attachment.set(human, null);
    }

    /** Close only the wire, leaving its Human attachment and both bodies intact. */
    private static void closeLinkOnly(Human human) throws Exception {
        Field closed = NeuralLink.class.getDeclaredField("closed");
        closed.setAccessible(true);
        closed.setBoolean(human.link(), true);
    }

    /** Exact canonical baseline remainder, with sequence numbers re-densified. */
    private static boolean sameExceptSubject(TruthSnapshot baseline,
            TruthSnapshot observed, int removedOrdinal) {
        String removed = "human-" + removedOrdinal;
        int observedIndex = 0;
        for (CausalRecord.TruthEntry expected : baseline.entries()) {
            if (expected.subject().key().value().equals(removed)) {
                continue;
            }
            if (observedIndex >= observed.entries().size()) {
                return false;
            }
            CausalRecord.TruthEntry actual = observed.entries().get(observedIndex);
            if (actual.tick() != baseline.tick()
                    || actual.sequence() != observedIndex
                    || !actual.subject().equals(expected.subject())
                    || !actual.fact().equals(expected.fact())
                    || !actual.provenance().equals(expected.provenance())) {
                return false;
            }
            observedIndex++;
        }
        return observed.tick() == baseline.tick()
                && observed.subjects() == baseline.subjects() - 1
                && observedIndex == observed.entries().size();
    }

    private static TruthSnapshot oneSubject(long tick, int ordinal,
            TruthSnapshot.ResidentPill pill, int x, int y) {
        TruthSnapshot.Builder builder = begun();
        builder.add(ordinal, pill, x, y);
        return builder.build(tick);
    }

    private static TruthSnapshot rebuildSame() {
        TruthSnapshot.Builder builder = begun();
        builder.add(10, TruthSnapshot.ResidentPill.RED, 30, 40);
        builder.add(2, TruthSnapshot.ResidentPill.BLUE, 10, 20);
        return builder.build(7);
    }

    private static String fact(CausalRecord.TruthEntry entry) {
        return entry.fact().predicate().value() + "=" + entry.fact().value().text();
    }

    private static boolean soleNoArgConstructor() {
        Constructor<?>[] constructors =
                TruthSnapshot.Builder.class.getDeclaredConstructors();
        return constructors.length == 1
                && Modifier.isPublic(constructors[0].getModifiers())
                && constructors[0].getParameterCount() == 0;
    }

    /**
     * The whole public Builder surface, not only the shape of {@code add}: an
     * added public or protected method is a new door even when every existing
     * door still matches.
     */
    private static boolean exactBuilderRoster() {
        int doors = 0;
        for (Method method : TruthSnapshot.Builder.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers)) {
                continue;
            }
            doors++;
            if (!Modifier.isPublic(modifiers) || !declaredDoor(method)) {
                return false;
            }
        }
        return doors == 3;
    }

    private static boolean declaredDoor(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return switch (method.getName()) {
            case "begin" -> method.getReturnType() == void.class
                    && parameters.length == 0;
            case "add" -> method.getReturnType() == void.class
                    && parameters.length == 4
                    && parameters[0] == int.class
                    && parameters[1] == TruthSnapshot.ResidentPill.class
                    && parameters[2] == int.class
                    && parameters[3] == int.class;
            case "build" -> method.getReturnType() == TruthSnapshot.class
                    && parameters.length == 1
                    && parameters[0] == long.class;
            default -> false;
        };
    }

    private static boolean ownedArraysPrivateFinal() {
        Set<String> owned = Set.of("ordinals", "pills", "xCm", "yCm");
        int seen = 0;
        for (Field field : TruthSnapshot.class.getDeclaredFields()) {
            if (!owned.contains(field.getName())) {
                continue;
            }
            seen++;
            int modifiers = field.getModifiers();
            if (!field.getType().isArray()
                    || !Modifier.isPrivate(modifiers)
                    || !Modifier.isFinal(modifiers)) {
                return false;
            }
        }
        return seen == owned.size();
    }

    private static int eligibleSubjects(RealWorld real, World world) {
        int eligible = 0;
        for (Human human : real.humans()) {
            NeuralLink link = human.link();
            if (link != null && !link.closed() && human.alive()
                    && link.avatar.alive && world.isPresent(link.avatar)) {
                eligible++;
            }
        }
        return eligible;
    }

    private static void rejects(String subject, String name, Throwing action) {
        boolean refused = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            refused = true;
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
        check(subject, name, refused);
    }

    private static void rejectsState(String name, Throwing action) {
        boolean refused = false;
        try {
            action.run();
        } catch (IllegalStateException expected) {
            refused = true;
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
        check("order", name, refused);
    }

    private static void rejectsNull(String name, Throwing action) {
        boolean refused = false;
        try {
            action.run();
        } catch (NullPointerException expected) {
            refused = true;
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
        check("eligibility", name, refused);
    }

    private static void rejectsListMutation(String name,
            List<CausalRecord.TruthEntry> entries, boolean add) {
        boolean refused = false;
        try {
            if (add) {
                entries.add(entries.get(0));
            } else {
                entries.set(0, entries.get(0));
            }
        } catch (UnsupportedOperationException expected) {
            refused = true;
        }
        check("immutable", name, refused);
    }

    private static void rejectsPredicateMutation(String name,
            List<TruthSnapshot.Predicate> predicates, boolean add) {
        boolean refused = false;
        try {
            if (add) {
                predicates.add(TruthSnapshot.Predicate.BRAIN_ALIVE);
            } else {
                predicates.set(0, predicates.get(0));
            }
        } catch (UnsupportedOperationException expected) {
            refused = true;
        }
        check("immutable", name, refused);
    }

    private static void invoke(Simulation simulation, String name) throws Exception {
        Method method = Simulation.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(simulation);
    }

    private static TruthSnapshot truth(Simulation simulation, String name) throws Exception {
        return field(simulation, name, TruthSnapshot.class);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
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

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface Ineligible {
        void apply(Human human, World world) throws Exception;
    }

    private record Production(long tick, int subjects, int entries) {}

    private record SourceReading(int swept, int liveReads, int handovers, int callbacks) {
        private SourceReading(int swept, int liveReads, int handovers) {
            this(swept, liveReads, handovers, 0);
        }
    }

    /** Reachable findings; callback syntax is refused until its target is modeled. */
    private record Reachable(int liveReads, int callbacks, Set<FieldKey> allowedFields,
            Set<MethodKey> allowedExternalCalls) {
        private Reachable {
            allowedFields = Set.copyOf(allowedFields);
            allowedExternalCalls = Set.copyOf(allowedExternalCalls);
        }
    }

    /** Attributed method graph plus the exact non-vacuous field admission set. */
    private record SourceGraph(Map<MethodKey, MethodFacts> methods,
            Set<FieldKey> allowedFields, Set<MethodKey> allowedExternalCalls,
            boolean requireAllExternalCalls) {
        private SourceGraph {
            methods = Map.copyOf(methods);
            allowedFields = Set.copyOf(allowedFields);
            allowedExternalCalls = Set.copyOf(allowedExternalCalls);
        }
    }

    /** Exact attributed method identity; overloads remain separate graph nodes. */
    private record MethodKey(String className, String methodName, List<String> parameters) {
        private MethodKey {
            parameters = List.copyOf(parameters);
        }
    }

    /** Exact attributed field owner and declaration name. */
    private record FieldKey(String className, String fieldName) {}

    /** Immutable syntax facts merged across overloads with no last-body winner. */
    private record MethodFacts(int liveReads, int handovers, int callbacks,
            Set<MethodKey> calls, Set<FieldKey> allowedFields,
            Set<MethodKey> allowedExternalCalls) {
        private MethodFacts {
            calls = Set.copyOf(calls);
            allowedFields = Set.copyOf(allowedFields);
            allowedExternalCalls = Set.copyOf(allowedExternalCalls);
        }

        private static MethodFacts empty() {
            return new MethodFacts(0, 0, 0, Set.of(), Set.of(), Set.of());
        }

        private MethodFacts withLiveRead() {
            return new MethodFacts(liveReads + 1, handovers, callbacks,
                    calls, allowedFields, allowedExternalCalls);
        }

        private MethodFacts withHandover() {
            return new MethodFacts(liveReads, handovers + 1, callbacks,
                    calls, allowedFields, allowedExternalCalls);
        }

        private MethodFacts withCallback() {
            return new MethodFacts(liveReads, handovers, callbacks + 1,
                    calls, allowedFields, allowedExternalCalls);
        }

        private MethodFacts withCall(MethodKey call) {
            Set<MethodKey> joined = new HashSet<>(calls);
            joined.add(call);
            return new MethodFacts(liveReads, handovers, callbacks,
                    joined, allowedFields, allowedExternalCalls);
        }

        private MethodFacts withAllowedField(FieldKey field) {
            Set<FieldKey> joined = new HashSet<>(allowedFields);
            joined.add(field);
            return new MethodFacts(liveReads, handovers, callbacks, calls, joined,
                    allowedExternalCalls);
        }

        private MethodFacts withAllowedExternalCall(MethodKey call) {
            Set<MethodKey> joined = new HashSet<>(allowedExternalCalls);
            joined.add(call);
            return new MethodFacts(liveReads, handovers, callbacks, calls,
                    allowedFields, joined);
        }

        private MethodFacts merge(MethodFacts other) {
            Set<MethodKey> joined = new HashSet<>(calls);
            joined.addAll(other.calls);
            Set<FieldKey> fields = new HashSet<>(allowedFields);
            fields.addAll(other.allowedFields);
            Set<MethodKey> external = new HashSet<>(allowedExternalCalls);
            external.addAll(other.allowedExternalCalls);
            return new MethodFacts(Math.addExact(liveReads, other.liveReads),
                    Math.addExact(handovers, other.handovers),
                    Math.addExact(callbacks, other.callbacks), joined, fields, external);
        }
    }

    private TruthSnapshots() {}
}
