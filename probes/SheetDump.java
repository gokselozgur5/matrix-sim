import matrix.Simulation;
import matrix.character.Family;
import matrix.character.Sheet;
import matrix.character.Sheets;
import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.Program;
import matrix.realworld.Human;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Probe: what did every soul in this universe derive? (#535)
 *
 * The kernel is imported by nothing in the domain, so until now the only
 * sheets that existed anywhere were the ten legends {@code SheetBench}
 * prints. A city of seven hundred residents derived nothing, and the two
 * properties the phase is audited on — vocabulary discipline and derivation
 * stability — were reviewed by eye. This is the census: one line per soul,
 * for the named cast, for every wing's population, and for the Matrix
 * itself, at an explicit seed, on demand.
 *
 * <h2>The double run is the law, not the courtesy</h2>
 *
 * Every mode builds the universe TWICE, renders the whole census twice, and
 * byte-compares the two renderings before printing either. A census that
 * differs between two runs of the same seed has found a bug in the layer,
 * not in itself — so the compare is what makes this instrument evidence
 * rather than output, and it fails the run rather than printing a footnote.
 * It is a different question from {@code bench.sh --twice}, which compares
 * two PROCESSES (charset, locale, JVM) and would not notice a second
 * universe disagreeing with the first inside one of them.
 *
 * <h2>cached=, measured</h2>
 *
 * The sheets-cached-nowhere law (#350) says base sheets re-derive on read
 * and are stored on nobody. That is a promise until something counts, so
 * the trailer's {@code cached=} is a heap walk: every object reachable from
 * the composition root through {@code matrix.*} fields, arrays, collections
 * and maps, counting the {@link Sheet} instances the domain is holding on
 * to. It is 0 today by construction — the domain does not import the
 * package — and the day a wing adapter caches one, this line says so with
 * a number instead of a reviewer noticing.
 *
 * <h2>What this probe decides, and what it will stop deciding</h2>
 *
 * Which family a resident belongs to, and which string is its identity, are
 * the WING ADAPTERS' calls (#350) — and the adapters do not exist yet.
 * Until they do, this probe decides for them, in one place, out loud:
 * humans derive from {@code Human.name}, programs from {@code Program.purpose}
 * (which is where {@code SmithPrime} keeps "purpose: I decide for myself"),
 * and the Matrix and the Machine City from their own names — the two wings
 * that have no residents to read a name off answer for themselves. When
 * #350 lands, this probe must read the adapters instead, and the census it
 * prints today is the fixture that says whether the adapters agreed with it.
 *
 * Usage:
 *   java -cp out:probes/out SheetDump [selector] [seed] [ticks]
 *
 *   --all                the whole census: the cast, then all four wings (default)
 *   --cast               the named cast, derived
 *   --wing &lt;FAMILY&gt;      one wing's living population
 *   --system             the Matrix's own row — --wing SYSTEM under the name #350 greps
 */
public final class SheetDump {

    /**
     * The Matrix, by the record's spelling. Capitalization is identity: the
     * bytes of this string are the SYSTEM row's fate, so it is quoted here
     * exactly once and never re-typed. Its {@code versionFatigue} is the
     * mixer's number today; #661 makes that axis read {@code World.version()},
     * and this row is where that change becomes visible.
     */
    private static final String THE_MATRIX = "the Matrix";

    /**
     * The Machine City, minted here (#1010) and spelled once and forever.
     * The wing has nobody to derive from — {@code Source} holds a world and
     * a registry, {@code Architect} is {@code enum … INSTANCE},
     * {@code MachineCity} is static methods, {@code SubstrateBudget} is two
     * ints, and not one of them carries an identity string — so the city
     * answers for itself, one row, the way the Matrix answers the SYSTEM
     * row above. Capitalization is identity: these bytes ARE the MACHINE
     * row's fate, and respelling them afterwards re-rolls the sheet
     * attached to them, which is why the string is quoted in exactly one
     * place. If a later verdict gives the wing residents — sentinels,
     * harvesters, as catalog data under D-015 — they APPEND to this wing;
     * they do not replace this row, and this row's numbers do not move.
     */
    private static final String THE_MACHINE_CITY = "the Machine City";

    /** Safety stop on the reachability walk: a census must not become a heap dump. */
    private static final int WALK_CEILING = 5_000_000;

    private enum Mode { ALL, CAST, WING, SYSTEM }

    /** One rendering of the census: the lines, the counts, and what the heap walk found. */
    private record Census(List<String> lines, int souls, int families, Reach reach) {}

    /** What the reachability walk found, printed as its own line so the walk can be judged. */
    private record Reach(int sheets, int objects, int classes, int unreadable, boolean truncated) {}

    /**
     * One wing's answer: the sheets it derived, and how many residents it
     * looked at and did not print. {@code skipped} is on the line because a
     * filtered count that does not say it filtered is how two census
     * instruments come to disagree about the same universe and nobody can
     * see why — {@code NameCensus} counts 196 humans at seed 42 and this
     * wing prints 189 at 6,000 ticks, and the seven are the dead.
     */
    private record Wing(List<Sheet> sheets, int skipped) {}

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();

        Mode mode = Mode.ALL;
        Family wing = null;
        int next = 0;
        if (args.length > 0 && args[0].startsWith("--")) {
            switch (args[0]) {
                case "--all" -> mode = Mode.ALL;
                case "--cast" -> mode = Mode.CAST;
                case "--system" -> mode = Mode.SYSTEM;
                case "--wing" -> {
                    mode = Mode.WING;
                    if (args.length < 2) {
                        System.err.println("--wing needs a family: " + families());
                        System.exit(2);
                    }
                    wing = family(args[1]);
                    next = 1;
                }
                default -> {
                    System.err.println("unknown selector: " + args[0]
                            + " (try --all, --cast, --wing <FAMILY>, --system)");
                    System.exit(2);
                }
            }
            next += 1;
        }
        long seed = args.length > next ? Long.parseLong(args[next]) : 42;
        int ticks = args.length > next + 1 ? Integer.parseInt(args[next + 1]) : 0;

        // Twice, from nothing, before a line is printed: two universes at the
        // same seed, two full renderings, one byte compare.
        Census first = census(mode, wing, seed, ticks);
        Census second = census(mode, wing, seed, ticks);
        int drift = firstDifference(first.lines(), second.lines());

        for (String line : first.lines()) {
            System.out.println(line);
        }
        if (drift >= 0) {
            System.out.println("DRIFT line=" + (drift + 1)
                    + " a=\"" + at(first.lines(), drift) + "\""
                    + " b=\"" + at(second.lines(), drift) + "\"");
        }
        System.out.println(String.format(Locale.ROOT,
                "SHEETDUMP souls=%d families=%d cached=%d double_run=%s",
                first.souls(), first.families(), first.reach().sheets(),
                drift < 0 ? "identical" : "DIFFERS"));
        // Three ways to be red, and a truncated walk is one of them: a
        // cached= printed off a walk that gave up early is a number that
        // means nothing, and printing it green would be the vacuous pass.
        System.exit(drift < 0 && first.reach().sheets() == 0 && !first.reach().truncated() ? 0 : 1);
    }

    /** One whole census, rendered into lines and never printed from here. */
    private static Census census(Mode mode, Family wing, long seed, int ticks) throws Exception {
        Simulation sim = new Simulation(seed, null, null);
        for (int t = 0; t < ticks; t++) {
            sim.tickOnce();
        }
        World world = Probes.world(sim);

        // Every wing is walked whatever the selector prints, because the cast
        // line reports how many legends the city is currently holding and that
        // answer is a fact about all four wings. Walking one to print one and
        // then walking them all to count would be two readings of the same
        // population, which is how two numbers in one output come to disagree.
        Map<Family, Wing> wings = new EnumMap<>(Family.class);
        for (Family family : Family.values()) {
            wings.put(family, wing(family, sim, world));
        }

        List<String> lines = new ArrayList<>();
        List<Sheet> souls = new ArrayList<>();
        lines.add("CENSUS mode=" + label(mode, wing) + " seed=" + seed + " ticks=" + ticks);

        if (mode == Mode.ALL || mode == Mode.CAST) {
            List<Sheet> cast = SheetBench.namedCast();
            lines.add("CAST souls=" + cast.size() + " in_world=" + inWorld(cast, wings));
            emit(lines, souls, cast);
        }
        if (mode == Mode.ALL) {
            for (Family family : Family.values()) {
                wingInto(lines, souls, wings.get(family), family);
            }
        } else if (mode == Mode.WING || mode == Mode.SYSTEM) {
            Family only = mode == Mode.SYSTEM ? Family.SYSTEM : wing;
            wingInto(lines, souls, wings.get(only), only);
        }

        // The law, measured on the universe this census was taken from —
        // after the walk of it, so anything a read could have cached is
        // already cached by the time we look.
        Reach reach = reach(sim);
        lines.add(String.format(Locale.ROOT,
                "CACHED sheets=%d objects=%d classes=%d unreadable=%d truncated=%s",
                reach.sheets(), reach.objects(), reach.classes(), reach.unreadable(),
                reach.truncated() ? "yes" : "no"));

        Set<Family> present = new LinkedHashSet<>();
        for (Sheet sheet : souls) {
            present.add(sheet.family());
        }
        return new Census(lines, souls.size(), present.size(), reach);
    }

    /** One wing, headed by its own count line and followed by its rows. */
    private static void wingInto(List<String> lines, List<Sheet> souls, Wing population, Family family) {
        lines.add("WING " + family + " souls=" + population.sheets().size()
                + " skipped=" + population.skipped());
        emit(lines, souls, population.sheets());
    }

    /**
     * How many of the named cast the city is holding right now, by family and
     * name. It is on the CAST line because the two sections answer two
     * questions — what the legend derives, and what this universe derived —
     * and at 6,000 ticks they overlap: the One is born as
     * {@code Thomas A. Anderson}, so that soul is printed twice and
     * {@code souls=} counts him twice. The number states the overlap instead
     * of the reader having to find it; #479's roster closes it for good by
     * making the cast residents rather than a second list.
     */
    private static int inWorld(List<Sheet> cast, Map<Family, Wing> wings) {
        Set<String> resident = new LinkedHashSet<>();
        for (Wing population : wings.values()) {
            for (Sheet sheet : population.sheets()) {
                resident.add(sheet.family() + "\0" + sheet.name());
            }
        }
        int held = 0;
        for (Sheet sheet : cast) {
            if (resident.contains(sheet.family() + "\0" + sheet.name())) {
                held++;
            }
        }
        return held;
    }

    /**
     * One wing's living population, derived at the census's own door, with
     * the count of residents this wing looked at and did not print.
     *
     * <p>A resident is counted once, on the side it exists on: a mind wearing
     * an avatar is one soul on the real side and not a second one in the
     * city, and a mind wearing SMITH is the same — {@code SmithCopy} is a
     * decorator over the victim's avatar (D-001), so the victim is still the
     * Human it always was and gets the one line it always had.
     *
     * <p>The dead are skipped rather than dropped in silence. The registry
     * keeps the fallen (liberation is not deletion, and neither is dying), so
     * {@code souls} plus {@code skipped} is the whole list the wing walked,
     * and a reader can put this census beside another one — {@code NameCensus}
     * counts the list, this counts the living — without the difference
     * looking like a bug.
     */
    private static Wing wing(Family family, Simulation sim, World world) throws Exception {
        List<Sheet> sheets = new ArrayList<>();
        int skipped = 0;
        switch (family) {
            case HUMAN -> {
                for (Human human : Probes.realWorld(sim).humans()) {
                    if (human.alive()) {
                        sheets.add(Sheets.derive(human.name, Family.HUMAN));
                    } else {
                        skipped++;
                    }
                }
            }
            case PROGRAM -> {
                for (MatrixEntity entity : world.entities()) {
                    if (!(entity instanceof Program program)) {
                        continue;
                    }
                    if (entity.alive) {
                        sheets.add(Sheets.derive(program.purpose, Family.PROGRAM));
                    } else {
                        skipped++;
                    }
                }
            }
            // The wing that never sleeps has no population to walk: the
            // machine side is singletons and statics, and a sheet derives
            // from an identity string that none of them carries. So the city
            // answers for itself. souls=0 was a finding with a keeper, and
            // this is the keeper cashed: one row, so that
            // `power · precision · relentlessness` has somebody to speak it
            // and #659's WING machine souls=<N> stops being 0 by
            // construction.
            case MACHINE -> sheets.add(Sheets.derive(THE_MACHINE_CITY, Family.MACHINE));
            case SYSTEM -> sheets.add(Sheets.derive(THE_MATRIX, Family.SYSTEM));
        }
        return new Wing(sheets, skipped);
    }

    private static void emit(List<String> lines, List<Sheet> souls, List<Sheet> sheets) {
        for (Sheet sheet : sheets) {
            lines.add("SHEET " + sheet.line());
            souls.add(sheet);
        }
    }

    /**
     * Every {@link Sheet} the domain is holding, found by walking what the
     * composition root can reach: {@code matrix.*} instance and static
     * fields, arrays, collections and maps. Objects outside {@code matrix.*}
     * are traversed only through those four shapes and never by reflection,
     * so the walk never asks the JDK to open a field it has closed — the
     * count is a fact about OUR graph, which is the only graph the law is
     * about.
     *
     * <p>What it cannot see, stated rather than implied: a sheet cached in a
     * static field of a class this walk never reaches an instance of, and a
     * sheet held by a thread-local or a weakly-reachable cache. It counts
     * what the root can get to.
     */
    private static Reach reach(Object root) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        Set<Class<?>> walked = new LinkedHashSet<>();
        int sheets = 0;
        int objects = 0;
        int unreadable = 0;
        seen.put(root, Boolean.TRUE);
        queue.add(root);
        while (!queue.isEmpty()) {
            if (objects >= WALK_CEILING) {
                return new Reach(sheets, objects, walked.size(), unreadable, true);
            }
            Object o = queue.poll();
            objects++;
            if (o instanceof Sheet) {
                sheets++;
            }
            if (o instanceof Object[] array) {
                for (Object element : array) {
                    push(seen, queue, element);
                }
                continue;
            }
            Class<?> type = o.getClass();
            if (type.isArray()) {
                continue;
            }
            if (o instanceof Collection<?> collection) {
                for (Object element : collection) {
                    push(seen, queue, element);
                }
                continue;
            }
            if (o instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    push(seen, queue, entry.getKey());
                    push(seen, queue, entry.getValue());
                }
                continue;
            }
            for (Class<?> k = type; ours(k); k = k.getSuperclass()) {
                boolean statics = walked.add(k);
                for (Field field : k.getDeclaredFields()) {
                    if (field.getType().isPrimitive()) {
                        continue;
                    }
                    boolean isStatic = Modifier.isStatic(field.getModifiers());
                    if (isStatic && !statics) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        push(seen, queue, field.get(isStatic ? null : o));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        unreadable++;
                    }
                }
            }
        }
        return new Reach(sheets, objects, walked.size(), unreadable, false);
    }

    private static boolean ours(Class<?> type) {
        return type != null && type != Object.class && type.getName().startsWith("matrix.");
    }

    private static void push(IdentityHashMap<Object, Boolean> seen, ArrayDeque<Object> queue, Object o) {
        if (o != null && seen.put(o, Boolean.TRUE) == null) {
            queue.add(o);
        }
    }

    /** The index of the first line the two renderings disagree on, or -1. */
    private static int firstDifference(List<String> a, List<String> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!at(a, i).equals(at(b, i))) {
                return i;
            }
        }
        return -1;
    }

    private static String at(List<String> lines, int i) {
        return i < lines.size() ? lines.get(i) : "<no line>";
    }

    /**
     * The family a selector word asks for, in any case. Capitalization is
     * identity for a NAME — the bytes of "the Architect" are its fate — but
     * a selector is not a name and never reaches the mixer, so {@code --wing
     * machine} and {@code --wing MACHINE} are the same request. #659 writes
     * the lowercase form and #535 the uppercase one; both resolve, and the
     * row that prints keeps the vocabulary's own spelling either way.
     */
    private static Family family(String word) {
        for (Family family : Family.values()) {
            if (family.name().equalsIgnoreCase(word)) {
                return family;
            }
        }
        // Refused at the door, in the vocabulary's own spelling: a family is
        // one of four words and a typo is not a wing with nobody in it.
        System.err.println("no such family: " + word + " (" + families() + ")");
        System.exit(2);
        return null;
    }

    private static String families() {
        StringBuilder sb = new StringBuilder();
        for (Family family : Family.values()) {
            sb.append(sb.length() == 0 ? "" : " ").append(family);
        }
        return sb.toString();
    }

    private static String label(Mode mode, Family wing) {
        return switch (mode) {
            case ALL -> "all";
            case CAST -> "cast";
            case SYSTEM -> "system";
            case WING -> "wing:" + wing;
        };
    }

    private SheetDump() {}
}
