package matrix.zion;

import matrix.core.Config;
import matrix.core.PlaceGraph;
import matrix.core.Severity;
import matrix.core.World;
import matrix.core.WorldEvent;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.realworld.Human;
import matrix.realworld.LinkKind;
import matrix.realworld.NeuralLink;

import java.util.ArrayList;
import java.util.List;

/**
 * One hovercraft's pirate uplink (crown #123, D-032): channel capacity, a
 * per-session station budget in ticks, and the channel board itself. A
 * session begins at the insertion zone Zion assigned at launch (#116's
 * rotation — the rig spends no fate on it); {@link #open} spawns a
 * RED {@link Avatar} at that zone and jacks a crew Human in over a
 * {@code NeuralLink(PIRATE)} — the D-013 bridge, zero new Matrix code, and
 * inside, an avatar is an avatar (A1). The rig OWNS its links: pirate wires
 * are Zion's book, never {@code RealWorld}'s, tracked in registration
 * order. That also keeps them out of the acceptance loop — a pirate rides
 * in lucid, and the dream is not negotiated with a mind that knows it is
 * dreaming (flagged for the gate, like everything else on this floor).
 * Refuses: mission strategy (the ship's), the census (Zion's), and any
 * Matrix-side special-casing.
 *
 * <p>Recall, since #117: the booths have teeth, and the clean exit is
 * EARNED. The recall order lifts nobody — it sends every open channel's
 * avatar sprinting for the nearest booth, and only within
 * {@code EXIT_REACH_CM} of one does the wire {@code closeClean}: the
 * jackOut. A sprint still on the wire past {@code RECALL_TIMEOUT_TICKS}
 * gets cut — {@code severUnclean}, flatline, the cost of a slow exit.
 * {@link #destroy} is the other unclean ending: every open link severed
 * through the bridge — flatline, {@code Remove}, nothing to flush (#119
 * scripts the scenario that triggers it).
 */
public final class BroadcastRig {
    /** The channel board, registration order. Closed wires stay on it until the next session clears the board. */
    private final List<NeuralLink> links = new ArrayList<>();
    private PlaceGraph.Zone insertionZone;
    private int budgetRemaining = 0;
    private int traced = 0;
    /** True from the recall order until the next session arms — the ship holds station on it while wires stay open. */
    private boolean recallIssued = false;
    /** Watches since the recall order — the sprint clock the timeout cut is measured on. */
    private int sprintTicks = 0;

    /**
     * A new session: take the insertion zone Zion assigned at launch
     * (#116 — zone choice is the scheduler's strategy now, rotated per
     * sortie; the rig draws nothing), arm the station budget, clear the
     * board — the recall that ended the last session closed every
     * channel, so nothing open is ever dropped here.
     */
    public void beginSession(World world, PlaceGraph.Zone zone) {
        links.clear();
        insertionZone = zone;
        budgetRemaining = Config.RIG_STATION_TICKS;
        recallIssued = false;
        sprintTicks = 0;
        world.log(Severity.SYS, "broadcast rig live: insertion zone " + insertionZone.name()
                + ", " + Config.RIG_CAPACITY + " channels, station budget "
                + Config.RIG_STATION_TICKS + " ticks");
    }

    /**
     * One channel: spawns a RED avatar at the session's insertion zone and
     * returns the PIRATE wire, registered on the board. Capacity is the law
     * (#115 DoD): at {@code RIG_CAPACITY} simultaneous open links the rig
     * refuses and the crew member stays aboard.
     */
    public NeuralLink open(Human crew, World world) {
        if (openLinks() >= Config.RIG_CAPACITY) {
            world.log(Severity.SYS, "rig refuses: all " + Config.RIG_CAPACITY
                    + " channels busy — " + crew.name + " stays aboard");
            return null;
        }
        Avatar red = new Avatar(world.allocateId(), insertionZone.center(), crew.name, Pill.RED);
        world.queue(new WorldEvent.Spawn(red));
        NeuralLink link = new NeuralLink(crew, red, LinkKind.PIRATE);
        links.add(link);
        world.log(Severity.OK, "channel open: " + crew.name + " rides the pirate signal into "
                + insertionZone.name());
        return link;
    }

    /** One station tick off the budget; true exactly when it exhausts — the recall trigger (#115 DoD). */
    public boolean spendBudgetTick() {
        if (budgetRemaining > 0) {
            budgetRemaining--;
        }
        return budgetRemaining == 0;
    }

