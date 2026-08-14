import matrix.Simulation;
import matrix.core.Config;
import matrix.core.District;
import matrix.core.PlaceGraph;
import matrix.core.Rng;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: naming a city must not cost the world a single die roll.
 *
 * The district catalog (D-048, #289) is derived from zone names by our own
 * mixer. The claim that it is STREAM-NEUTRAL — zero draws off the one
 * seeded stream, so every position, every pill and every fate lands exactly
 * where it landed before the city had names — is the kind of claim that is
 * true the day it is written and false three units later, when someone
 * makes a quarter's character depend on who lives in it.
 *
 * <p>Five legs, because each alone is refutable:
 *
 * <ol>
 * <li><b>Seed independence.</b> The catalog is read out of four live
 * universes with four different seeds. If naming ever consulted the
 * stream, two universes would name their quarters differently — the
 * loudest possible symptom, and the one a digest alone would never
 * name.</li>
 * <li><b>Construction neutrality.</b> A stream's draw counter is read
 * across a hundred {@link PlaceGraph} constructions. Any draw, direct or
 * transitive, moves the counter.</li>
 * <li><b>Order independence.</b> A catalog built after the stream has
 * been burned down is the same catalog. A mixer that had quietly grown a
 * dependency on stream POSITION rather than on the seed would pass leg
 * two and fail here.</li>
 * <li><b>The boot print.</b> A universe is booted with its stream
 * captured, and the {@code DISTRICT} rows it printed are held to the
 * catalog they claim to quote — same rows, same order — then booted again
 * on another seed for the same rows. Finally the boot draw total is read
 * with the sink attached and with it detached: printing must cost what
 * every other narrative line costs, which is nothing. Legs one to three
 * guard the catalog; a catalog nobody can read is not what #539
 * shipped. The row comparison is the part of this leg that can fail —
 * the two boots differ only in whether an {@link matrix.core.EventLog}
 * is attached, so {@code BOOTDRAWS} catches a print path that draws and
 * nothing wider than that, which is #950's subject.</li>
 * <li><b>The pin.</b> The six names, against literals held in this file.
 * Legs one to four compare the city to ITSELF — four universes out of one
 * binary in one process, and a boot print held to the catalog it quotes —
 * so a change that renames every quarter identically moves both sides of
 * every comparison together and passes all four, green, exit zero (#944).
 * The seal cannot answer for the names either: a district name is not
 * state the digest frames, so renaming the city is byte-identical at
 * {@code DIGEST tick=6000}. An expectation recorded OUTSIDE the run is the
 * only thing that can say no, which is what #899 gave the sha when it put
 * it in {@code .github/canonical-digest}. This leg is that, for the
 * city.</li>
 * </ol>
 *
 * <p>Deliberately NOT a pinned boot-draw count. The boot total moves the
 * day any unit anywhere adds a seeded decision at birth — the commuter
 * address book (#290) does exactly that, by declaration — and a probe that
 * fails on someone else's lawful move teaches its readers to ignore it.
 * What is pinned here is the district catalog's own relationship to the
 * stream: none. Leg four reads the boot total TWICE in one process and
 * compares the two, so it measures the print and never the era.
 *
 * <p>Nor is the row COUNT pinned as a number. The city has six quarters
 * because the zone list has six entries, and D-048's open point (c) leaves
 * redistricting on the table; a probe asserting six would fail the day the
 * Architect takes it. Six is checked by the unit's own command in #539;
 * what is checked here is that whatever the catalog holds is what boot
 * prints. Leg five pins the NAMING and not the census: the table is keyed
 * by the zone each quarter binds, so a redistricting goes red on the new
 * quarter alone, by name, and the mover writes that quarter into the table
 * in the same commit. That edit IS the declaration, in the shape #899 gave
 * the seal — a lawful move states the new name, and an unlawful one is this
 * leg going red on a tree nobody meant to rename.
 *
 * Usage: java -cp out:probes/out DistrictNeutral [ticks-ignored]
 */
public final class DistrictNeutral {

    private static final long[] SEEDS = {42, 7, 1, 55};
    /** The token every catalog row opens with — the word this unit's own DoD greps for. */
    private static final String TOKEN = "DISTRICT ";
    private static final int CONSTRUCTIONS = 100;
    private static final int BURN = 1_000;

    /**
     * What the city is called, zone by zone, as LITERALS — the expectation
     * leg five verdicts against. Written here rather than read out of
     * {@link District}, because a catalog cannot vouch for itself: the
     * mixer, the pools and the four universes are all downstream of the
     * same salts, and the row this table is checking is the row that would
     * move (HullRoster holds the same precedent for the film's keels).
     *
     * <p>The name is pinned and the character columns are not. The names
     * are what #290's address book, #291's instrument lines and #703's
     * scene WHERE put into shipped prose, so a silent rename is a silent
     * rewrite of every line that names a place; the axes are lore that
     * nothing mechanical reads yet, and the day one does is the day they
     * earn a pin of their own.
     */
    private static final String[][] PINNED = {
            {"downtown", "Felix Sato"},
            {"industrial district", "Vera Okafor"},
            {"chinatown", "Milo Petrov"},
            {"financial district", "Ezra Petrov"},
            {"old city", "Noor Iglesias"},
            {"the loop", "Marcus Frost"},
    };

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        List<String> faults = new ArrayList<>();

        // Leg 1: four universes, one city.
        List<String> reference = null;
        List<District> catalog = null;
        for (long seed : SEEDS) {
            Simulation sim = new Simulation(seed, null, null);
            PlaceGraph places = Probes.world(sim).places();
            List<String> rows = rows(places);
            if (reference == null) {
                reference = rows;
                catalog = places.districts();
                for (String row : rows) {
                    System.out.println("CATALOG " + row);
                }
            } else if (!reference.equals(rows)) {
                faults.add("seed " + seed + " named the city differently");
            }
            System.out.println("SEED " + seed + " districts=" + rows.size()
                    + " matches_reference=" + reference.equals(rows));
        }

        // Leg 2: a hundred constructions cost the stream nothing.
        Rng rng = new Rng(42);
        long before = rng.draws();
        for (int i = 0; i < CONSTRUCTIONS; i++) {
            new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM);
        }
        long spent = rng.draws() - before;
        System.out.println("DRAWS constructions=" + CONSTRUCTIONS + " spent=" + spent);
        if (spent != 0) {
            faults.add("building the catalog spent " + spent + " draws");
        }

        // Leg 3: the same city, built out of a burned-down stream.
        for (int i = 0; i < BURN; i++) {
            rng.nextInt(1_000);
        }
        List<String> afterBurn = rows(new PlaceGraph(Config.WORLD_W_CM, Config.WORLD_H_CM));
        System.out.println("BURNED draws=" + rng.draws()
                + " matches_reference=" + afterBurn.equals(reference));
        if (!afterBurn.equals(reference)) {
            faults.add("a catalog built after " + BURN + " draws is a different city");
        }

        // Leg 4: what boot printed is what the catalog holds, and printing it
        // cost the stream nothing.
        List<String> printed = bootRows(SEEDS[0]);
        System.out.println("BOOT seed=" + SEEDS[0] + " rows=" + printed.size()
                + " matches_catalog=" + printed.equals(reference));
        if (!printed.equals(reference)) {
            faults.add("the boot print is not the catalog it quotes");
        }
        List<String> printedElsewhere = bootRows(SEEDS[1]);
        System.out.println("BOOT seed=" + SEEDS[1] + " rows=" + printedElsewhere.size()
                + " matches_first=" + printedElsewhere.equals(printed));
        if (!printedElsewhere.equals(printed)) {
            faults.add("seed " + SEEDS[1] + " printed a different city at boot");
        }
        long lit = bootDraws(new ByteArrayOutputStream());
        long dark = bootDraws(null);
        System.out.println("BOOTDRAWS printed=" + lit + " silent=" + dark + " cost=" + (lit - dark));
        if (lit != dark) {
            faults.add("printing the catalog at boot cost " + (lit - dark) + " draws");
        }

        // Leg 5: the city against the names this file holds for it.
        int drifted = pinned(catalog);

        for (String fault : faults) {
            System.out.println("FAULT " + fault);
        }
        // Drift takes the verdict line and the exit code: a renamed city is
        // not a stream touch, and calling it one would put the wrong words on
        // the loudest line the probe prints. The FAULT rows above are printed
        // either way, so nothing a stream leg found is swallowed by a rename.
        if (drifted > 0) {
            System.out.println("VERDICT CATALOG_DRIFTED drifted=" + drifted);
            System.exit(1);
        }
        System.out.println(faults.isEmpty()
                ? "VERDICT DISTRICTS_DRAW_NOTHING"
                : "VERDICT DISTRICTS_TOUCHED_THE_STREAM faults=" + faults.size());
    }

    /**
     * The catalog against {@link #PINNED}, quarter by quarter, keyed by the
     * zone each one binds rather than by position — position is not
     * identity, and the question this leg asks is what a quarter is CALLED,
     * so every drift line can name the place it is talking about.
     *
     * <p>Three ways to drift, all counted and all named on their own line:
     * a pinned zone wearing a different name, a pinned zone the catalog no
     * longer has, and a quarter the table has never heard of. The third is
     * drift on SealHygiene's reasoning — unpinned is not the same as
     * unchanged, and a name nothing holds is a name that can move on a
     * Tuesday — so a redistricting names its new quarter here rather than
     * arriving unwatched.
     *
     * @return how many quarters no longer read as pinned
     */
    private static int pinned(List<District> catalog) {
        int drifted = 0;
        for (District d : catalog) {
            String want = pinnedName(d.zoneName());
            if (want == null) {
                drifted++;
                System.out.println("CATALOG DRIFT zone=\"" + d.zoneName() + "\" name=\""
                        + d.name() + "\" pinned=- fault=unpinned_quarter");
            } else if (!want.equals(d.name())) {
                drifted++;
                System.out.println("CATALOG DRIFT zone=\"" + d.zoneName() + "\" name=\""
                        + d.name() + "\" pinned=\"" + want + "\" fault=renamed");
            }
        }
        for (String[] row : PINNED) {
            if (!bindsZone(catalog, row[0])) {
                drifted++;
                System.out.println("CATALOG DRIFT zone=\"" + row[0] + "\" name=- pinned=\""
                        + row[1] + "\" fault=quarter_gone");
            }
        }
        System.out.println("CATALOG PINNED names=" + PINNED.length + " drifted=" + drifted);
        return drifted;
    }

    /** The name this file holds for one zone, or null when the table has none. */
    private static String pinnedName(String zone) {
        for (String[] row : PINNED) {
            if (row[0].equals(zone)) {
                return row[1];
            }
        }
        return null;
    }

    /** Whether the catalog still has a quarter bound to one zone. */
    private static boolean bindsZone(List<District> catalog, String zone) {
        for (District d : catalog) {
            if (d.zoneName().equals(zone)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The catalog rows one boot actually printed, in the order it printed
     * them — read out of the run's own stream, not out of the object, which
     * is the entire point: the row is taken from the narrative line at the
     * offset the token starts, so the log's timestamp and severity columns
     * are stripped and what is compared is the row itself.
     */
    private static List<String> bootRows(long seed) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(1 << 16);
        new Simulation(seed, sink, null);
        List<String> printed = new ArrayList<>();
        for (String line : sink.toString(StandardCharsets.UTF_8).split("\n")) {
            int at = line.indexOf(TOKEN);
            if (at >= 0) {
                printed.add(line.substring(at));
            }
        }
        return printed;
    }

    /**
     * The stream's draw total the instant boot finishes, with the narrative
     * sink attached or detached. Two boots in ONE process and the difference
     * between them is the measurement — an absolute total would be a pin on
     * the era, which this probe refuses to be.
     */
    private static long bootDraws(OutputStream sink) throws Exception {
        return Probes.world(new Simulation(SEEDS[0], sink, null)).rng().draws();
    }

    /** One row per quarter, everything the catalog claims about it — the whole comparable surface. */
    private static List<String> rows(PlaceGraph places) {
        List<String> rows = new ArrayList<>();
        for (District d : places.districts()) {
            rows.add(d.row());
        }
        return rows;
    }

    private DistrictNeutral() {}
}
