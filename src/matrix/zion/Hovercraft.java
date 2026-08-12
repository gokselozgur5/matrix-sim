package matrix.zion;

import matrix.core.Config;
import matrix.core.PlaceGraph;
import matrix.core.Severity;
import matrix.core.World;
import matrix.realworld.Human;

import java.util.ArrayList;
import java.util.List;

/**
 * Pirate client platform (crown #83, D-032): carries a crew of census
 * Humans through a mission CLOCK — the real world has no geometry, so a
 * sortie is ticks, not space (the crown's flagged hole, accepted for this
 * floor). {@code DOCKED → TRANSIT → ON_STATION → RETURNING}, {@code LOST}
 * terminal; every transition is tick arithmetic against D-006 knobs, so a
 * full sortie shows deterministic timing across double-runs (#114 DoD).
 * The ship hosts the rig (composition — same fate) and executes the recall
 * order when the rig's budget exhausts; the crew is aggregation, citizens
 * on rotation — Zion assigns them, the docking releases them, nobody is
 * owned. Refuses: choosing missions (Zion schedules), owning crew lives,
 * any Matrix-side presence.
 */
public final class Hovercraft {

    /** The mission clock's positions. LOST is terminal — no arithmetic leads back out of it. */
    public enum MissionState { DOCKED, TRANSIT, ON_STATION, RETURNING, LOST }

    /**
     * The ship's own ceiling on the hold (#809). #117 spent this clock's
     * only bound on a branch this class does not own — the rig's timeout
     * cut — and then claimed in prose that the bound holds: <em>"the hold
     * is bounded by the timeout, so the mission clock stays arithmetic."</em>
     * #206 then gave that cut a presence gate, correctly: a wire whose
     * avatar is worn by Smith is not the rig's to cut. Each is right on its
     * own; together they turned the exit condition into "some other class
     * managed to act this tick", and when it cannot, the hull holds station
     * with its crew and its citizens aboard — no BAD line, no FATE line, no
     * instrument moving. Measured at seed 42 with one wire deferred: 1,963
     * watches of hold against a bound advertised as 200.
     *
     * <p>So the ship keeps its own clock. The rig gets its whole promise —
     * {@code RECALL_TIMEOUT_TICKS} watches to settle every wire — and then
     * the same again as grace for the ones it had to defer. Past that the
     * hold is not a sprint any more, it is a fault, and a fault is no reason
     * to strand a hull: the ship turns for home and SAYS what it is leaving
     * on the board. Derived from the rig's own knob, deliberately, rather
     * than adding a knob: it cannot fire before the timeout cut has had its
     * full run, so no run that the rig can settle ever moves.
     */
    private static final int HOLD_CEILING_TICKS = 2 * Config.RECALL_TIMEOUT_TICKS;

    public final String name;
    private final List<Human> crew = new ArrayList<>();
    private final BroadcastRig rig = new BroadcastRig();
    private MissionState state = MissionState.DOCKED;
    private int ticksInState = 0;
    /** The mission brief (#116): the insertion zone Zion assigned at launch, carried to arrival. */
    private PlaceGraph.Zone assignedZone;

    public Hovercraft(String name) {
        this.name = name;
    }

    /**
     * Zion's launch order: the assigned crew boards in assignment order
     * (the seeded draw's order — crown #83), the insertion zone rides the
     * mission brief (#116 — Zion rotates, the rig obeys), and the clock
     * starts. Only a docked ship can cast off.
     */
    public void launch(List<Human> assigned, PlaceGraph.Zone zone, World world) {
        crew.addAll(assigned);
        assignedZone = zone;
        state = MissionState.TRANSIT;
        ticksInState = 0;
        StringBuilder names = new StringBuilder();
        for (Human h : crew) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(h.name);
        }
        world.log(Severity.OK, name + " casts off — crew of " + crew.size() + ": " + names
                + ", bound for " + zone.name() + " (transit " + Config.TRANSIT_TICKS + " ticks)");
    }

    /**
     * One tick of the mission clock. The rig's wire watches first,
     * whatever the state — a death inside does not wait for the ship, and
     * neither does the sprint. Arrival opens the session and jacks the
     * crew in, capacity-capped. Budget exhaustion issues the recall
     * order — since #117 an order, not a lift: the ship HOLDS STATION
     * while the crew sprint for booths, and turns for home when every
     * channel has closed, clean or cut — or when its OWN hold ceiling
     * expires, whichever comes first (#809). The second exit is what makes
     * the clock arithmetic; the first one alone never did, because it is
     * another class's number and that class may rightly decline to move
     * it. Docking releases the crew to the census rotation.
     */
    public void tick(World world) {
        rig.watch(world);
        switch (state) {
            case DOCKED, LOST -> { }
            case TRANSIT -> {
                ticksInState++;
                if (ticksInState >= Config.TRANSIT_TICKS) {
                    state = MissionState.ON_STATION;
                    ticksInState = 0;
                    world.log(Severity.SYS, name + " on station at broadcast depth");
                    rig.beginSession(world, assignedZone);
                    for (Human h : crew) {
                        rig.open(h, world);
                    }
                }
            }
            case ON_STATION -> {
                if (!rig.recallIssued()) {
                    if (rig.spendBudgetTick()) {
                        rig.recall(world);
                    }
                } else if (rig.openLinks() == 0) {
                    state = MissionState.RETURNING;
                    ticksInState = 0;
                    world.log(Severity.SYS, name + " turns for home — every channel closed, one way or the other");
                } else if (++ticksInState >= HOLD_CEILING_TICKS) {
                    // The hold ends on this ship's arithmetic, whatever the
                    // board can or cannot do (#809). The debt stays the
                    // rig's: the board keeps every unsettled wire and cuts
                    // it the moment the world holds that avatar again.
                    state = MissionState.RETURNING;
                    ticksInState = 0;
                    world.log(Severity.SYS, name + " turns for home on its own clock — "
                            + rig.openLinks() + " wires still open, " + rig.deferred(world)
                            + " beyond the board's reach; the hold is over, the debt is not");
                }
            }
            case RETURNING -> {
                ticksInState++;
                if (ticksInState >= Config.TRANSIT_TICKS) {
                    state = MissionState.DOCKED;
                    ticksInState = 0;
                    crew.clear();
                    world.log(Severity.SYS, name + " docks; the crew returns to the census rotation");
                }
            }
        }
    }

    /**
     * The ship dies (#119): LOST is terminal, and every open link executes
     * the death rule through the same bridge — the D-032 confirmation. The
     * rig speaks one BAD line per cut wire; the ship closes with one FATE
     * line carrying the count. Zion's sink order is the only caller today:
     * the loss is operator-driven, deterministic — natural causes
     * (sentinels, squiddies) arrive with later units.
     */
    public void destroy(World world) {
        state = MissionState.LOST;
        int cut = rig.destroy(world);
        int drowned = 0;
        for (matrix.realworld.Human h : crew) {
            if (h.alive()) {
                h.brain.flatline();
                drowned++;
                world.log(Severity.BAD, h.name + " goes down with the ship — no wire, no booth, no way home");
            }
        }
        crew.clear();
        world.log(Severity.FATE, name + " goes down — " + cut + " wires cut, "
                + drowned + " souls lost with the hull");
    }

    public MissionState state() {
        return state;
    }

    public BroadcastRig rig() {
        return rig;
    }

    /** The berth list, assignment order — Zion reads it so nobody is drawn off a deck mid-mission (#116). */
    public List<Human> crew() {
        return crew;
    }
}
