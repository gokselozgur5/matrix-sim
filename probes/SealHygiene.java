import matrix.entities.eco.Bestiary;
import matrix.entities.eco.Species;
import matrix.realworld.AcceptanceLoop;
import matrix.realworld.RealWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Probe: every number the seal borrows from outside this repository, pinned.
 *
 * <p>D-010 owns the bytes of the canonical chain, and the birth-seed ruling's
 * hygiene clause (#212) says the die derives from <i>our own</i> digest mixing,
 * never from anything JVM-shaped. Four call sites read {@code String.hashCode}
 * and all four reach state — one of them inside {@code World.digestEntity},
 * which means {@code String.hashCode} is in the preimage of the canonical sha
 * this phase pins its control group to.
 *
 * <p>#837 ruled that those sites stay (see D-010's Confirmation): {@code
 * String.hashCode} is <b>specification-shaped, not implementation-shaped</b> —
 * the JLS publishes the formula, so it is as portable as UTF-8 byte order, and
 * it is not the thing the hygiene clause was written against. {@code
 * Object.hashCode}, identity hashes and iteration order over an unordered
 * collection are; none of those appear anywhere in {@code src/}.
 *
 * <p>But {@code matrix.character.Sheets} states the distinction that makes the
 * ruling survivable, and it cuts against comfort: FNV-1a over bytes is stable
 * <i>because it is arithmetic we define</i>, while {@code String.hashCode} is
 * stable <i>because a specification says so</i>. A repository whose method is
 * "a rule that cannot be measured is a mood" cannot leave a borrowed
 * specification as the only thing standing between it and a silently different
 * seal. So the borrowing is allowed and it is <b>checked</b>: this probe pins
 * every value the seal actually takes from the JLS, and fails loudly the first
 * time one of them is not what it was.
 *
 * <p>Two legs, because they fail for different reasons and only one of them is
 * about the JVM:
 *
 * <ol>
 * <li><b>The catalog.</b> Every {@link Species#id()} in {@link Bestiary},
 * against its pinned hash. This leg catches a JVM that computes {@code
 * String.hashCode} differently — and, far more likely, it catches a
 * <i>rename</i>. Editing {@code "black cat"} to {@code "stray cat"} looks like
 * a caption change and is a digest move; today nothing in the tree says so.</li>
 * <li><b>The two doors.</b> The borrowed input each door's fate is built on,
 * over six canon births, against pinned values: {@link
 * AcceptanceLoop#birthKey} for the outward door and {@code name.hashCode()}
 * for the inward one. They are deliberately decorrelated from each other; a
 * JLS deviation would move both fates at once and this leg names which birth
 * moved.
 *
 * <p>The outward door stopped being a function of the name at #764 — it reads
 * {@link AcceptanceLoop#birthKey}, which mixes seed, tick, rack unit, growth
 * ordinal and name — so the six rows carry a whole birth each and not just a
 * string. The leg's reason for existing is unchanged and if anything larger:
 * the key still runs on {@code String.hashCode}, now over TWO strings, the
 * rack unit as well as the name.
 *
 * <p>What it pins is the key and the hash, <b>not</b> the thresholds those
 * feed. {@link AcceptanceLoop#threshold} is {@code KID_BASE + floorMod(...)}
 * and {@link RealWorld#petitionThreshold} is {@code PETITION_BASE +
 * floorMod(...)}: both carry a constant this repository sets as an additive
 * term, so pinning them made a lawful {@code KID_*} retune — the thing {@code
 * FateAtlas} exists to measure, and #764 already had to perform once — report
 * as a JLS deviation, in a file whose whole claim is that it watches values
 * from outside this tree. Both thresholds are still <i>printed</i> on every
 * row beside the key that produced them, so a tuning is visible here; it is
 * simply not a hygiene break.</li>
 * </ol>
 *
 * <p>What this probe does <b>not</b> claim: that the sites are the right
 * design. #837 records the argument for retiring them (species ordinal, or
 * FNV-1a of the id) and the reason it is not this unit's move — it is a
 * declared digest move, and the pre-v6 chain is under seal as the NEUTRAL
 * lane's fixture right now. This probe is what makes that deferral safe rather
 * than merely convenient.
 *
 * <p>Usage: {@code java -cp out:probes/out SealHygiene} — no ticks, no world,
 * no seed. It reads constants, so it is the one probe that cannot be flaky.
 */
public final class SealHygiene {

    private record Row(String id, int pinned) {}

    private record Door(String name, String rack, int id, long key, int hash) {}

    /**
     * The Bestiary as the seal sees it. Pinned 2026-08-13 on Temurin 17
     * (Apple Silicon) and re-read on ubuntu-latest by the same run of this
     * probe in CI — a value that agreed on two platforms and one specification.
     */
    private static final List<Row> CATALOG = List.of(
            new Row("sparrow", -2011703348),
            new Row("pigeon", -988363594),
            new Row("crow", 3062423),
            new Row("ant", 96743),
            new Row("bee", 97410),
            new Row("moth", 3357590),
            new Row("rose", 3506511),
            new Row("oak", 109785),
            new Row("ivy", 104684),
            new Row("black cat", 1330742325),
            new Row("stray dog", -410265915),
            new Row("rain", 3492756),
            // A one-off (Bestiary.ONE_OFFS), pinned here for the same reason as
            // every row above it: the seal hashes what the world contains, and
            // the epilogue's sunrise is in the world. Renaming it is a digest
            // move exactly as renaming "rain" is.
            new Row("sunrise", -1856560363));

    /**
     * The universe and the clock the six pinned births share. Literals, not a
     * boot: this probe reads constants and nothing else, which is why it is
     * the one probe that cannot be flaky. Any fixed pair would do — these two
     * are the canonical seed and the world's first tick, so a reader can see
     * at a glance that no run produced them.
     */
    private static final long DOOR_SEED = 42, DOOR_TICK = 0;

    /**
     * Six canon names, each given a whole birth, with the borrowed value each
     * door reads: the birth key for the outward door, the name's hash for the
     * inward one. Otto Aydin is first for the history: while fate keyed to the
     * name he was the one string in 400 whose bar could be cleared inside a
     * 6,000-tick arc, and #764 is the unit that ended that. His row is now
     * five pinned numbers like any other — which is the change, stated as a
     * table.
     *
     * <p>The rack units and ordinals are the farm's own first six slots. They
     * are inputs to the outward mix and therefore load-bearing: editing one is
     * a different birth and a different key, exactly as editing a name is.
     */
    private static final List<Door> DOORS = List.of(
            new Door("Otto Aydin", "R01/U01", 0, 5339372980178437441L, -839922607),
            new Door("Marcus Osei", "R01/U02", 1, 1902699839737590696L, 2122998629),
            new Door("Noor Reyes", "R01/U03", 2, 7774114967339331165L, -1937520616),
            new Door("Dario Novak", "R01/U04", 3, 7387284114210322740L, -380217190),
            new Door("Thomas Lindqvist", "R01/U05", 4, -2832801419990887202L, 1675382294),
            new Door("Dario Moreau", "R01/U06", 5, 4336398516626685796L, 1069424334));

    public static void main(String[] args) {
        matrix.Streams.utf8();
        List<String> breaks = new ArrayList<>();
        int checked = 0;

        // Leg 1 — the catalog. Pinned rows are matched by id, so a REMOVED or
        // RENAMED species is a break rather than a silently skipped row: the
        // whole point of this leg is that a rename is a digest move.
        // EVERY, not CATALOG. #974 split the two — CATALOG is what the seeding
        // loop walks, EVERY is what the world can CONTAIN — and this leg asks a
        // question about containment: `World.digestEntity` feeds
        // `p.species.id().hashCode()` into the seal for whatever is in the world,
        // and it cannot tell which list a row was written on. Reading CATALOG
        // walked twelve rows while the seal hashed thirteen, so the sunrise
        // (#974's one-off) was a digest input nothing checked — this probe was
        // checking the wrong list against the right hash (#1111).
        Map<String, Species> live = new LinkedHashMap<>();
        for (Species s : Bestiary.EVERY) {
            live.put(s.id(), s);
        }
        for (Row r : CATALOG) {
            checked++;
            Species s = live.remove(r.id());
            if (s == null) {
                breaks.add("CATALOG id=\"" + r.id() + "\" MISSING from Bestiary (a rename is a digest move)");
                continue;
            }
            int actual = s.id().hashCode();
            System.out.printf("SEAL catalog id=\"%s\" hash=%d %s%n", r.id(), actual,
                    actual == r.pinned ? "OK" : "BREAK want=" + r.pinned);
            if (actual != r.pinned) {
                breaks.add("CATALOG id=\"" + r.id() + "\" hash=" + actual + " want=" + r.pinned);
            }
        }
        // Anything left in `live` is a species the seal now hashes and this
        // file has never seen. Unpinned is not the same as unchanged.
        for (String extra : live.keySet()) {
            checked++;
            System.out.printf("SEAL catalog id=\"%s\" hash=%d BREAK unpinned%n", extra, extra.hashCode());
            breaks.add("CATALOG id=\"" + extra + "\" is in Bestiary and not pinned here");
        }

        // Leg 2 — the two doors. Two deliberately decorrelated mixes, both
        // pure and both reaching state. They no longer take the same input:
        // since #764 the outward door reads the whole birth and the inward one
        // still reads the name, so a JLS deviation in String.hashCode moves
        // both — the outward one through the rack unit and the name at once.
        //
        // Judged: the birth key and the name's hash, which are the JLS plus
        // arithmetic this file's subject owns. Printed and NOT judged: the two
        // thresholds, which are those values plus KID_BASE and PETITION_BASE.
        // A retune of either constant moves the printed number and holds the
        // lock, because a constant this repository sets is not something the
        // seal borrowed.
        for (Door d : DOORS) {
            checked += 2;
            long key = AcceptanceLoop.birthKey(DOOR_SEED, DOOR_TICK, d.rack(), d.id(), d.name());
            int hash = d.name().hashCode();
            System.out.printf("SEAL door name=\"%s\" rack=\"%s\" id=%d key=%d hash=%d outward=%d inward=%d %s%n",
                    d.name(), d.rack(), d.id(), key, hash,
                    AcceptanceLoop.threshold(key), RealWorld.petitionThreshold(d.name()),
                    (key == d.key() && hash == d.hash()) ? "OK"
                            : "BREAK want key=" + d.key() + " hash=" + d.hash());
            if (key != d.key()) {
                breaks.add("DOOR outward name=\"" + d.name() + "\" key=" + key + " want=" + d.key());
            }
            if (hash != d.hash()) {
                breaks.add("DOOR inward name=\"" + d.name() + "\" hash=" + hash + " want=" + d.hash());
            }
        }

        for (String b : breaks) {
            System.out.println("BREAK " + b);
        }
        // The denominator rides the verdict: a run that pinned nothing must not
        // be able to report that nothing moved.
        System.out.printf("VERDICT %s sites=2 checked=%d breaks=%d%n",
                breaks.isEmpty() ? "SEAL_HYGIENE_HELD" : "SEAL_HYGIENE_BROKEN", checked, breaks.size());
    }

    private SealHygiene() {}
}
