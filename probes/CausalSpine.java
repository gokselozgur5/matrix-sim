import matrix.Simulation;
import matrix.causal.CausalPhase;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Probe: D-066 has one executable phase order and {@code Simulation} owns it.
 *
 * <p>The production hooks are intentionally empty in #1688. The contract this
 * probe retains is therefore structural rather than theatrical: every normal
 * tick must traverse the complete nine-phase order once, the root must reject
 * an out-of-order or repeated hand-over before it reaches a digest, the
 * completed receipt must be immutable, and no second production class may
 * advance the phase vocabulary.
 *
 * <p>Three tempting false greens are attacked directly through the private
 * root door. Calling delivery before snapshot, calling snapshot twice, and
 * calling observation before digest must each throw the root's ordering
 * refusal. Reflection is confined to this probe; it cannot install a callback
 * or mutate either world. The ordinary path is still exercised through
 * {@link Simulation#tickOnce()} and read only through the public immutable
 * receipt.
 *
 * <p>The source fence is deliberately narrow. It does not claim that a grep
 * can prove all cross-world behavior. It proves the jurisdiction introduced
 * by this unit: production references to {@link CausalPhase} may live only in
 * its vocabulary file and in {@code Simulation}. A future mind reducer may
 * consume a percept record, but it may not become a second phase scheduler.
 * The scanner walks a tiny synthetic tree too, so adding an off-root user is a
 * retained red case rather than an untested sentence.
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
        boolean held = FAILURES.values().stream().mapToInt(Integer::intValue).sum() == 0;
        Probes.leave("VERDICT CAUSAL_SPINE_" + (held ? "HELD" : "BROKEN")
                + " cases=" + CASES.values().stream().mapToInt(Integer::intValue).sum()
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

        Path simulation = root.resolve(Path.of("matrix", "Simulation.java"));
        String code = String.join("\n", Probes.uncommentedLines(simulation));
        int begin = code.indexOf("beginCausalTick();");
        int snapshot = code.indexOf("snapshotTruth();");
        int nodes = code.indexOf("for (SystemNode node : nodes)");
        int digest = code.indexOf("digestCausalState();");
        int digestWalk = code.indexOf("if (t % Config.DIGEST_EVERY_TICKS == 0)");
        int observe = code.indexOf("observeCausalState();");
        int follow = code.indexOf("if (followName != null && t % Config.FOLLOW_EVERY_TICKS == 0)");
        int finish = code.indexOf("finishCausalTick();");
        check("root", "snapshot-before-world-advance",
                ordered(begin, snapshot, nodes));
        check("root", "digest-before-observation",
                ordered(digest, digestWalk, observe, follow, finish));
    }

    /** The source reader proves it can see the off-root shape it forbids. */
    private static void scannerFalsifier() throws IOException {
        Path scratch = Files.createTempDirectory("causal-spine-fence-");
        Path matrix = Files.createDirectories(scratch.resolve("matrix"));
        Path causal = Files.createDirectories(matrix.resolve("causal"));
        Path simulation = matrix.resolve("Simulation.java");
        Path vocabulary = causal.resolve("CausalPhase.java");
        Path foreign = matrix.resolve("Foreign.java");
        try {
            Files.writeString(simulation,
                    "package matrix; import matrix.causal.CausalPhase; class Simulation {}\n");
            Files.writeString(vocabulary,
                    "package matrix.causal; enum CausalPhase { SNAPSHOT_TRUTH }\n");
            Fence clean = inspect(scratch);
            check("root", "fixture-clean", clean.roots() == 1 && clean.offRoots() == 0);

            Files.writeString(foreign,
                    "package matrix; import matrix.causal.CausalPhase; class Foreign {}\n");
            Fence escaped = inspect(scratch);
            check("root", "fixture-off-root-red",
                    escaped.roots() == 1 && escaped.offRoots() == 1);
        } finally {
            Files.deleteIfExists(foreign);
            Files.deleteIfExists(vocabulary);
            Files.deleteIfExists(simulation);
            Files.deleteIfExists(causal);
            Files.deleteIfExists(matrix);
            Files.deleteIfExists(scratch);
        }
    }

    /** Count production files that name the phase vocabulary after comments are stripped. */
    private static Fence inspect(Path root) throws IOException {
        int swept = 0;
        int vocabularies = 0;
        int roots = 0;
        int offRoots = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                swept++;
                String relative = root.relativize(file).toString().replace('\\', '/');
                String code = String.join("\n", Probes.uncommentedLines(file));
                if (!code.contains("CausalPhase")) {
                    continue;
                }
                if (relative.equals("matrix/causal/CausalPhase.java")) {
                    vocabularies++;
                } else if (relative.equals("matrix/Simulation.java")) {
                    roots++;
                } else {
                    offRoots++;
                }
            }
        }
        return new Fence(swept, vocabularies, roots, offRoots);
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
        boolean refused = false;
        try {
            phases.add(CausalPhase.OBSERVE);
        } catch (UnsupportedOperationException expected) {
            refused = true;
        }
        check("order", name, refused);
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

    private record Fence(int swept, int vocabularies, int roots, int offRoots) {}

    private CausalSpine() {}
}
