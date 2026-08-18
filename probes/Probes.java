import matrix.Simulation;
import matrix.core.World;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * A population as a sorted, comma-joined string, or {@code none} (#1574).
     *
     * <p>Three probes wrote this the same day for the same reason — {@code LeaveContract}
     * and {@code VacuousGuard} in #1550, {@code CatalogFlags} in #1572 — and the argument
     * that kept them apart was about TWO copies: a helper added for two callers is one the
     * next thirty inherit. Three copies written in one afternoon is the sentence #1512
     * settled about the comment strip: <em>written an hour apart for the same bug, which is
     * the argument for one home per language.</em>
     *
     * <p><b>Sorted</b>, so two sweeps of one tree produce byte-identical text. The order a
     * population comes out of the bench table is not information, and an unsorted list
     * would make a diff of two runs report a reordering as a change — which is the whole
     * purpose of these lines: a pinned COUNT cannot show a swap, and a sorted member list
     * can.
     *
     * <p><b>{@code none} rather than an empty field</b>, because a trailing {@code =}
     * followed by nothing reads as a truncated line rather than as an empty set. That is a
     * decision, and a decision copied three times is a decision nobody can change.
     */
    static String joined(List<String> names) {
        if (names.isEmpty()) {
            return "none";
        }
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return String.join(",", sorted);
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


    /**
     * One row of {@code probes/bench.sh}'s table: the verb, the class, and the pinned
     * verdict when there is one (#1590).
     *
     * @param verb    {@code judge}, {@code known} or {@code run}
     * @param probe   the class the row invokes
     * @param verdict the quoted line the bench greps, or empty for a {@code run} row
     */
    record BenchRow(String verb, String probe, String verdict) {

        /** A judged row is one whose verdict the bench greps — `judge` or `known`. */
        boolean judged() {
            return !verdict.isEmpty();
        }
    }

    /**
     * A row read from its LAST verb: `  judge SameTick 'VERDICT SAME_TICK_ABSORB' 6000`.
     *
     * <p>One pattern, one rule (#1598). There were two — one anchored at the start of the
     * line to read the row, and one that found the last verb to strip a joined {@code vary}
     * block's prefix — and nothing asserted they held the same verb list. Adding a fourth
     * verb to one and not the other passed every case: to the reader only, and a
     * {@code vary} decorating it keeps its prefix so the class reads as a word from the
     * reason; to the stripper only, and the row is unmatched and DISAPPEARS. The second is
     * a silent row loss, which is what #1588, #1590, #1594 and #1596 have all been about.
     *
     * <p>Both were answering one question from opposite ends — <em>where does the row
     * start</em> — so the rule is stated once: <b>read from the last verb on the line.</b>
     * The leading {@code .*} is greedy, which is what makes it the last; that lesson has
     * cost two units in two languages (#1588, then #1594 inside its own fix) and is a case
     * here rather than a sentence.
     *
     * <p><b>The skip is bounded by what a row LOOKS like</b>, not by the verb alone. A
     * decorated row whose verdict says `VERDICT X run Beta` has two verbs on the joined
     * line, and the greedy skip took the second — reading the row as `run/Beta/`. The
     * lookahead demands verb + class + quote, which the verdict text does not supply, so
     * the skip stops at the real verb. That case is in the suite; without it this reading
     * would have shipped with the bug it was written to remove.
     */

    /**
     * The verbs a bench row can open with, as data (#1602).
     *
     * <p>The list was in two places for three units — this one, and a {@code Set.of} in
     * {@code CatalogFlags} that decides which verb-shaped line is a stranger — and #1598
     * had just collapsed two into one for exactly the reason two are dangerous: nothing
     * asserts they agree. Add a fifth verb here and not there, and the file-reader reports
     * the READER'S OWN verb as unread — a false accusation about the file, which is the
     * class of defect {@code advice.sh} spent #1341 removing from its own reading.
     *
     * <p>They were never the same list, which is why merging them would have been wrong:
     * the other one is this plus {@code vary}. The MODIFIER is known to the file and not to
     * the reader — it decorates a row rather than being one — and that is precisely why
     * {@code verb_shaped=} and {@code bench_rows=} differ by seven.
     */
    static final List<String> BENCH_VERB_WORDS = List.of("judge", "known", "run");

    /** The same three as an alternation, for the one pattern that reads a row (#1598). */
    private static final String BENCH_VERBS = "(?:" + String.join("|", BENCH_VERB_WORDS) + ")";

    private static final Pattern BENCH_ROW =
            Pattern.compile("^\\s+(?:vary\\b.*\\s(?=" + BENCH_VERBS + "\\s+\\w+\\s+'))?"
                    + "(" + BENCH_VERBS + ")\\s+(\\w+)\\b(?:[^']*'([^']*)')?");



    /**
     * Every row of the bench table, read once (#1590).
     *
     * <p>Four readers parsed this table before this method existed — three probes in Java
     * and {@code counters.sh} in shell — each with its own regex. #1588 found the shell one
     * returning <b>67 of 70</b> rows for the whole life of the file: a {@code vary} block is
     * three lines held together by backslashes, it joins into one whose first token is the
     * modifier, and that reading anchored on the start. It was found by accident, because a
     * Java reader answered the same question and disagreed.
     *
     * <p>The three Java copies agreed with each other, which is weaker evidence than it
     * looks: {@code LeaveContract} and {@code VacuousGuard} were written from one template
     * and {@code CatalogFlags} from theirs, so they agree <em>by descent</em> and not
     * independently. One home per language is #1512's settled rule; this is the Java home.
     *
     * <p><b>A {@code vary} line is skipped and the row it modifies is not.</b> The modifier
     * sits on its own source line and the {@code judge} it decorates on another, so reading
     * line by line takes the row and drops the decoration — which is what all three Java
     * readers already did, correctly, and is stated here because the shell reader's bug was
     * exactly the opposite mistake.
     */
    static List<BenchRow> benchRows(Path bench) throws IOException {
        List<BenchRow> rows = new ArrayList<>();
        // BACKSLASH CONTINUATIONS ARE JOINED FIRST (#1594). `counters.sh` has always
        // joined them; this read line by line, and that was correct for every row in the
        // file only because the sole continued shape is a `vary` block whose `judge` sits
        // on the last line. The row nobody has written yet —
        //
        //     judge SomeProbe \
        //           'VERDICT SPLIT_BEFORE_THE_QUOTE a=0'
        //
        // — reads as a `judge` with no quoted verdict, which `judged()` calls a `run` row:
        // `LeaveContract` moves it to `reporting`, `VacuousGuard` drops it, `CatalogFlags`
        // skips it, and #1590's lane step reports two readers disagreeing on a pull request
        // whose author was writing a long line. A correct check naming the wrong cause is
        // #1170's shape.
        //
        // THE ORDER IS THE TRAP. Joining first makes a `vary` block one line whose FIRST
        // token is `vary`, so skipping that line would drop the row it decorates — which is
        // exactly the bug #1588 found in the shell reader, arriving here by way of the fix.
        // So the `vary` prefix is STRIPPED from a joined line rather than the line being
        // skipped, and the row after it is read from the verb.
        StringBuilder held = new StringBuilder();
        for (String raw : Files.readAllLines(bench, StandardCharsets.UTF_8)) {
            String line = raw;
            if (line.endsWith("\\")) {
                held.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            if (held.length() > 0) {
                line = held + line.trim();
                held.setLength(0);
            }
            Matcher m = BENCH_ROW.matcher(line);
            if (m.find()) {
                rows.add(new BenchRow(m.group(1), m.group(2), m.group(3) == null ? "" : m.group(3)));
            }
        }
        return rows;
    }


    /**
     * A line SHAPED like a bench row: whitespace, a lowercase word, a CamelCase class.
     *
     * <p>Deliberately blind to which word it is (#1600). Every other check in this family
     * reads a READER — {@code benchRows} knows three verbs, {@code counters.sh} knows the
     * same three, and #1590's lane step asserts the two agree. If {@code bench.sh} grows a
     * FOURTH verb, both readers ignore every row that uses it, they agree perfectly about
     * the rows they can see, and the lane is green. Two readers with one blind spot agree.
     */
    private static final Pattern VERB_SHAPED =
            Pattern.compile("^\\s+([a-z][a-z-]*)\\s+[A-Z][A-Za-z0-9]*\\b");

    /**
     * Every line in the bench table that LOOKS like a row, whatever verb it opens with.
     *
     * <p>The mirror of {@link #benchRows}, and the direction nothing had. A verb the
     * readers do not know shows up here the moment it is written, and the gap between this
     * count and theirs is the question: {@code read=70} beside {@code verb_shaped=77} says
     * seven lines look like rows and are not being read.
     *
     * <p>Seven is the right answer today — {@code vary} is a modifier, not a verb, and it
     * decorates a row rather than being one. That is exactly why the two numbers differ,
     * and it is why this returns the WORDS rather than a count: a reader seeing
     * {@code vary} in the gap knows the gap is accounted for, and seeing anything else
     * knows it is not.
     */
    static List<String> verbShaped(Path bench) throws IOException {
        List<String> verbs = new ArrayList<>();
        for (String line : Files.readAllLines(bench, StandardCharsets.UTF_8)) {
            Matcher m = VERB_SHAPED.matcher(line);
            if (m.find()) {
                verbs.add(m.group(1));
            }
        }
        return verbs;
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
