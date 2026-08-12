package matrix.realworld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An edge between two MINDS (D-045). The jack owns mind-body — that is
 * D-013's rule and it lives on {@link NeuralLink}; a bond owns mind-mind,
 * and it lives HERE, real-side, on the biological bank. It is never on the
 * {@code Avatar}, never in {@code entities}, never reachable from the
 * dream: what two people are to each other is not rendered, and the
 * Matrix is not told. The import law is the invariant — no package under
 * {@code matrix.entities} or {@code matrix.machine} may name this type.
 *
 * <p>A bond is not declared, it is WOVEN. An edge opens as a CANDIDATE,
 * accrues over accrual windows, and only past {@code BOND_WEAVE_WINDOWS}
 * is it load-bearing — the gate's guard rail against speed-run love
 * (#215). Past that it can be spent exactly once, by the Room 303 clause,
 * and what survives the spending is a SCAR: an edge that can still tilt
 * allegiance and doors but can never again unwrite a death. One miracle,
 * then guaranteed tragedy — as a state machine, not as a mood.
 *
 * <p>Not a Java {@code record}, deliberately: a row with a state machine
 * cannot be frozen at construction, and D-015/A5 says the state is a
 * VALUE ON THE ROW, not a subclass. "Record" here is the ledger's sense
 * of the word — the thing the registry keeps — which is the sense #324
 * used.
 *
 * <p>This unit ships the type and its home. Formation, accrual, the
 * crossing that speaks and the digest segment are its siblings' (#493,
 * #494, #497); the clause that spends an edge is #325's and only READS
 * what is stored here.
 */
public final class Bond {

    /**
     * Where an edge stands. CANDIDATE is a pair the world has noticed;
     * WOVEN is load-bearing — the only state the clause may spend; SCAR is
     * what a spent edge becomes, permanently ineligible and permanently
     * present.
     */
    public enum State {
        CANDIDATE,
        WOVEN,
        SCAR
    }

    private final Human a;
    private final Human b;
    private State state = State.CANDIDATE;
    private int windows = 0;

    /**
     * Package-private by law: only the registry mints edges, so the ordered
     * walk every downstream reader depends on cannot be forged from outside
     * {@code matrix.realworld}.
     */
    Bond(Human a, Human b) {
        this.a = a;
        this.b = b;
    }

    /** The near end, in the order the registry minted the edge. */
    public Human a() {
        return a;
    }

    /** The far end. Order is registration order, never a sort: the walk must be replayable. */
    public Human b() {
        return b;
    }

    public State state() {
        return state;
    }

    /** Accrual windows this edge has earned — the weave's only clock (#494). */
    public int windows() {
        return windows;
    }

    /** True while the edge is load-bearing: woven, and not yet spent. */
    public boolean loadBearing() {
        return state == State.WOVEN;
    }

    /** True when this mind stands at either end. */
    public boolean holds(Human mind) {
        return a == mind || b == mind;
    }

    /** The far end from {@code mind}, or null when the mind is not on this edge. */
    public Human other(Human mind) {
        if (a == mind) {
            return b;
        }
        if (b == mind) {
            return a;
        }
        return null;
    }

    /** The pair as the log says it, in mint order — one wording, every line. */
    public String pair() {
        return a.name + " and " + b.name;
    }

    /**
     * The registry of edges the biological side owns. Ordered by weave
     * order — the registration-order discipline the link book already runs
     * — because every downstream reader walks it: the crossing that speaks,
     * the clause's guard (#325), and the digest segment (#497). A set would
     * be a replay bug wearing a data structure.
     *
     * <p>It lives one level out from {@link RealWorld} this cycle, on the
     * realworld SYSTEM NODE (D-031), because {@code RealWorld.java} is
     * another crew's territory in this wave; ownership stays biological
     * either way, and moving the field inward is a rename, not a redesign.
     */
    public static final class Registry {

        private final List<Bond> edges = new ArrayList<>();

        /** The ordered walk. Read-only: nobody outside mints or reorders edges. */
        public List<Bond> edges() {
            return Collections.unmodifiableList(edges);
        }

        public int size() {
            return edges.size();
        }

        /** Edges past the threshold — load-bearing or scarred; both were woven. */
        public int woven() {
            int n = 0;
            for (Bond bond : edges) {
                if (bond.state != State.CANDIDATE) {
                    n++;
                }
            }
            return n;
        }

        /** The registry's one line, spoken at boot and greppable forever after. */
        public String line() {
            return "BOND registry: edges=" + size() + " woven=" + woven();
        }
    }
}
