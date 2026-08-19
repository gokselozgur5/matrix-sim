import matrix.Simulation;
import matrix.causal.CausalRecord;
import matrix.causal.TruthSnapshot;
import matrix.core.World;
import matrix.entities.Pill;
import matrix.realworld.Human;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <p>The constructor half attacks the aggregate rather than trusting Java
 * record syntax. Caller-list mutation, returned-list mutation, incomplete
 * groups, wrong tick/sequence, unknown predicates, wrong provenance, non-Human
 * subjects, duplicate subjects, and unstable group order each have a retained
 * case. Empty is a first-class value with its tick and rule intact.
 *
 * <p>The source half keeps #1691's door narrow before #1691 exists: the
 * production delivery hook must hand over {@code tickTruth} and may not name
 * {@code world} or {@code realWorld}. It also reads the three production
 * predicate constants and drives the same scanner over a private live-read
 * mutant, so a green source count cannot mean the reader forgot its subject.
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
    private static final Pattern PREDICATE_REFERENCE =
            Pattern.compile("TruthSnapshot\\.Predicate\\.([A-Z_]+)");

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

    /** The aggregate rejects every malformed shape instead of normalizing it. */
    private static void aggregateContract() {
        TruthSnapshot empty = TruthSnapshot.empty(7);
        check("eligibility", "empty-explicit",
                empty.tick() == 7 && empty.isEmpty() && empty.subjects() == 0
                        && empty.eligibility()
                        == TruthSnapshot.EligibilityRule.CONNECTED_RESIDENT_SELF_V1);

        CausalRecord.Principal alice = human("human-1");
        List<CausalRecord.TruthEntry> caller = new ArrayList<>(group(7, 0, alice));
        TruthSnapshot snapshot = snapshot(7, caller);
        caller.clear();
        check("immutable", "caller-list-copied", snapshot.entries().size() == PREDICATES.size());
        rejectsListMutation("returned-list-add", snapshot.entries(), true);
        rejectsListMutation("returned-list-set", snapshot.entries(), false);
        rejectsPredicateMutation("rule-list-add", empty.eligibility().predicates(), true);
        rejectsPredicateMutation("rule-list-set", empty.eligibility().predicates(), false);

        List<CausalRecord.TruthEntry> incomplete = new ArrayList<>(group(7, 0, alice));
        incomplete.remove(incomplete.size() - 1);
        rejects("eligibility", "incomplete-group", () -> snapshot(7, incomplete));

        List<CausalRecord.TruthEntry> unknown = new ArrayList<>(group(7, 0, alice));
        CausalRecord.TruthEntry old = unknown.get(0);
        unknown.set(0, new CausalRecord.TruthEntry(7, 0, alice,
                new CausalRecord.Fact(new CausalRecord.Symbol("ledger.balance"),
                        new CausalRecord.Payload("secret")), old.provenance()));
        rejects("eligibility", "hidden-predicate", () -> snapshot(7, unknown));

        List<CausalRecord.TruthEntry> wrongSource = new ArrayList<>(group(7, 0, alice));
        old = wrongSource.get(0);
        wrongSource.set(0, new CausalRecord.TruthEntry(7, 0, alice, old.fact(),
                new CausalRecord.Principal(CausalRecord.PrincipalKind.SYSTEM,
                        "matrix-world")));
        rejects("eligibility", "wrong-provenance", () -> snapshot(7, wrongSource));

        List<CausalRecord.TruthEntry> hiddenValue = new ArrayList<>(group(7, 0, alice));
        old = hiddenValue.get(0);
        hiddenValue.set(0, new CausalRecord.TruthEntry(7, 0, alice,
                new CausalRecord.Fact(TruthSnapshot.Predicate.BRAIN_ALIVE.symbol(),
                        new CausalRecord.Payload("ledger-secret")),
                old.provenance()));
        rejects("eligibility", "hidden-value-under-eligible-name",
                () -> snapshot(7, hiddenValue));

        for (String bad : List.of("BLUE", "unknown")) {
            List<CausalRecord.TruthEntry> wrongPill = new ArrayList<>(group(7, 0, alice));
            old = wrongPill.get(1);
            wrongPill.set(1, new CausalRecord.TruthEntry(7, 1, alice,
                    new CausalRecord.Fact(TruthSnapshot.Predicate.AVATAR_PILL.symbol(),
                            new CausalRecord.Payload(bad)), old.provenance()));
            rejects("eligibility", "pill-value-" + bad,
                    () -> snapshot(7, wrongPill));
        }

        for (String bad : List.of("-1,0", "01,0", "2147483648,0", "x,0",
                "0", "0,1,2")) {
            List<CausalRecord.TruthEntry> wrongCoordinate =
                    new ArrayList<>(group(7, 0, alice));
            old = wrongCoordinate.get(2);
            wrongCoordinate.set(2, new CausalRecord.TruthEntry(7, 2, alice,
                    new CausalRecord.Fact(
                            TruthSnapshot.Predicate.AVATAR_POSITION_CM.symbol(),
                            new CausalRecord.Payload(bad)), old.provenance()));
            rejects("eligibility", "coordinate-value-" + bad,
                    () -> snapshot(7, wrongCoordinate));
        }

        List<CausalRecord.TruthEntry> wrongKind = group(7, 0,
                new CausalRecord.Principal(CausalRecord.PrincipalKind.MACHINE, "machine-a"));
        rejects("eligibility", "non-human-subject", () -> snapshot(7, wrongKind));

        for (String bad : List.of("human-a", "human-01", "human--1",
                "human-2147483648")) {
            List<CausalRecord.TruthEntry> malformedSubject = group(7, 0, human(bad));
            rejects("eligibility", "subject-key-" + bad,
                    () -> snapshot(7, malformedSubject));
        }
        rejects("eligibility", "predicate-factory-refuses-hidden-value",
                () -> TruthSnapshot.Predicate.BRAIN_ALIVE.fact("ledger-secret"));

        List<CausalRecord.TruthEntry> wrongTick = new ArrayList<>(group(7, 0, alice));
        old = wrongTick.get(0);
        wrongTick.set(0, new CausalRecord.TruthEntry(8, 0, alice,
                old.fact(), old.provenance()));
        rejects("order", "entry-tick-mismatch", () -> snapshot(7, wrongTick));

        List<CausalRecord.TruthEntry> sequenceGap = new ArrayList<>(group(7, 0, alice));
        old = sequenceGap.get(2);
        sequenceGap.set(2, new CausalRecord.TruthEntry(7, 9, alice,
                old.fact(), old.provenance()));
        rejects("order", "sequence-gap", () -> snapshot(7, sequenceGap));

        List<CausalRecord.TruthEntry> wrongFactOrder = new ArrayList<>(group(7, 0, alice));
        Collections.swap(wrongFactOrder, 0, 1);
        wrongFactOrder = resequence(7, wrongFactOrder);
        List<CausalRecord.TruthEntry> finalWrongFactOrder = wrongFactOrder;
        rejects("order", "fact-order", () -> snapshot(7, finalWrongFactOrder));

        List<CausalRecord.TruthEntry> duplicate = new ArrayList<>(group(7, 0, alice));
        duplicate.addAll(group(7, PREDICATES.size(), alice));
        rejects("order", "duplicate-subject", () -> snapshot(7, duplicate));

        CausalRecord.Principal bob = human("human-2");
        List<CausalRecord.TruthEntry> descending = new ArrayList<>(group(7, 0, bob));
        descending.addAll(group(7, PREDICATES.size(), alice));
        rejects("order", "subject-order", () -> snapshot(7, descending));

        List<CausalRecord.TruthEntry> numeric = new ArrayList<>(group(7, 0, bob));
        numeric.addAll(group(7, PREDICATES.size(), human("human-10")));
        check("order", "subject-order-is-numeric", snapshot(7, numeric).subjects() == 2);
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

        Simulation reordered = new Simulation(42, null, null);
        RealWorld reorderedReal = field(reordered, "realWorld", RealWorld.class);
        Collections.reverse(reorderedReal.humans());
        World reorderedWorld = field(reordered, "world", World.class);
        reorderedWorld.ledger().accrue(9_999);
        invoke(reordered, "beginCausalTick");
        invoke(reordered, "snapshotTruth");
        check("order", "registry-order-independent",
                frozen.equals(truth(reordered, "tickTruth")));
        check("eligibility", "hidden-ledger-ineligible",
                frozen.equals(truth(reordered, "tickTruth")));

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
        check("eligibility", "closed-production-predicate-roster",
                production.predicates().equals(predicateNames()));

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
        StringBuilder predicates = new StringBuilder();
        for (TruthSnapshot.Predicate predicate : PREDICATES) {
            predicates.append("TruthSnapshot.Predicate.").append(predicate.name()).append(";\n");
        }
        return "class Simulation {\n"
                + "  void predicates() {\n" + predicates + "  }\n"
                + "  private void deliverPercepts() { " + deliveryBody + " }\n"
                + "}\n";
    }

    private static SourceReading inspect(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            return new SourceReading(0, 0, 0, Set.of());
        }
        String code = String.join("\n", Probes.uncommentedLines(source));
        String body = methodBody(code, "private void deliverPercepts()");
        int liveReads = occurrences(LIVE_DELIVERY_READ, body);
        int handovers = occurrences(Pattern.compile(
                "\\bdeliveryTruth\\s*=\\s*tickTruth\\s*;"), body);
        Set<String> predicates = new LinkedHashSet<>();
        Matcher matcher = PREDICATE_REFERENCE.matcher(code);
        while (matcher.find()) {
            predicates.add(matcher.group(1));
        }
        return new SourceReading(1, liveReads, handovers, Set.copyOf(predicates));
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

    private static Set<String> predicateNames() {
        Set<String> names = new LinkedHashSet<>();
        for (TruthSnapshot.Predicate predicate : PREDICATES) {
            names.add(predicate.name());
        }
        return Set.copyOf(names);
    }

    private static TruthSnapshot snapshot(long tick,
            List<CausalRecord.TruthEntry> entries) {
        return new TruthSnapshot(tick,
                TruthSnapshot.EligibilityRule.CONNECTED_RESIDENT_SELF_V1, entries);
    }

    private static List<CausalRecord.TruthEntry> group(long tick, int start,
            CausalRecord.Principal subject) {
        List<CausalRecord.TruthEntry> entries = new ArrayList<>();
        List<String> values = List.of("true", "blue", "10,20");
        for (int i = 0; i < PREDICATES.size(); i++) {
            TruthSnapshot.Predicate predicate = PREDICATES.get(i);
            entries.add(new CausalRecord.TruthEntry(tick, start + i, subject,
                    predicate.fact(values.get(i)), predicate.provenance()));
        }
        return entries;
    }

    private static List<CausalRecord.TruthEntry> resequence(long tick,
            List<CausalRecord.TruthEntry> entries) {
        List<CausalRecord.TruthEntry> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CausalRecord.TruthEntry entry = entries.get(i);
            result.add(new CausalRecord.TruthEntry(tick, i, entry.subject(),
                    entry.fact(), entry.provenance()));
        }
        return result;
    }

    private static CausalRecord.Principal human(String key) {
        return new CausalRecord.Principal(CausalRecord.PrincipalKind.HUMAN, key);
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

    private static void rejectsListMutation(String name,
            List<CausalRecord.TruthEntry> entries, boolean add) {
        boolean refused = false;
        try {
            if (add) {
                entries.add(group(7, entries.size(), human("human-99")).get(0));
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

    private record Production(long tick, int subjects, int entries) {}

    private record SourceReading(int swept, int liveReads, int handovers,
                                 Set<String> predicates) {}

    private TruthSnapshots() {}
}
