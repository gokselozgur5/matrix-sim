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
 * session begins with a seeded insertion-zone draw; {@link #open} spawns a
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
 * <p>Recall, pre-#117: the clean exit at this unit is the operator's lift —
 * {@code closeClean()} plus the avatar's {@code Remove}, in registration
 * order. The booth sprint within {@code EXIT_REACH_CM} arrives with #117;
 * until then the phone lines stay quiet and the lift is honest about being
 * a simplification. {@link #destroy} is the other ending: every open link
 * severed unclean through the bridge — flatline, {@code Remove}, nothing
 * to flush (#119 scripts the scenario that triggers it).
 */
public final class BroadcastRig {
    /** The channel board, registration order. Closed wires stay on it until the next session clears the board. */
    private final List<NeuralLink> links = new ArrayList<>();
    private PlaceGraph.Zone insertionZone;
    private int budgetRemaining = 0;
    private int traced = 0;

    /**
     * A new session: draw the insertion zone (one {@code world.rng()} draw,
     * zion tick slot), arm the station budget, clear the board — the recall
     * that ended the last session closed every channel, so nothing open is
     * ever dropped here.
     */
    public void beginSession(World world) {
        links.clear();
        List<PlaceGraph.Zone> zones = world.places().zones();
        insertionZone = zones.get(world.rng().nextInt(zones.size()));
        budgetRemaining = Config.RIG_STATION_TICKS;
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
     * The wire watches, whatever the mission clock says: walk the board in
     * registration order and let each link observe its avatar — the
     * mind-body rule for pirates runs HERE, because these links are Zion's
     * book, not RealWorld's. A death inside is a session ended the hard
     * way: the tally the ZION line reports as {@code traced=}.
     */
    public void observeDeaths(World world) {
        for (NeuralLink link : links) {
            if (link.observeDeath()) {
                traced++;
                world.log(Severity.BAD, "the wire went dark — " + link.human.name
                        + " flatlined mid-broadcast (no pod to flush — they died free)");
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
    }

    /**
     * The recall order, pre-#117: every open link closes CLEAN in
     * registration order — the operator lifts the mind out, the avatar
     * leaves the world, the Human lives. When the booth sprint lands
     * (#117), reaching a phone line becomes the price of this mercy.
     */
    public void recall(World world) {
        int lifted = 0;
        for (NeuralLink link : links) {
            if (!link.closed()) {
                link.closeClean();
                world.queue(new WorldEvent.Remove(link.avatar.id));
                lifted++;
            }
        }
        world.log(Severity.OK, "recall order: " + lifted
                + " minds lifted out clean — the broadcast ends");
    }

    /**
     * The rig dies mid-session: every open link severs UNCLEAN in
     * registration order through the same bridge — brain flatlined, link
     * closed, {@code Remove} queued, nothing to flush (the D-032
     * confirmation, #119's DoD). One BAD line per cut wire; the count goes
     * back to the ship, whose FATE line carries it. Zion's sink order is
     * the only caller — operator-driven loss; natural causes arrive with
     * later units.
     */
    public int destroy(World world) {
        int cut = 0;
        for (NeuralLink link : links) {
            if (!link.closed()) {
                link.severUnclean();
                traced++;
                cut++;
                world.log(Severity.BAD, "the rig dies with " + link.human.name
                        + " still under — severed, flatlined, nothing to flush");
                world.queue(new WorldEvent.Remove(link.avatar.id));
            }
        }
        return cut;
    }

    /**
     * The open channels' avatars, registration order — the root hands these
     * to the collector for the trace-pressure metric (#118, the #95 fold
     * ruling). A read of the board, nothing more: the rig neither computes
     * nor prints, and the Matrix never learns what a pirate is.
     */
    public void openAvatarsInto(List<Avatar> out) {
        for (NeuralLink link : links) {
            if (!link.closed()) {
                out.add(link.avatar);
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

    /** Sessions ended the hard way, cumulative — death inside or an unclean sever; the lift never counts. */
    public int traced() {
        return traced;
    }
}
