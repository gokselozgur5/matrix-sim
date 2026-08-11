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
     * whatever the state — a death inside does not wait for the ship.
     * Arrival opens the session and jacks the crew in, capacity-capped;
     * budget exhaustion turns the ship for home and the recall executes;
     * docking releases the crew to the census rotation.
     */
    public void tick(World world) {
        rig.observeDeaths(world);
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
                if (rig.spendBudgetTick()) {
                    rig.recall(world);
                    state = MissionState.RETURNING;
                    ticksInState = 0;
                    world.log(Severity.SYS, name + " turns for home — the station budget is spent");
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
     * The ship dies: LOST is terminal, and the open links execute the
     * death rule through the same bridge — the D-032 confirmation. #119
     * scripts the scenario that calls this; nothing on this floor does.
     */
    public void destroy(World world) {
        state = MissionState.LOST;
        rig.destroy(world);
        world.log(Severity.BAD, name + " is lost");
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
