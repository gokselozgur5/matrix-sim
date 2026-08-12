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
 * <li><b>The two doors.</b> {@link AcceptanceLoop#threshold} (outward) and
 * {@link RealWorld#petitionThreshold} (inward), over six canon names, against
 * pinned values. Both mixes are murmur3 over {@code name.hashCode()}, and they
 * are deliberately decorrelated from each other; a JLS deviation would move
 * both fates at once and this leg names which name moved.</li>
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

    private record Door(String name, long outward, long inward) {}

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
            new Row("rain", 3492756));

    /**
     * Six names the canonical seed-42 run actually liberates, with both door
     * thresholds. Otto Aydin is first deliberately: #373 established he is the
     * one name in 400 whose fate can cross inside a 6,000-tick arc, so if any
     * pinned number in this file is going to matter, it is his.
     */
    private static final List<Door> DOORS = List.of(
            new Door("Otto Aydin", 161L, 88L),
            new Door("Marcus Osei", 149L, 86L),
            new Door("Noor Reyes", 152L, 91L),
            new Door("Dario Novak", 170L, 112L),
            new Door("Thomas Lindqvist", 182L, 62L),
            new Door("Dario Moreau", 174L, 104L));

    public static void main(String[] args) {
        matrix.Streams.utf8();
        List<String> breaks = new ArrayList<>();
        int checked = 0;

        // Leg 1 — the catalog. Pinned rows are matched by id, so a REMOVED or
        // RENAMED species is a break rather than a silently skipped row: the
        // whole point of this leg is that a rename is a digest move.
        Map<String, Species> live = new LinkedHashMap<>();
        for (Species s : Bestiary.ALL) {
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

        // Leg 2 — the two doors. Same input, two deliberately decorrelated
        // mixes; both are pure functions of the name and both reach state.
        for (Door d : DOORS) {
            checked += 2;
            long out = AcceptanceLoop.threshold(d.name());
            long in = RealWorld.petitionThreshold(d.name());
            System.out.printf("SEAL door name=\"%s\" outward=%d inward=%d %s%n", d.name(), out, in,
                    (out == d.outward() && in == d.inward()) ? "OK"
                            : "BREAK want outward=" + d.outward() + " inward=" + d.inward());
            if (out != d.outward()) {
                breaks.add("DOOR outward name=\"" + d.name() + "\" got=" + out + " want=" + d.outward());
            }
            if (in != d.inward()) {
                breaks.add("DOOR inward name=\"" + d.name() + "\" got=" + in + " want=" + d.inward());
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
