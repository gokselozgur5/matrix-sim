import matrix.Simulation;
import matrix.causal.CausalRecord;
import matrix.causal.TruthSnapshot;
import matrix.core.World;
import matrix.entities.Pill;
import matrix.realworld.Human;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * production delivery hook must hand over {@code tickTruth} and may not name
     * {@code world} or {@code realWorld}. It also drives the same scanner over
     * a private live-read mutant, so a green source count cannot mean the
     * reader forgot its subject.
 *
 * <p>Usage: {@code java -cp out:probes/out TruthSnapshots [--root src]}
 */
public final class TruthSnapshots {

    private static final List<TruthSnapshot.Predicate> PREDICATES = List.of(
            TruthSnapshot.Predicate.BRAIN_ALIVE,
            TruthSnapshot.Predicate.AVATAR_PILL,
            TruthSnapshot.Predicate.AVATAR_POSITION_CM);

    private static final Pattern LIVE_DELIVERY_READ =
            Pattern.compile("\\b(?:world|realWorld)\\b");
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
        Collections.reverse(reorderedReal.humans());
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
        TruthSnapshot ordinaryTruth = truth(ordinary, "tickTruth");
        check("delivery", "ordinary-tick-handover",
                ordinaryTruth == truth(ordinary, "deliveryTruth")
                        && ordinaryTruth.tick() == 1);

        return new Production(frozen.tick(), frozen.subjects(), frozen.entries().size());
    }

    /** Pin the real delivery body and prove the source reader sees a live lookup. */
    private static void sourceContract(Path root) throws IOException {
        SourceReading production = inspect(root.resolve(Path.of("matrix", "Simulation.java")));
        check("delivery", "production-source-read", production.swept() == 1);
        check("delivery", "no-live-delivery-read", production.liveReads() == 0);
        check("delivery", "one-frozen-handover", production.handovers() == 1);
        Path scratch = Files.createTempDirectory("truth-snapshot-source-");
        Path file = scratch.resolve("Simulation.java");
        try {
            Files.writeString(file, fixture("deliveryTruth = tickTruth;"));
            SourceReading clean = inspect(file);
            check("delivery", "fixture-clean", clean.liveReads() == 0
                    && clean.handovers() == 1);

            Files.writeString(file, fixture("world.entities(); deliveryTruth = tickTruth;"));
            SourceReading escaped = inspect(file);
            check("delivery", "fixture-live-read-red", escaped.liveReads() == 1
                    && escaped.handovers() == 1);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(scratch);
        }
    }

    private static String fixture(String deliveryBody) {
        return "class Simulation {\n"
                + "  private void deliverPercepts() { " + deliveryBody + " }\n"
                + "}\n";
    }

    private static SourceReading inspect(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            return new SourceReading(0, 0, 0);
        }
        String code = String.join("\n", Probes.uncommentedLines(source));
        String body = methodBody(code, "private void deliverPercepts()");
        int liveReads = occurrences(LIVE_DELIVERY_READ, body);
        int handovers = occurrences(Pattern.compile(
                "\\bdeliveryTruth\\s*=\\s*tickTruth\\s*;"), body);
        return new SourceReading(1, liveReads, handovers);
    }

    private static String methodBody(String code, String signature) {
        int start = code.indexOf(signature);
        if (start < 0) {
            return "";
        }
        int open = code.indexOf('{', start + signature.length());
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return "";
    }

    private static int occurrences(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
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

    private record SourceReading(int swept, int liveReads, int handovers) {}

    private TruthSnapshots() {}
}
