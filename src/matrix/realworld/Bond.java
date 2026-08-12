package matrix.realworld;

import matrix.core.Config;
import matrix.core.Geo;
import matrix.core.Severity;
import matrix.core.World;

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
    /**
     * Census positions at mint time — the pair's identity in the chain
     * (#497). A {@code Human} has no id and its name is not unique (196
     * minds wear 154 names at seed 42), so a name would let two different
     * worlds hash equal. The census is append-only by D-011, so a position
     * taken once is that person's forever.
     */
    private final int aIndex;
    private final int bIndex;
    private State state = State.CANDIDATE;
    private int windows = 0;

    /**
     * Package-private by law: only the registry mints edges, so the ordered
     * walk every downstream reader depends on cannot be forged from outside
     * {@code matrix.realworld}.
     */
    Bond(Human a, int aIndex, Human b, int bIndex) {
        this.a = a;
        this.aIndex = aIndex;
        this.b = b;
        this.bIndex = bIndex;
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

    /**
     * The retail price list's ordering, asserted rather than commented
     * (#382). #212's unifier says the list is ordered by how hard a frame
     * is to reject; "you are dead" is the hardest frame there is, so the
     * miracle must cost more than every other disbelief item this repo
     * prices. A future retune of ANY of those neighbours could otherwise
     * demote the miracle in silence — a comment cannot stop that and an
     * assertion can.
     *
     * <p>Called from --selftest, so the failure mode is a red build rather
     * than a quiet economy. The neighbour list is explicit and its members
     * are named in the line: when #332 and #345 add allegiance terms they
     * belong in this array, and the reviewer who adds them will see why.
     *
     * @throws IllegalStateException when the miracle is no longer the most
     *         expensive thing the world can be made to disbelieve
     */
    public static String retailOrderLine() {
        long[] prices = {
                Config.RESIDUE_RED, Config.RESIDUE_BLUE,
                Config.KID_SPIKE, Config.DEJA_RESIDUE_SPIKE};
        String[] names = {
                "RESIDUE_RED", "RESIDUE_BLUE",
                "KID_SPIKE", "DEJA_RESIDUE_SPIKE"};
        int top = 0;
        for (int i = 1; i < prices.length; i++) {
            if (prices[i] > prices[top]) {
                top = i;
            }
        }
        if (Config.ROOM_303_DEPOSIT <= prices[top]) {
            throw new IllegalStateException("RETAIL order broken: ROOM_303_DEPOSIT="
                    + Config.ROOM_303_DEPOSIT + " no longer outranks " + names[top]
                    + "=" + prices[top] + " — the retail list is ordered by how hard a"
                    + " frame is to reject, and nothing outranks \"you are dead\"");
        }
        return "RETAIL order held: ROOM_303_DEPOSIT=" + Config.ROOM_303_DEPOSIT
                + " > " + names[top] + "=" + prices[top];
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
        private final RealWorld realWorld;
        private final World world;
        /** The rotating pair walk's position: which mind, and how far along the ring its partner stands. */
        private int cursor = 0;
        private int offset = 1;

        public Registry(RealWorld realWorld, World world) {
            this.realWorld = realWorld;
            this.world = world;
        }

        /**
         * The heart's tick, driven by the realworld node BEFORE the death
         * rule runs. Formation is bookkeeping on an accrual window and
         * nothing else happens off one — the same wheel the acceptance loop
         * turns on, because a bond is the same kind of slow accounting.
         */
        public void tick(long tick) {
            if (tick % Config.ACCRUE_EVERY_TICKS != 0) {
                return;
            }
            discover();
            weave();
        }

        /**
         * The weave (#494). A bond is not declared, it is WOVEN: every
         * candidate edge that is still co-present this window earns one
         * window, and past {@code BOND_WEAVE_WINDOWS} it becomes
         * load-bearing. That threshold is the gate's guard rail against
         * speed-run love and the precondition the Room 303 clause checks
         * before it unwrites a death — no edge below it may ever fire.
         *
         * <p>Weaving is bookkeeping and never a draw: it reads the same
         * already-earned co-presence the candidate set derives from. An edge
         * whose minds have drifted apart simply earns nothing this window —
         * accrual is cumulative, because time spent apart is not time
         * unspent together. It does not decay and it does not expire; if the
         * verdict wants love to be perishable that is one branch, here.
         *
         * <p>The crossing speaks exactly once. A woven edge is skipped by
         * this walk forever after, so no edge can re-cross, and a SCAR
         * (#378) can never come back through it — the conservative reading
         * of D-045 open point (c), which the gate left open: this crew does
         * NOT allow renewal after consumption, because the scar keeps the
         * pair's slot in the book and {@link #between} therefore refuses to
         * mint them a second edge. Flagged, not assumed; reversing it is
         * deleting one guard.
         */
        private void weave() {
            for (int i = 0; i < edges.size(); i++) {
                Bond bond = edges.get(i);
                if (bond.state != State.CANDIDATE || !coPresent(bond.a, bond.b)) {
                    continue;
                }
                bond.windows++;
                if (bond.windows >= Config.BOND_WEAVE_WINDOWS) {
                    bond.state = State.WOVEN;
                    world.log(Severity.FATE, "BOND woven: " + bond.pair()
                            + " — over " + bond.windows + " windows");
                }
            }
        }

        /**
         * Where an edge comes from — D-045's open point (b), closed here as
         * a stated rule so the verdict can redirect it in ONE place.
         *
         * <p>Two sources were on the table and both derive from state the
         * world already has, never from a draw. This unit ships EARNED
         * CO-PRESENCE: two minds whose avatars keep standing near each other
         * are a candidate pair. The rejected alternative is boot-seeded
         * affinity — a pure function of the two names, the D-033 fate
         * precedent, "some people were always going to matter to each other"
         * — rejected because it makes the warmth of the world a property of
         * its seed rather than of what happens in it, and because the
         * commute (#105) already produces real shared rooms the hash can
         * see. If the verdict prefers the seed, {@link #coPresent} is the
         * one method that changes.
         *
         * <p>Bounded by construction: the walk offers exactly
         * {@code BOND_SCAN_PAIRS} pairs per window from a rotating cursor
         * over the pair ring, so every pair in the census is eventually
         * offered and no window ever costs more than a constant. Zero draws
         * (D-010): the rng is never consulted, here or anywhere below.
         */
        private void discover() {
            List<Human> minds = realWorld.humans();
            int n = minds.size();
            if (n < 2) {
                return;
            }
            for (int k = 0; k < Config.BOND_SCAN_PAIRS; k++) {
                if (edges.size() >= Config.BOND_MAX_EDGES) {
                    return;
                }
                int xi = cursor % n;
                int yi = (cursor + offset) % n;
                Human x = minds.get(xi);
                Human y = minds.get(yi);
                step(n);
                if (x == y || !coPresent(x, y) || between(x, y) != null) {
                    continue;
                }
                Bond bond = new Bond(x, xi, y, yi);
                edges.add(bond);
                world.log(Severity.TRACE, "BOND candidate: " + bond.pair()
                        + " — their paths keep crossing");
            }
        }

        /** One step along the pair ring: every mind against every offset, forever, in order. */
        private void step(int n) {
            cursor++;
            if (cursor >= n) {
                cursor = 0;
                offset++;
                if (offset >= n) {
                    offset = 1;
                }
            }
        }

        /**
         * The co-presence predicate — the one method open point (b) lives in.
         * Both minds must be dreaming (an open wire, a living avatar) and
         * their avatars must stand within {@code BOND_NEAR_CM}. Deliberately
         * NOT a presence check against the entity list: that walk is O(census)
         * and this runs on a wheel. The clause's guard pays for presence
         * because it fires once; the weave cannot afford to.
         */
        private boolean coPresent(Human x, Human y) {
            NeuralLink lx = x.link();
            NeuralLink ly = y.link();
            if (lx == null || ly == null || lx.closed() || ly.closed()) {
                return false;
            }
            if (!lx.avatar.alive || !ly.avatar.alive) {
                return false;
            }
            return Geo.within(lx.avatar.xCm(), lx.avatar.yCm(),
                    ly.avatar.xCm(), ly.avatar.yCm(), Config.BOND_NEAR_CM);
        }

        /** The edge between these two, either way round, or null. Identity, never names. */
        Bond between(Human x, Human y) {
            for (int i = 0; i < edges.size(); i++) {
                Bond bond = edges.get(i);
                if ((bond.a == x && bond.b == y) || (bond.a == y && bond.b == x)) {
                    return bond;
                }
            }
            return null;
        }

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

        /**
         * The heart's segment of the digest chain — framed, in mint order,
         * appended by the root immediately after the realworld segment
         * (D-020/A4, the {@code digestInto} declared-move precedent). New
         * state is digest-covered state and love gets no exemption: an edge
         * that can unwrite a death is state that decides the world, so the
         * fold has to be able to prove it re-grew the same one.
         *
         * <p>Three values per edge, nothing else: the pair as census
         * positions, the weave state, the accrual count. Positions rather
         * than names because names are not unique; the ordinal rather than
         * the enum because a sink speaks integers, and the ordinal is
         * append-safe as long as nobody reorders {@link State} — which is
         * the same discipline every enum in the chain already lives under.
         *
         * <p>What the digest sees here the v4.5 {@code Snapshot} must
         * retain (#179), so the root feeds this walk to BOTH sinks. If they
         * ever disagree, {@code SNAPSHOT == DIGEST} is the thing that
         * catches it, and it is checked in this unit rather than
         * discovered in a later one.
         */
        public void digestInto(matrix.core.StateSink sink) {
            sink.putCount(edges.size());
            for (int i = 0; i < edges.size(); i++) {
                Bond bond = edges.get(i);
                sink.putInt(bond.aIndex);
                sink.putInt(bond.bIndex);
                sink.putInt(bond.state.ordinal());
                sink.putInt(bond.windows);
            }
        }

        /** The registry's one line, spoken at boot and greppable forever after. */
        public String line() {
            return "BOND registry: edges=" + size() + " woven=" + woven();
        }
    }
}
