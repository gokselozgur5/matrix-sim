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
 * (#215). Before it gets there it can be lost: a candidate that spends
 * {@code BOND_FORGET_WINDOWS} consecutive windows apart leaves the book
 * (#852) — the world's answer to when it forgets a pair it once noticed,
 * and the only reason the book is not write-once. Past the threshold
 * nothing forgets it, and it can be spent exactly once, by the Room 303
 * clause; what survives the spending is a SCAR: an edge that can still
 * tilt allegiance and doors but can never again unwrite a death. One
 * miracle, then guaranteed tragedy — as a state machine, not as a mood.
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
     * Consecutive accrual windows this CANDIDATE has spent apart — the
     * forgetting's only clock (#852), and the mirror of {@link #windows}.
     * Reset to zero by every window the pair is co-present, so it counts a
     * run and not a total: time apart is not time together undone, and a
     * pair who drift and come back have not been forgotten. It stops moving
     * the moment an edge weaves, which is why every WOVEN and SCAR row
     * carries zero here forever.
     */
    private int apart = 0;
    /**
     * The tick the Room 303 clause spent this edge, or -1 while it is
     * unspent. This is the field the trigger's third question reads, and
     * the reason the answer can never change back: once per edge, ever.
     */
    private long firedAt = -1;
    /** The mind the clause gave back, and its census position — the firing's other half. */
    private Human resurrected;
    private int resurrectedIndex = -1;

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

    /** Consecutive windows apart, zero on every woven or scarred edge (#852). */
    public int apart() {
        return apart;
    }

    /** True while the edge is load-bearing: woven, and not yet spent. */
    public boolean loadBearing() {
        return state == State.WOVEN;
    }

    /** The tick the clause spent this edge, or -1 — the third question's whole answer. */
    public long firedAt() {
        return firedAt;
    }

    /** The mind this edge gave back, or null — set once, at the firing, forever. */
    public Human resurrected() {
        return resurrected;
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
        /**
         * Edges the book has let go (#852). It decides nothing, and it is
         * in the chain anyway: the walk alone cannot distinguish a book
         * that filled once from a book that has been turning over, so two
         * universes that end on the same 64 rows by different histories
         * would otherwise hash equal. The same reason D-010 puts an UNUSED
         * draw in the digest — a state change nobody read is still a state
         * change.
         */
        private int forgotten = 0;

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
            // The clause is asked EVERY tick, because deaths are not on a
            // wheel: an avatar dies whenever an Agent catches it, and the
            // question has to be answered before the death rule writes.
            // Weaving stays on the accrual window, because love is.
            arbitrate(tick);
            if (tick % Config.ACCRUE_EVERY_TICKS != 0) {
                return;
            }
            discover();
            weave();
            // Forgetting closes the window rather than opening it, so the
            // invariant is exact: when tick() returns, no CANDIDATE in the
            // book stands at or past BOND_FORGET_WINDOWS. The window that
            // makes the count is the window the edge leaves on, and the
            // slot it frees is open to the next window's discovery.
            forget();
        }

        /**
         * The Room 303 clause's entry test, and nothing else (#376). D-013
         * is this repository's oldest law — the avatar dies, the link
         * flatlines the brain, no exceptions — and this is the ONE canonical
         * exception, so the gate is narrow, ordered, and speaks when it
         * refuses.
         *
         * <p>Three questions, in the order the ruling states them:
         * <ol>
         * <li>does a mind stand at the far end of a {@link Bond} edge,
         *     dying, with a LIVING partner at the other end;</li>
         * <li>is that edge past {@code BOND_WEAVE_WINDOWS} — load-bearing,
         *     not a speed-run;</li>
         * <li>is the edge UNSPENT — once per edge, ever.</li>
         * </ol>
         * Three yeses and the clause fires. Anything less and D-013 runs
         * untouched, the flatline BAD line unchanged.
         *
         * <p>The refusals SPEAK, both of them, because a guard nobody can
         * observe is a guard nobody can trust: a spent edge says so, and so
         * does the one mind the clause may never save.
         *
         * <p>Cost: two O(1) field reads per edge per tick. The expensive
         * presence check is paid only once a death is already on the table,
         * which is the split the weave deliberately could not afford.
         */
        private void arbitrate(long tick) {
            for (int i = 0; i < edges.size(); i++) {
                Bond bond = edges.get(i);
                Human dying = dyingEndOf(bond);
                if (dying == null) {
                    continue;
                }
                if (dying.link().clause303) {
                    // One death, one edge. A mind can stand on several
                    // woven edges (Iris Kovacs stands on two at seed 42),
                    // and without this the same death would spend every one
                    // of them at once — many bonds consumed for one life,
                    // which is neither the ruling nor arithmetic. Mint order
                    // decides: the oldest edge answers.
                    world.log(Severity.TRACE, "Room 303: " + bond.pair()
                            + " — the death was already answered on an older edge");
                    continue;
                }
                Human partner = bond.other(dying);
                if (!partner.alive()) {
                    continue;
                }
                if (dying.link().avatar instanceof matrix.entities.TheOne) {
                    // D-045, and #327's contract: The One's death at Machine
                    // City stays canon. HIS bond story is the negotiation,
                    // not the kiss, and OneTrace's died==closed must hold in
                    // every universe.
                    world.log(Severity.SYS, "Room 303: refused — the fated do not get the kiss");
                    continue;
                }
                if (bond.state == State.CANDIDATE) {
                    continue;
                }
                if (bond.firedAt >= 0) {
                    world.log(Severity.BAD, "Room 303: refused — the edge is spent");
                    continue;
                }
                fire(bond, dying, partner, tick);
            }
        }

        /**
         * The firing, as an authorization (#376). This unit records that the
         * clause fired and spends the edge; the MOVES belong to its siblings
         * — the unwriting (#377), the scar conversion (#378), the ledger
         * price (#383), the awareness term (#380). For exactly this one
         * merge the clause authorizes and D-013 still writes the death; the
         * next unit makes the death not happen.
         */
        private void fire(Bond bond, Human dying, Human partner, long tick) {
            dying.link().clause303 = true;
            bond.firedAt = tick;
            bond.resurrected = dying;
            bond.resurrectedIndex = bond.a == dying ? bond.aIndex : bond.bIndex;
            world.log(Severity.FATE, "Room 303: " + partner.name
                    + " refuses the frame for " + dying.name
                    + " — a woven edge answers a death, and the edge is spent");
        }

        /**
         * The end of this edge that is dying RIGHT NOW: an open wire, an
         * avatar that has stopped being alive, and a world that still holds
         * it. The presence check is last on purpose — it is an O(census)
         * identity walk, and it only ever runs on a tick where somebody has
         * already died. Resurrecting an avatar the world no longer holds
         * would manufacture exactly the ghost D-013's four phases of
         * skeptics drove to zero.
         */
        private Human dyingEndOf(Bond bond) {
            if (dying(bond.a)) {
                return bond.a;
            }
            if (dying(bond.b)) {
                return bond.b;
            }
            return null;
        }

        private boolean dying(Human mind) {
            NeuralLink link = mind.link();
            return link != null && !link.closed() && !link.avatar.alive
                    && world.isPresent(link.avatar);
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
         * already-earned co-presence the candidate set derives from. Accrual
         * is cumulative, because time spent apart is not time unspent
         * together — a pair who drift and come back keep every window they
         * earned. What a window apart DOES cost is the run: the same
         * co-presence read that grants a window resets or advances
         * {@code apart}, and past {@code BOND_FORGET_WINDOWS} of them
         * {@link #forget} drops the edge (#852). One read, both clocks; the
         * forgetting costs the weave nothing.
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
                if (bond.state != State.CANDIDATE) {
                    continue;
                }
                if (!coPresent(bond.a, bond.b)) {
                    bond.apart++;
                    continue;
                }
                bond.apart = 0;
                bond.windows++;
                if (bond.windows >= Config.BOND_WEAVE_WINDOWS) {
                    bond.state = State.WOVEN;
                    world.log(Severity.FATE, "BOND woven: " + bond.pair()
                            + " — over " + bond.windows + " windows");
                }
            }
        }

        /**
         * The forgetting (#852). A CANDIDATE that has spent
         * {@code BOND_FORGET_WINDOWS} consecutive windows apart leaves the
         * book, and the world may notice somebody else in its slot.
         *
         * <p>Why a rule about the PAIR and not about the book's pressure.
         * The obvious shape is eviction by rank — a full book drops its
         * lowest-accrual candidate to admit a co-present newcomer — and it
         * says something this world does not mean: that love is perishable
         * only while the city is crowded, and that a pair who drifted apart
         * at window two keep their slot indefinitely as long as nobody else
         * wants it. Decay says the perishing is a fact about the two of
         * them. It also needs no newcomer to fire, so the book empties on
         * its own and the walk survives: removing from the middle of a list
         * leaves every survivor in mint order, which the ordering discipline
         * (#492) requires and a priority queue would have ended.
         *
         * <p>WOVEN and SCAR are untouchable — the loop never sees them,
         * because {@code apart} stops moving the moment an edge weaves and
         * every non-candidate row therefore stands at zero. A scar keeps its
         * slot by law (#378); a woven edge the world could forget would be a
         * bond the world forgot, which is a different feature and a worse
         * one.
         *
         * <p>Forgetting is not banishment. {@link #between} finds nothing
         * for a dropped pair, so the rotating walk may mint them again from
         * zero if their paths cross again — the world forgot them, it did
         * not rule on them. That is CANDIDATE renewal only and it leaves
         * D-045 open point (c) exactly where #494 left it: a spent edge is a
         * SCAR, a scar is never forgotten, and no pair walks back through
         * one.
         *
         * <p>A pair whose wire closed or whose avatar died stops being
         * co-present and is forgotten by the same clock, with no special
         * case: this loop is also the reason the book no longer holds edges
         * to minds that are gone.
         */
        private void forget() {
            for (int i = edges.size() - 1; i >= 0; i--) {
                Bond bond = edges.get(i);
                if (bond.state != State.CANDIDATE || bond.apart < Config.BOND_FORGET_WINDOWS) {
                    continue;
                }
                edges.remove(i);
                forgotten++;
                world.log(Severity.TRACE, "BOND forgotten: " + bond.pair()
                        + " — " + bond.apart + " windows apart, and the book lets go");
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
         * <p>Per edge: the pair as census positions, the weave state, and
         * BOTH clocks — the accrual count and the run apart (#852), because
         * an edge one window from being forgotten is not the same edge as a
         * freshly minted one and a fold that dropped the run would keep a
         * pair the live run let go. Positions rather than names because
         * names are not unique; the ordinal rather than the enum because a
         * sink speaks integers, and the ordinal is append-safe as long as
         * nobody reorders {@link State} — which is the same discipline every
         * enum in the chain already lives under. The registry's own
         * {@code forgotten} count leads, for the reason its field says.
         *
         * <p>What the digest sees here the v4.5 {@code Snapshot} must
         * retain (#179), so the root feeds this walk to BOTH sinks. If they
         * ever disagree, {@code SNAPSHOT == DIGEST} is the thing that
         * catches it, and it is checked in this unit rather than
         * discovered in a later one.
         */
        public void digestInto(matrix.core.StateSink sink) {
            sink.putInt(forgotten);
            sink.putCount(edges.size());
            for (int i = 0; i < edges.size(); i++) {
                Bond bond = edges.get(i);
                sink.putInt(bond.aIndex);
                sink.putInt(bond.bIndex);
                sink.putInt(bond.state.ordinal());
                sink.putInt(bond.windows);
                sink.putInt(bond.apart);
                // The firing (#376): when it happened, and who stood back up.
                // An edge that has paid is a different edge, and the fold has
                // to replay a world where it already paid.
                sink.putLong(bond.firedAt);
                sink.putInt(bond.resurrectedIndex);
            }
        }

        /** The registry's one line, spoken at boot and greppable forever after. */
        public String line() {
            return "BOND registry: edges=" + size() + " woven=" + woven()
                    + " forgotten=" + forgotten;
        }
    }
}
