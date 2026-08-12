package matrix.zion;

import matrix.core.Config;
import matrix.core.PlaceGraph;
import matrix.core.Severity;
import matrix.core.World;
import matrix.realworld.Human;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The city: the free-human registry (D-011 cashed in — every liberation
 * lands HERE, somewhere real) and its economy (crown #84): the census is
 * spent on the fleet — crews drawn seeded from citizens, sorties scheduled
 * under the rig's budget. The census grows only through {@link #absorb},
 * and absorb order is liberation order: the root — the only class allowed
 * to hold both banks (D-012) — drains RealWorld's pending liberations into
 * this door each tick, in link registration order. Refuses: deleting a
 * Human (liberation, not deletion — the fallen stay on the registry),
 * touching pods or links of the still-plugged, Matrix-side anything, and
 * printing — it returns its {@code ZION} line; only the root emits.
 */
public final class Zion {
    /**
     * The named keels (#116): names in boot order, a fixed array — the Nth
     * hull ever laid down bears the Nth name, never a draw. The Hammer waits
     * past {@code FLEET_MAX} for a later floor. This is a list of names, not
     * a ceiling on keels: {@link #hullName} owns what a hull is called, and
     * it does not stop where the array does.
     */
    private static final String[] ROSTER = {"the Nebuchadnezzar", "the Logos", "the Hammer"};

    /** The generation mark's table — ASCII, locale-free, greedy from the top. */
    private static final int[] MARK_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] MARK_SIGNS = {
            "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private final List<Human> census = new ArrayList<>();
    /** Index-aligned with the census: where each citizen came from ("treaty" today; #121 adds the Kid's own tag). */
    private final List<String> origins = new ArrayList<>();
    /** The fleet: ships built and lost here (composition — crown #84). LOST hulls stay listed: nobody deleted, not even ships. */
    private final List<Hovercraft> fleet = new ArrayList<>();
    /** A filed sink order (#119): executes at the top of the next zion tick, then clears. */
    private boolean sinkOrdered = false;
    /** Hard endings that went down with a hull — the city keeps the count a sunk rig can no longer report. */
    private int tracedFallen = 0;
    /** Insertion-zone rotation (#116): -1 until the first sortie draws the seeded start. */
    private int zoneCursor = -1;
    private final World world;

    public Zion(World world) {
        this.world = world;
    }

    /** The door on this side: one freed Human enters the census — link already closed clean, nothing flushed. */
    public void absorb(Human human, String origin) {
        census.add(human);
        origins.add(origin);
    }

    /**
     * The canonical zion slot, per crown #84: absorb (the root's drain,
     * already landed) → fleet tick → sortie scheduling. #116's scheduler:
     * hulls come off the fixed roster in boot order, one laid down each
     * time the census can man one more full board — the Nebuchadnezzar at
     * {@code RIG_CAPACITY} citizens, the Logos at twice that — up to
     * {@code FLEET_MAX}. Sorties are STAGGERED: at most one ship casts off
     * per tick, walked in fleet list order, so two docked hulls never
     * launch on the same tick and every draw has one possible owner.
     * Draws happen only when a launch actually happens; an idle wing
     * consumes no fate.
     */
    public void tick(long tick) {
        // #119: a loss lands before the fleet flies its tick — the filed sink
        // order (ops console / --sink-at) or the SHIP_LOSS_TICK knob, one
        // trigger, one slot, deterministic either way.
        if (sinkOrdered || tick == Config.SHIP_LOSS_TICK) {
            sinkOrdered = false;
            sinkActiveShip();
        }
        while (afloat() < Config.FLEET_MAX
                && livingCensus() >= Config.RIG_CAPACITY * (afloat() + 1)) {
            int laydown = fleet.size();
            Hovercraft hull = new Hovercraft(hullName(laydown));
            fleet.add(hull);
            world.log(Severity.FATE, laydownLine(laydown, hull.name));
        }
        for (Hovercraft ship : fleet) {
            ship.tick(world);
        }
        for (Hovercraft ship : fleet) {
            if (ship.state() == Hovercraft.MissionState.DOCKED) {
                List<Human> drawn = drawCrew();
                if (!drawn.isEmpty()) {
                    ship.launch(drawn, nextZone(), world);
                }
                // One launch per tick, and the free pool is shared — if this
                // hull could not be manned, no hull behind it can be either.
                break;
            }
        }
    }

    /**
     * What the Nth hull ever laid down is called (#806), N zero-based and
     * unbounded. The laydown ordinal counts the fallen, because the registry
     * does — #202's ruling, kept: liberation is not deletion and neither is
     * a sinking. The laydown GATE counts only the afloat (#206, M1). Those
     * two numbers were the same number once and the roster index quietly
     * assumed they always would be; the third loss proved otherwise with an
     * {@code ArrayIndexOutOfBoundsException}.
     *
     * <p>Through the roster this IS the roster, in boot order: same three
     * hulls, same three names, same film. Past it the city does what fleets
     * have always done with a name whose bearer is gone — lay the keel again
     * and mark the generation: {@code the Nebuchadnezzar II} is the fourth
     * keel, {@code the Logos II} the fifth. That is the rule, stated, not a
     * silent modulo: the mark is part of the name, so no two hulls ever wear
     * the same string, the fallen keep the name they went down under, and
     * any line in the log reads back to exactly one ordinal.
     *
     * <p>The invariant is now structural rather than remembered: this
     * function is TOTAL over the ordinals, so there is no index left for the
     * array to refuse. Public because an invariant nobody can read is the
     * thing that broke here — a bench row can walk the ordinals and check
     * the rule without booting a universe (#832).
     */
    public static String hullName(int laydown) {
        String named = ROSTER[laydown % ROSTER.length];
        int generation = 1 + laydown / ROSTER.length;
        return generation == 1 ? named : named + " " + generationMark(generation);
    }

    /**
     * How the city announces a keel (#806, the same block's second lie): by
     * its ORDINAL, which is what the sentence always claimed to be saying.
     * "A second hull" was printed over the third one — witnessed at seed 42,
     * t=021560, over the Hammer — and every hull after it would have said
     * the same. The instrument was not lying about a count it printed; it
     * was lying about a count it narrated. Past the second, a laydown can
     * only happen because a hull was lost — the gate is
     * {@code afloat() < FLEET_MAX} — so the line says that, instead of
     * counting boards it is not adding.
     */
    private static String laydownLine(int laydown, String name) {
        return switch (laydown) {
            case 0 -> "the first hull: " + name + " joins the fleet — the census learns to fly";
            case 1 -> "a second hull: " + name + " joins the fleet — the census can man two boards";
            default -> "hull number " + (laydown + 1) + ": " + name
                    + " joins the fleet — the census replaces what it lost";
        };
    }

    /** The generation mark: greedy Roman, so every generation a census could reach has a name. */
    private static String generationMark(int generation) {
        StringBuilder mark = new StringBuilder();
        int n = generation;
        for (int i = 0; i < MARK_VALUES.length; i++) {
            while (n >= MARK_VALUES[i]) {
                n -= MARK_VALUES[i];
                mark.append(MARK_SIGNS[i]);
            }
        }
        return mark.toString();
    }

    /**
     * The sink order (#119): operator-driven loss, filed by the root —
     * exactly like reload, except the execution waits for the canonical
     * zion slot so the cascade always lands deterministically. Natural
     * loss causes (sentinels, squiddies, sabotage) arrive with later
     * units; today the operator IS fate.
     */
    public void orderSink() {
        sinkOrdered = true;
    }

    /**
     * The active ship goes down: first hull in fleet order still afloat.
     * The rig severs every open wire through the bridge (one BAD line per
     * wire), the ship closes with its FATE line, and the city absorbs the
     * fallen rig's hard-ending tally — a count must not sink with a hull.
     */
    private void sinkActiveShip() {
        for (Hovercraft ship : fleet) {
            if (ship.state() != Hovercraft.MissionState.LOST) {
                ship.destroy(world);
                tracedFallen += ship.rig().traced();
                return;
            }
        }
        world.log(Severity.SYS, "sink order refused: no hull afloat");
    }

    /**
     * The insertion-zone strategy (#116): zone choice stops being a
     * per-session draw and becomes rotation. The first sortie ever flown
     * draws the seeded start — one {@code world.rng()} draw in the zion
     * slot, AFTER that launch's crew draws, and the only fate this
     * strategy ever spends — then every later sortie takes the next zone
     * in {@code PlaceGraph} list order, round-robin, fleet-wide. Launches
     * are staggered one per tick, so the cursor's walk is a total order:
     * same seed, same zone for every sortie ever flown. Which booths a
     * zone can reach is the operational price of the rotation — #117
     * collects it.
     */
    /** Hulls still in the fight — the fallen stay on the registry but hold no slot (#206, M1). */
    private int afloat() {
        int n = 0;
        for (Hovercraft ship : fleet) {
            if (ship.state() != Hovercraft.MissionState.LOST) {
                n++;
            }
        }
        return n;
    }

    /** Citizens still breathing — a census that counts its dead cannot crew a ship. */
    private int livingCensus() {
        int n = 0;
        for (matrix.realworld.Human h : census) {
            if (h.alive()) {
                n++;
            }
        }
        return n;
    }

    private PlaceGraph.Zone nextZone() {
        List<PlaceGraph.Zone> zones = world.places().zones();
        zoneCursor = zoneCursor < 0
                ? world.rng().nextInt(zones.size())
                : (zoneCursor + 1) % zones.size();
        return zones.get(zoneCursor);
    }

    /**
     * The seeded crew draw (crown #84): the free pool is walked in census
     * order — alive, no wire — and each of the {@code RIG_CAPACITY} berths
     * is filled by one {@code world.rng()} draw from what remains; crew
     * order is draw order. A board flies fully manned or not at all:
     * fewer than a full crew free and nobody flies, nothing is drawn —
     * which is how losses ground hulls (#116's staffing decision, made by
     * the census itself: six citizens fly two boards; the fallen ground
     * them one board at a time).
     *
     * <p>Free means alive, wireless, AND ashore. Wireless alone was the
     * one-ship floor's definition and it lied the moment a second hull
     * existed: a crew in TRANSIT wears no wire yet (links open on
     * arrival), so the Logos drew the Nebuchadnezzar's crew right off her
     * deck — witnessed at seed 42, ticks 4325/4326, before this check.
     * One mind, one berth: the fleet's boards are consulted too.
     */
    private List<Human> drawCrew() {
        List<Human> free = new ArrayList<>();
        for (Human h : census) {
            if (h.alive() && h.link() == null && !aboard(h)) {
                free.add(h);
            }
        }
        if (free.size() < Config.RIG_CAPACITY) {
            return List.of();
        }
        List<Human> crew = new ArrayList<>(Config.RIG_CAPACITY);
        for (int i = 0; i < Config.RIG_CAPACITY; i++) {
            crew.add(free.remove(world.rng().nextInt(free.size())));
        }
        return crew;
    }

    /**
     * The census lane's gate (D-046 open point (a), #335/#468): the citizens
     * free to walk to the inward door — alive, no wire, no berth. The root
     * carries this roster to the door path (D-012: only the root holds both
     * banks); this class neither knows nor asks what it is for.
     *
     * <p>The gate is deliberately NARROW. The verdict left "census only vs
     * runners in-world" open, and the honest way to hold a question open is
     * to answer the half that was settled and refuse the half that was not —
     * not to let the implementation decide by accident. A runner riding a
     * pirate signal wears a wire and is out. A crew mid-mission is out too,
     * and that one is load-bearing rather than tidy: a mind in TRANSIT wears
     * no wire yet, so the wire test alone would have quietly opened the
     * runner lane through the back door and stranded a hovercraft. The berth
     * check is the same one {@link #drawCrew} already trusts.
     *
     * <p>Empty rosters allocate nothing — a city with nobody ashore is the
     * common case, not an error.
     */
    public List<Human> ashore() {
        List<Human> out = null;
        for (Human h : census) {
            if (h.alive() && h.link() == null && !aboard(h)) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(h);
            }
        }
        return out == null ? List.of() : out;
    }

    /** Identity membership across every hull's berth list — a citizen mid-mission is not free, wire or no wire. */
    private boolean aboard(Human h) {
        for (Hovercraft ship : fleet) {
            for (Human c : ship.crew()) {
                if (c == h) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The ZION instrument line (D-020, additive grammar), now with real
     * counts: fleet size, open pirate links across the fleet's rigs, and
     * sessions ended the hard way. {@code Locale.ROOT}, byte-stable across
     * locales; the caller prints, never this class. The #197 note is
     * cashed (#119): a lost hull's tally moved up to the city the moment
     * it went down, so the sum walks only ships still afloat and no count
     * is forgotten. {@code fleet=} keeps counting every hull ever built —
     * the fallen stay on the registry, ships included (D-011's spirit).
     */
    public String zionLine(long tick) {
        int links = 0;
        int traced = tracedFallen;
        for (Hovercraft ship : fleet) {
            if (ship.state() == Hovercraft.MissionState.LOST) {
                continue;
            }
            links += ship.rig().openLinks();
            traced += ship.rig().traced();
        }
        return String.format(Locale.ROOT,
                "ZION tick=%d census=%d fleet=%d links=%d traced=%d",
                tick, census.size(), fleet.size(), links, traced);
    }

    /**
     * Every open pirate avatar across the fleet — ship order, then the
     * board's registration order. The root carries this to the collector
     * for the #118 trace metric (D-012: only the root holds both banks);
     * nothing here computes, and an empty list is the quiet answer.
     */
    public List<matrix.entities.Avatar> openPirateAvatars() {
        List<matrix.entities.Avatar> out = new ArrayList<>();
        for (Hovercraft ship : fleet) {
            ship.rig().openAvatarsInto(out);
        }
        return out;
    }

    public List<Human> census() {
        return census;
    }
}
