import matrix.Simulation;
import matrix.core.World;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Shared reflection openers for the probe bench. Reflection is allowed here
 * by the probe contract: encapsulation protects the domain from the domain,
 * not the coroner from the corpse. Every accessor is read-only.
 */
final class Probes {

    /**
     * Print the verdict and leave with an exit code that agrees with it.
     *
     * <p>`probes/README.md` asks a judged probe for two things — a greppable
     * verdict line AND an honest exit code — and only the first half was ever
     * enforced, because `bench.sh` greps the line and never needed the code.
     * #1091 found the consequence: `DistrictNeutral` printed
     * `DISTRICTS_TOUCHED_THE_STREAM` and exited 0, so the lane was safe and the
     * probe lied to anyone running it by hand — which is what an investigation
     * does. `bench.sh --twice` reads the second run's exit code too, before it
     * compares bytes.
     *
     * <p>One helper rather than nineteen `System.exit` calls, so the contract
     * has one place to be read and one place to change. A probe with no failing
     * verdict — a reporting probe, whose bench row is `run` rather than `judge`
     * — must NOT call this: a `run` row fails on a nonzero exit, so adopting an
     * exit code there changes what the row means (#1093).
     *
     * @param verdict the line the bench greps, printed verbatim
     * @param held    true when the contract this probe judges was kept
     */
    static void leave(String verdict, boolean held) {
        System.out.println(verdict);
        System.exit(held ? 0 : 1);
    }

    /**
     * Print the verdict and leave with one of THREE codes: 0 held, 1 broken,
     * 2 the condition never arose.
     *
     * <p>#1138's question, answered. Three probes print a word that is neither
     * pass nor fail — {@code BIRTH_INPUTS_NONE}, {@code NO_FIRING},
     * {@code NO_LIBERATIONS} — and a two-valued exit forces each of them into a
     * lie. Zero says the contract held, which it did not: it was never tested.
     * One says the contract broke, which it did not either.
     *
     * <p>The tree already had this argument and settled it the same way one
     * directory over: {@code DreamReader} has five codes because #1011 needed
     * "nobody by that name" told apart from "you typed the flag wrong", and
     * #1112 refused a zero-tick day for exactly this reason — a day that never
     * happened must not exit 0. A probe whose scenario never arose is that day.
     *
     * <p>The bench reads the third code as {@code UNEXERCISED}: not a failure,
     * not a pass, and counted so a sweep cannot report a green lane over a
     * scenario no seed produced. That is #970's {@code INSTRUMENTS_UNPROVEN}
     * argument in the other axis — a green report about work that did not occur.
     *
     * @param verdict    the line the bench greps, printed verbatim
     * @param held       true when the contract this probe judges was kept
     * @param exercised  false when the scenario never arose in this run
     */
    /**
     * The three things a probe can report, each carrying the exit code that
     * says it. One argument rather than two booleans, because the two-boolean
     * form was one transposition away from inverting the sweep (#1204):
     * {@code leave(v, false, true)} is "the contract broke" and
     * {@code leave(v, true, false)} is "the scenario never arose" — adjacent,
     * same types, compile identically, and the whole point of the three-valued
     * exit is that a sweep tells those two apart by exit code.
     *
     * <p>The codes are the probe grammar and not this enum's invention:
     * 0 held · 1 broken · 2 the scenario never arose. A refused invocation has
     * no code of its own here and lands on 1 — see {@code BirthInputs}, which
     * says so at its call site rather than pretending otherwise.
     */
    enum Outcome {
        /** The contract this probe judges was kept. */
        HELD(0),
        /** It was exercised and it broke. */
        BROKE(1),
        /**
         * The run never reached the situation the probe judges — no births, no
         * liberations, no firing. Silence is not testimony: a probe that prints
         * a passing verdict here would be certifying a question nobody asked.
         */
        NEVER_AROSE(2);

        private final int code;

        Outcome(int code) {
            this.code = code;
        }
    }

    /** Print the verdict and leave with the code that outcome means. */
    static void leave(String verdict, Outcome outcome) {
        System.out.println(verdict);
        System.exit(outcome.code);
    }

    static RealWorld realWorld(Simulation sim) throws ReflectiveOperationException {
        return (RealWorld) open(Simulation.class, "realWorld").get(sim);
    }

    static World world(Simulation sim) throws ReflectiveOperationException {
        return (World) open(Simulation.class, "world").get(sim);
    }

    @SuppressWarnings("unchecked")
    static List<NeuralLink> links(RealWorld rw) throws ReflectiveOperationException {
        return (List<NeuralLink>) open(RealWorld.class, "links").get(rw);
    }

    /** The near bank of the handoff — freed Humans still waiting for the root's drain (#830's keeper reads it). */
    @SuppressWarnings("unchecked")
    static List<RealWorld.Liberation> pendingLiberations(RealWorld rw) throws ReflectiveOperationException {
        return (List<RealWorld.Liberation>) open(RealWorld.class, "pendingLiberations").get(rw);
    }

    static matrix.machine.Source source(Simulation sim) throws ReflectiveOperationException {
        return (matrix.machine.Source) open(Simulation.class, "source").get(sim);
    }

    static matrix.zion.Zion zion(Simulation sim) throws ReflectiveOperationException {
        return (matrix.zion.Zion) open(Simulation.class, "zion").get(sim);
    }

    /** The wing's render budget — null under BATTERY, where no budget is ever constructed (D-008). */
    static matrix.machine.SubstrateBudget substrate(Simulation sim) throws ReflectiveOperationException {
        return (matrix.machine.SubstrateBudget) open(Simulation.class, "substrate").get(sim);
    }

    /** The inward door's far bank, as the root wired it — the object a hand-built one cannot stand in for (#886). */
    static matrix.machine.DoorPolicy doorPolicy(Simulation sim) throws ReflectiveOperationException {
        return (matrix.machine.DoorPolicy) open(Simulation.class, "doorPolicy").get(sim);
    }

    @SuppressWarnings("unchecked")
    static List<matrix.zion.Hovercraft> fleet(matrix.zion.Zion zion) throws ReflectiveOperationException {
        return (List<matrix.zion.Hovercraft>) open(matrix.zion.Zion.class, "fleet").get(zion);
    }

    @SuppressWarnings("unchecked")
    static List<NeuralLink> rigLinks(matrix.zion.BroadcastRig rig) throws ReflectiveOperationException {
        return (List<NeuralLink>) open(matrix.zion.BroadcastRig.class, "links").get(rig);
    }

    private static Field open(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private Probes() {}
}
