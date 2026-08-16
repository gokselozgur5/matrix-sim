package matrix.zion;

import matrix.core.Config;
import matrix.core.PlaceGraph;
import matrix.core.Severity;
import matrix.core.World;
import matrix.realworld.Human;
import matrix.realworld.Origin;

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
    // Read-only: the three hulls, by name, never written after the class loads (#1148).
    private static final String[] ROSTER = {"the Nebuchadnezzar", "the Logos", "the Hammer"};

    /** The generation mark's table — ASCII, locale-free, greedy from the top. */
    // Read-only: the numeral table the mark renderer walks. Its CONTENTS are load-bearing
    // and its mutability is not — nothing writes to it after the class loads (#1148).
    private static final int[] MARK_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    // Read-only, paired with MARK_VALUES above by index (#1148).
    private static final String[] MARK_SIGNS = {
            "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /**
     * The doors, resolved once: {@link Origin#values()} clones its array on
     * every call and the ZION line asks for it on a cadence.
     */
    // Read-only. Origin.values() hands back a fresh array on every call, so caching it once
    // is the point — and nothing writes to the cache (#1148).
    private static final Origin[] DOORS = Origin.values();

    /**
     * One name on the registry: the freed Human and the door they came
     * through. Until #831 this was two lists and a comment promising they
     * stayed index-aligned — an invariant no type, assertion or probe held,
     * in a class that handed the census out live. One list of a record is
     * the same fact with the alignment welded on: nobody is admitted
     * without a door, and no caller can shift one list out from under the
     * other.
     */
    private record Citizen(Human human, Origin origin) {}

    /** The registry, in liberation order — the fallen stay on it (D-011: liberation is not deletion). */
    private final List<Citizen> census = new ArrayList<>();
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

    /** The door on this side: one freed Human enters the census under the door they came through — link already closed clean, nothing flushed. */
    public void absorb(Human human, Origin origin) {
        census.add(new Citizen(human, origin));
    }

    /**
     * The canonical zion slot, per crown #84: fleet tick → sortie
     * scheduling. The root's drain is NOT part of this slot and must never
     * be moved into it (#830): this slot runs inside the node loop, ahead
     * of the treaty block that queues liberations, so a slot-side drain
     * would land every treaty liberation a tick late — invisibly, since
     * {@code ZION} prints every hundred ticks and the census is outside
     * the digest chain. What this slot sees is the census as the drain
     * left it at the END of the previous tick. #116's scheduler:
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
            // #948: the loss is a fact about the fleet, not about the
            // ordinal. The ordinal counts every keel ever laid, afloat()
            // only the hulls still in the fight; a gap between them, read
            // before this keel joins, is a hull that went down.
            boolean replacing = laydown > afloat();
            Hovercraft hull = new Hovercraft(hullName(laydown));
            fleet.add(hull);
            world.log(Severity.FATE, laydownLine(laydown, hull.name, replacing));
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
     * was lying about a count it narrated.
     *
     * <p>The clause past the second was the same defect one layer down
     * (#948): a loss read off the ordinal instead of off the fleet. That
     * proxy holds at exactly one value of one knob — with the shipped
     * {@code FLEET_MAX = 2} the gate {@code afloat() < FLEET_MAX} can only
     * open a third time after a hull is gone — and {@code FLEET_MAX} is a
     * D-006 tunable. At 3 a third keel is growth, and the line narrated a
     * loss into a run that had none. So the caller passes the fact rather
     * than a stand-in for it: {@code replacing} is the gap between the
     * keels ever laid and the hulls still afloat, read at laydown — the
     * same asymmetry between the registry and the fight that the whole
     * #806 cluster is about. A fleet that has lost nothing narrates no
     * loss; it narrates the board it just added.
     *
     * <p>That fix took the general case and left the specific one (#1056),
     * and the specific one is wrong at the SHIPPED {@code FLEET_MAX = 2}
     * rather than at a tuned value of it: a second keel laid after the first
     * hull sank leaves exactly one board manned, and the arm said two. Both
     * arms past the first read {@code replacing} now. The first cannot need
     * it — {@code laydown} is {@code fleet.size()} and no hull afloat is
     * missing from that list, so ordinal 0 is an empty fleet and an empty
     * fleet has lost nothing.
     *
     * <p>Public for the reason {@link #hullName} is (#832): the names were
     * checked over three thousand ordinals and what these sentences CLAIM
     * was checked by nothing, which is how both halves of one defect reached
     * main. {@code FleetLines} walks this function, no universe booted.
     */
    public static String laydownLine(int laydown, String name, boolean replacing) {
        return switch (laydown) {
            case 0 -> "the first hull: " + name + " joins the fleet — the census learns to fly";
            case 1 -> "a second hull: " + name + " joins the fleet — "
                    + (replacing ? "the census replaces what it lost" : "the census can man two boards");
            default -> "hull number " + (laydown + 1) + ": " + name + " joins the fleet — "
                    + (replacing ? "the census replaces what it lost" : "the census can man another board");
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
        for (Citizen c : census) {
            if (c.human().alive()) {
                n++;
            }
        }
        return n;
    }

    /**
     * How many on the registry came through one door — counted off the
     * registry itself on the spot, never off a tally kept beside it. A
     * running counter per door would be a second parallel structure, which
     * is the defect this unit is here to remove; the walk is over a list
     * the fleet already walks twice a tick, read on the ZION cadence.
     */
    private int admittedThrough(Origin door) {
        int n = 0;
        for (Citizen c : census) {
            if (c.origin() == door) {
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
        for (Citizen c : census) {
            Human h = c.human();
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
        for (Citizen c : census) {
            Human h = c.human();
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
     * counts: fleet size, open pirate links across the fleet's rigs,
     * sessions ended the hard way, and the share of those open links the
     * board cannot settle. {@code Locale.ROOT}, byte-stable across
     * locales; the caller prints, never this class. The #197 note is
     * cashed (#119): a lost hull's tally moved up to the city the moment
     * it went down, so the sum walks only ships still afloat and no count
     * is forgotten. {@code fleet=} keeps counting every hull ever built —
     * the fallen stay on the registry, ships included (D-011's spirit).
     *
     * <p>{@code deferred=} is the #809 debt on the instruments (#846). A
     * wire whose avatar the world no longer holds is one #206's presence
     * gate correctly refuses to cut, and inside {@code links=} it is
     * indistinguishable from a wire that is merely still sprinting: the
     * operator watching METRIC/ZION/DIGEST — the whole observation contract
     * (D-020) — could not tell a live sortie from a hull carrying a debt it
     * cannot pay. It is a SUBSET of {@code links=}, never larger, and it is
     * a gauge like {@code links=} and unlike the cumulative {@code traced=}:
     * it falls back to zero the moment the Source gives the mind back and
     * the next watch cuts the wire.
     *
     * <p>Summed over the same hulls {@code links=} walks, LOST excluded, and
     * for the same reason plus one: {@link BroadcastRig#destroy} cuts every
     * open wire unconditionally, so a sunk rig's deferred count is zero by
     * construction and there is nothing for the city to carry the way it
     * carries {@code tracedFallen}. A DOCKED hull is included and that is
     * the point — since #809 a ship can come home with wires still on the
     * board, and that is precisely the state this column exists to show.
     *
     * <p>The column lands after {@code traced=} and not at the end of the
     * printed line, which are two different places: the root glues
     * {@code trace_mnn_cm}/{@code red_baseline_cm} on behind this string
     * when the trace metric is measurable (#118, #374), so the end of the
     * line belongs to an optional rider. See {@code probes/LineGrammar} —
     * the registry's ZION field list is the argument.
     *
     * <p>The per-door columns are #831's. {@code census=} counted the
     * registry and said nothing about how it was filled, so
     * {@code census=7} read the same whether the seventh came through the
     * treaty's open door or walked out on his own — and the origin tag that
     * could have answered was recorded by {@link #absorb} and read by
     * nothing. One column per {@link Origin}, generated from the
     * vocabulary rather than written out here, so the columns sum to
     * {@code census=} by construction and a door added later cannot be
     * silently left off the line.
     *
     * <p>{@code selfsub=} is deliberately a second sighting of a number
     * METRIC already prints, and the pair is the point: METRIC's counts
     * doors OPENED on the real-world side, this one counts citizens
     * ADMITTED on Zion's, and the whole of #200 is the claim that the tag
     * survives the crossing. Equal is the assertion; unequal names a
     * liberation the root drained and the city did not keep.
     *
     * <p>{@code living=} is #1007's, and it is the number this class
     * actually schedules on: {@link #livingCensus} is the laydown gate
     * ({@code livingCensus() >= RIG_CAPACITY * (afloat() + 1)}) and its
     * aliveness test is the first of the three {@link #drawCrew} takes a
     * berth from. {@code census=} is cumulative by law — the fallen stay on
     * the registry, liberation is not deletion (D-011) — so it keeps
     * counting a city after the city can no longer fly, and at seed 2 the
     * run ends {@code census=6 treaty=6} over six corpses. The per-door
     * columns partition the same registry and inherit the same blindness:
     * {@code treaty=6} there is six dead treaty walkers, and seed 4 prints
     * the same six numbers over three citizens who are still breathing.
     * Grouping by door was one half; this is the other, and it is the half
     * the gate reads.
     *
     * <p>Not derivable from what was already on the line. {@code traced=}
     * counts hard endings the RIGS saw, and the two numbers happen to
     * satisfy {@code census - traced == living} on every canonical seed
     * measured (1..30 at 6,000 ticks) — a coincidence of the runs where
     * every citizen who dies dies on a wire, not an identity.
     * {@link Hovercraft#destroy} flatlines a crew that is aboard with no
     * wire open, and {@code traced} never sees those: seed 42 under
     * {@code --sink-at 5000} ends {@code census=6 traced=3 living=0}, three
     * souls lost with the hull in TRANSIT and the subtraction off by three.
     * Counted off the registry on the spot for the same reason
     * {@code admittedThrough} is, and a gauge, not a tally: it falls as the
     * city dies.
     *
     * <p>Mandatory, so it lands at the END of the mandatory block, after the
     * doors and before the optional trace rider — the same road
     * {@code treaty=}/{@code selfsub=} took and for the registry's reason,
     * not politeness: a reader keys on position, so writing it beside
     * {@code census=} where it belongs by meaning would rename five shipped
     * columns. The ZION arities become 9 and 11 in {@code probes/LineGrammar}.
     */
    public String zionLine(long tick) {
        int links = 0;
        int deferred = 0;
        int traced = tracedFallen;
        for (Hovercraft ship : fleet) {
            if (ship.state() == Hovercraft.MissionState.LOST) {
                continue;
            }
            links += ship.rig().openLinks();
            deferred += ship.rig().deferred(world);
            traced += ship.rig().traced();
        }
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                "ZION tick=%d census=%d fleet=%d links=%d traced=%d deferred=%d",
                tick, census.size(), fleet.size(), links, traced, deferred));
        for (Origin door : DOORS) {
            line.append(' ').append(door.tag()).append('=').append(admittedThrough(door));
        }
        line.append(" living=").append(livingCensus());
        return line.toString();
    }

    /**
     * Every pirate body the fleet's boards are carrying — ship order, then
     * each board's registration order. The root carries this to the
     * collector for the #118 trace metric (D-012: only the root holds both
     * banks); nothing here computes, and an empty list is the quiet answer.
     *
     * <p>The board, not the open subset (#808). A wire that has closed still
     * has a body standing in the Matrix until the next flush takes it, and
     * for that one tick the old set moved it from the pirates into the
     * resident field the baseline is measured against. The exclusion set is
     * built from the board itself now, which is where it belonged: a mind
     * the rig ever put in there is the ship's, wire or no wire, until the
     * world lets the body go.
     *
     * <p><b>EVERY HULL'S BOARD, AFLOAT OR NOT</b> (#918). This is the fourth
     * walk over the fleet and the only one that does not guard
     * {@code MissionState.LOST} — launch does, recall does, {@code zionLine}
     * does before counting {@code links=} and {@code traced=}. The omission
     * is deliberate now rather than accidental, and it is deliberate because
     * a sunk hull's board never empties: {@code BroadcastRig.beginSession}
     * holds the only line that takes a closed wire off a board
     * ({@code links.removeIf(NeuralLink::closed)}) and a LOST hull never
     * begins another session. The fleet keeps them by design — <i>LOST hulls
     * stay listed: nobody deleted, not even ships</i> — so the roster of every
     * ship that has ever sunk is in this list until the process exits.
     *
     * <p>Measured at seed 42 with {@code --sink-at 4600}: three wires cut with
     * the Nebuchadnezzar are still on the board at tick 6,000, 1,400 ticks
     * later. {@code probes/BoardScope} counts them rather than leaving the
     * sentence to be believed.
     *
     * <p>It is inert today and the reason is worth stating, because the reason
     * is a filter somewhere else: {@code MetricsCollector.traceSuffix} keeps
     * only {@code p.alive && world.isPresent(p)}, and {@code destroy} queues a
     * {@code Remove} for every wire it cuts, so a drowned pirate never reaches
     * the arithmetic. The presence filter is the only thing standing between a
     * stale roster and the baseline. Nothing states that as an invariant and
     * nothing checks it, which is the half of #918 this javadoc closes — the
     * set is honestly named now, so a reader who relies on it relies on what it
     * actually is.
     *
     * <p>One path in the tree defeats the filter and no seed has walked it:
     * {@code Remove} is id-keyed, {@code SmithPrime.infect} replaces a victim
     * with a copy under a NEW id, so the {@code Remove} queued for an infected
     * pirate matches nothing — and when the copy is unwound
     * ({@code Replace(c.id, c.original)}) the original goes back alive and
     * present, on the board of a ship that no longer flies. That is a live
     * defect with a dead seed, and it belongs to whoever adds the guard rather
     * than to this correction.
     */
    public List<matrix.entities.Avatar> pirateBoard() {
        List<matrix.entities.Avatar> out = new ArrayList<>();
        for (Hovercraft ship : fleet) {
            ship.rig().boardAvatarsInto(out);
        }
        return out;
    }

    /**
     * The citizens, in liberation order. A fresh list, and that is the
     * second half of #831: the old body returned the field itself, so any
     * caller could add or remove an element and desynchronize a parallel
     * origins list that no assertion checked and nothing read. There is no
     * live {@code List<Human>} left to leak — the registry is a list of
     * {@link Citizen} — and the copy makes the leak impossible rather than
     * merely unattempted. The doors are not on this roster: what asks about
     * a door today is the {@code ZION} line, and a reader nothing calls is
     * how the origin tag got into this state.
     */
    public List<Human> census() {
        List<Human> out = new ArrayList<>(census.size());
        for (Citizen c : census) {
            out.add(c.human());
        }
        return out;
    }
}
