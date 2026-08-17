import matrix.Simulation;
import matrix.core.World;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
     * 0 held · 1 broken · 2 the scenario never arose · 3 the invocation was
     * refused. This sentence said THREE codes, and that a refusal lands on 1,
     * until #1219 read it back against the constant two lines below it — the
     * fourth arrived in #1239 and the paragraph describing the enum did not
     * move. A javadoc contradicting the field under it is the cheapest kind of
     * stale prose to write and the most expensive to read.
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
        NEVER_AROSE(2),
        /**
         * The invocation was refused: a flag with no argument, a scale the
         * config will not accept, a roster too wide to print. Nothing about the
         * world was judged, because the probe never got as far as looking.
         *
         * <p>3 rather than 1 or 2, and the number is borrowed:
         * {@code DreamReader} has spent 3 on {@code EXIT_USAGE} since #1011.
         *
         * <p>It was borrowed from the wrong incumbent (#1219). This javadoc
         * said the two families now agree where they overlap — a script
         * branching on {@code $?} across `probes/` and `tools/` reads one
         * meaning per value — and that was false as written. {@code DreamReader}
         * is one program with a five-code grammar of its own; the shell tools
         * are eleven, and {@code tools/README.md} makes 2 the refusal for all of
         * them and 3 <em>the answer could not be read</em>. So 2 is NEVER_AROSE
         * here and a refusal there, 3 is a refusal here and an unreadable answer
         * there. The split is DECLARED now, in `probes/README.md`'s
         * grammar-boundary marker, and {@code ExitGrammar} fails the sweep if a
         * code the two families spend differently is missing from it — or if one
         * on the list has stopped being split.
         *
         * <p>It was 2 in nine probes, which is the word for NEVER_AROSE: a
         * refused invocation reported as a world with no births (#1239). And it
         * was 1 in {@code BirthInputs}, which said so at the call site while
         * declining to add a fourth code, because that was a decision about the
         * bench's contract rather than a repair. This is that decision.
         */
        REFUSED(3);

        private final int code;

        Outcome(int code) {
            this.code = code;
        }

        /**
         * The number, for the probes that spend it without a verdict line.
         *
         * <p>A refused invocation has nothing to print in the bench's grammar —
         * it never judged anything — so it reaches {@code System.exit} directly
         * rather than through {@code leave}. Naming the code here rather than
         * writing `3` at eight call sites is the whole point: the grammar has
         * one home, and #1204's argument about transposition applies just as
         * well to a literal nobody can search for.
         */
        int code() {
            return code;
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

    /**
     * A source file with its comments removed (#1512).
     *
     * <p>Prose about a thing is not the thing, and this shop has paid for that six times:
     * {@code advice.sh} has matched its own comments (#1157), a neighbour's catalog row
     * (#1222), its own suite fixture (#1265) and its own {@code exit} pattern (#1276);
     * {@code ExitGrammar} read a sentence about {@code System.exit} as an exit (#1503); and
     * {@code DoorRefusal} counted a javadoc quoting D-021's Confirmation as a door (#1510).
     * The last two were an hour apart, by the same hand, because the fix was fresh and the
     * sibling was not checked — which is the argument for one home rather than three.
     *
     * <p>Two implementations and not one: {@code advice.sh} cannot call this and bash cannot
     * be called from here, so the boundary is the language. This is the Java one, and every
     * probe matcher that reads source is expected to go through it.
     *
     * <p>Block comments first, then line comments, and the order matters: a {@code //}
     * inside a block comment belongs to the block, and stripping lines first orphans the
     * block's opener. Line comments go from the marker to the end of the line rather than
     * taking the whole line, because real code can carry a trailing comment.
     *
     * <p>The one case that goes the other way is a {@code //} inside a string literal, which
     * would truncate the rest of that line. It does not occur in {@code probes/}, and its
     * direction is the safe one: it HIDES source from a matcher rather than inventing a
     * finding, so a checker under-reports instead of accusing.
     */
    static String uncommented(Path file) throws IOException {
        return String.join("\n", uncommentedLines(file));
    }

    /**
     * The same rule, LINE-PRESERVING: one entry per source line, comments blanked rather
     * than removed.
     *
     * <p>This is the implementation and {@link #uncommented} is a join of it (#1512, second
     * option). It came from {@code LatticeFence.code}, which needed the line count intact
     * because it reads FIELD DECLARATIONS one line at a time — a strip that deleted a block
     * comment's newlines would join the line before it to the line after, and the joined
     * result is a member declaration that was never written.
     *
     * <p>That is why the two signatures share one body rather than one regex serving both.
     * A regex strip is fine for a {@code contains} or a {@code find}; it is wrong for
     * anything that counts lines or reads them in order, and having both spellings of the
     * rule in one place is what keeps the difference from being rediscovered.
     *
     * <p>It also makes the join SAFER than the regex it replaces: a block comment followed
     * immediately by code on the next line can no longer be spliced into one line.
     */
    static List<String> uncommentedLines(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        boolean inBlock = false;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw;
            if (inBlock) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    out.add("");
                    continue;
                }
                line = line.substring(end + 2);
                inBlock = false;
            }
            int start = line.indexOf("/*");
            if (start >= 0) {
                int end = line.indexOf("*/", start + 2);
                if (end < 0) {
                    inBlock = true;
                    line = line.substring(0, start);
                } else {
                    line = line.substring(0, start) + line.substring(end + 2);
                }
            }
            int slashes = line.indexOf("//");
            if (slashes >= 0) {
                line = line.substring(0, slashes);
            }
            out.add(line);
        }
        return out;
    }

    /**
     * A positional number, or a refusal — never a stack trace (#1481).
     *
     * <p>Eleven probes read a tick count or a seed with a bare
     * {@code Long.parseLong(args[0])}, so an argument that is not a number left
     * through {@code NumberFormatException} and the JVM spent <b>1</b>. In this
     * tree 1 means <i>the claim does not hold</i> — {@code tools/README.md}'s
     * exit grammar and {@code probes/ExitGrammar} both say so — so a mistyped
     * argument reported a broken contract. An operator reading the sweep sees a
     * probe leave 1 and concludes the instrument found the thing it exists to
     * catch. #1170's finding with the labels swapped: a defect report naming the
     * wrong defect, except here there is no defect.
     *
     * <p>Refuses rather than defaults, and that is the whole distinction being
     * drawn: an ABSENT argument is the caller declining to choose, and every
     * caller here already handles that with its own default. A PRESENT argument
     * that is not a number is a mistake, and quietly using the default for it is
     * how {@code CensusSampleSize} priced the wrong question under a green
     * verdict (#1479).
     *
     * <p>One reader rather than eleven guarded parses, because eleven copies of
     * one rule is the shape #1053 was filed about and the eleventh copy is where
     * the twelfth goes.
     *
     * @param arg   the argument as typed, never null
     * @param what  the name this number has in the probe, for the refusal line
     */
    static long number(String arg, String what) {
        try {
            return Long.parseLong(arg);
        } catch (NumberFormatException e) {
            System.err.println("FATAL " + what + " wants a number, not '" + arg + "'");
            System.exit(Outcome.REFUSED.code());
            throw new AssertionError("unreachable");   // System.exit does not return
        }
    }

    /** The same reader where the probe's field is an {@code int}. */
    static int count(String arg, String what) {
        long v = number(arg, what);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            System.err.println("FATAL " + what + " does not fit an int: '" + arg + "'");
            System.exit(Outcome.REFUSED.code());
        }
        return (int) v;
    }

    private static Field open(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private Probes() {}
}