    /**
     * The wire watches, whatever the mission clock says — deaths first,
     * then the sprint. Walk the board in registration order and let each
     * link observe its avatar: the mind-body rule for pirates runs HERE,
     * because these links are Zion's book, not RealWorld's, and an agent
     * kill during the sprint stays the hard way, exactly as before. Then,
     * recall pending, the sprint clock advances (#117): a recalled avatar
     * within {@code EXIT_REACH_CM} of a booth jacks out CLEAN —
     * {@code closeClean}, {@code Remove}, the Human lives, and the tally
     * never counts it — and on the first watch past
     * {@code RECALL_TIMEOUT_TICKS} the rig cuts every wire still open:
     * {@code severUnclean}, counted as {@code traced=} alongside deaths
     * inside — the ZION line's hard endings, and nothing else changes on
     * the instruments. Mercy is checked before the knife on the timeout
     * watch itself: a mind AT the booth on the last tick goes home.
     */
    public void watch(World world) {
        for (NeuralLink link : links) {
            if (link.observeDeath()) {
                traced++;
                world.log(Severity.BAD, "the wire went dark — " + link.human.name
                        + " flatlined mid-broadcast (no pod to flush — they died free)");
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
        if (!recallIssued || openLinks() == 0) {
            return;
        }
        sprintTicks++;
        for (NeuralLink link : links) {
            matrix.core.Position booth = world.places()
                    .nearestExit(link.avatar.xCm(), link.avatar.yCm());
            if (!link.closed() && matrix.core.Geo.within(
                    link.avatar.xCm(), link.avatar.yCm(),
                    booth.xCm(), booth.yCm(), Config.EXIT_REACH_CM)) {
                link.closeClean();
                world.queue(new WorldEvent.Remove(link.avatar.id));
                world.log(Severity.OK, "booth exit: " + link.human.name
                        + " reaches a phone line — jacked out clean");
            }
        }
        if (sprintTicks > Config.RECALL_TIMEOUT_TICKS) {
            for (NeuralLink link : links) {
                if (!link.closed()) {
                    link.severUnclean();
                    traced++;
                    world.log(Severity.BAD, "the rig cuts the wire — " + link.human.name
                            + " never reached a phone line (flatline; the cost of a slow exit)");
                    world.queue(new WorldEvent.Remove(link.avatar.id));
                }
            }
        }
    }

    /**
     * The recall order (#117 — this replaces #114's simplified instant
     * lift, on purpose and out loud): nobody is lifted. Each open
     * channel's avatar hears the order and sprints for the nearest exit
     * booth; {@link #watch} settles every wire from here — the booth
     * jackOut or the timeout cut. The order itself closes nothing.
     */
    public void recall(World world) {
        recallIssued = true;
        sprintTicks = 0;
        int ordered = 0;
        for (NeuralLink link : links) {
            if (!link.closed()) {
                link.avatar.recalled = true;
                ordered++;
            }
        }
        world.log(Severity.OK, ordered == 0
                ? "recall order: the board is already silent — nobody left on the wire"
                : "recall order: " + ordered + " minds sprint for the phone lines — a booth within "
                        + Config.EXIT_REACH_CM + " cm jacks out clean, "
                        + Config.RECALL_TIMEOUT_TICKS + " ticks or the wire is cut");
    }

    /** True from the recall order until the next session arms — the ship reads it to hold station for the exit. */
    public boolean recallIssued() {
        return recallIssued;
    }

    /**
     * The rig dies mid-session: every open link severs UNCLEAN in
     * registration order through the same bridge — brain flatlined, link
     * closed, {@code Remove} queued, nothing to flush (D-032 confirmation;
     * #119 scripts the loss that calls this).
     */
    public void destroy(World world) {
        for (NeuralLink link : links) {
            if (!link.closed()) {
                link.severUnclean();
                traced++;
                world.log(Severity.BAD, "the rig dies with " + link.human.name
                        + " still under — severed, flatlined, nothing to flush");
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
    }

    /** Open channels right now — the ZION line's {@code links=} share of this rig. */
    public int openLinks() {
        int n = 0;
        for (NeuralLink link : links) {
            if (!link.closed()) {
                n++;
            }
        }
        return n;
    }

    /** Sessions ended the hard way, cumulative — death inside, a timeout cut, or a rig death; the booth exit never counts. */
    public int traced() {
        return traced;
    }
}
