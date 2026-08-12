package matrix.core;

/** Every tunable in one place (D-006 discipline). City-scale centimeters, not screen numbers (D-004). */
public final class Config {
    public static final int WORLD_W_CM = 400_000;
    public static final int WORLD_H_CM = 200_000;
    public static final int CONTACT_RADIUS_CM = 100;

    public static final int BLUE_START = 192;
    public static final int RED_START = 4;
    public static final int AGENT_START = 6;

    public static final int BLUE_SPEED_CM = 120;
    public static final int RED_SPEED_CM = 200;
    public static final int AGENT_SPEED_CM = 300;
    public static final int FLEE_TRIGGER_CM = 3_000;

    public static final double AGENT_KILL_CHANCE = 0.10;
    public static final int AWAKEN_EVERY_TICKS = 50;
    public static final int RED_CAP = 20;

    public static final int METRIC_EVERY_TICKS = 100;
    public static final int DIGEST_EVERY_TICKS = 100;
    public static final int FOLLOW_EVERY_TICKS = 100;
    public static final int ZION_EVERY_TICKS = 100;

    public static final int EXILE_COUNT = 6;
    public static final int EXILE_COLLECT_EVERY_TICKS = 900;
    public static final int MYTH_EVERY_TICKS = 600;
    public static final int COOKIES_EVERY_AWAKENINGS = 5;

    public static final long SMITH_FORK_TICK = 1_500;
    public static final long AGENT_DECOMMISSION_TICK = 3_000;

    public static final int ONE_SPEED_CM = 350;
    public static final double OVERFLOW_FRACTION = 0.62;
    public static final long LEDGER_BOUND = 30_000;
    public static final int ACCRUE_EVERY_TICKS = 10;
    public static final long RESIDUE_BLUE = 1;
    public static final long RESIDUE_RED = 8;
    public static final int NEGO_TICKS = 40;
    public static final int PEACE_TICKS = 900;
    public static final int OPTOUT_COUNT = 6;
    public static final long DEJA_RESIDUE_SPIKE = 250;
    // The Kid family (D-033, dossier #96 section 3): spike-only personal
    // residue on BLUE links; threshold in [KID_BASE, KID_BASE+KID_JITTER)
    // needs 5-7 spikes at lambda ~1.17/link per 6000-tick run — measured
    // ~once per 2-5M link-ticks over the unit PR's seed sweep. The first
    // family tried (140/40, 4e-4, 8) needed 18+ spikes and could never
    // fire; lowered once, per the sweep, to the dossier's own numbers.
    // 1/512 is a power of two: the chance compare is bit-exact everywhere.
    public static final long KID_BASE = 144;
    public static final int KID_JITTER = 48;
    public static final int KID_SPIKE_DENOM = 512;
    public static final long KID_SPIKE = 24;

    public static final int HASH_CELL_CM = 5_000;
    // The ring hunt's displacement law (#135): the largest legal single-tick
    // gait displacement is the sparrow's flock heading (±400, ±400) — 566 cm
    // euclid; SWARM tops out at 1.5x bee speed = 225, stepToward at ONE_SPEED
    // 350 -> 495. The bound rounds up with headroom; anything past it (rain's
    // ground recycle, an exile gone to ground) rides the far-mover ledger
    // instead. Correctness never depends on this number — only ring width does.
    public static final int HUNT_DISP_BOUND_CM = 640;
    public static final long HUNT_DISP_BOUND_SQ_CM2 =
            (long) HUNT_DISP_BOUND_CM * HUNT_DISP_BOUND_CM;
    /**
     * The hunt referee (#135): with {@code -Dmatrix.huntVerify=true} every
     * ring hunt replays the linear scan it replaced and throws on the first
     * divergence — object identity, not equals. Ships false; equivalence
     * evidence runs flip it. Results never depend on it: the ring is exact,
     * the referee only proves it.
     */
    public static final boolean HUNT_VERIFY = Boolean.getBoolean("matrix.huntVerify");
    public static final int FLOCK_NEIGHBOR_RADIUS_CM = 8_000;
    public static final int FLOCK_SEPARATION_CM = 1_500;
    public static final int FLOCK_MAX_NEIGHBORS = 6;
    public static final int SWARM_RADIUS_CM = 3_000;
    public static final int COMMUTE_SWITCH_TICKS = 1_000;
    public static final int COMMUTE_ARRIVE_CM = 500;
    public static final int ECO_EVERY_TICKS = 100;
    /** The dial's legal range — the city is large, not infinite (#136). */
    public static final int ECO_SCALE_MIN = 1;
    public static final int ECO_SCALE_MAX = 100;
    /**
     * The homecoming dial (#136) — the one deliberately mutable knob in the
     * file, and since #882 the only one nothing can write behind the gate's
     * back. {@code --scale N} multiplies every Bestiary population at seeding
     * (x11 puts ~5,269 entities in the city, the D-027 retargeted row's
     * scale); humans, agents, exiles and the arc keep their canon counts. 1 is
     * canonical and multiplies into byte-identical digests by construction.
     * Refused alongside --replay and --chronos: the fold's genesis line does
     * not carry a scale, so a scaled recording would be a lie.
     */
    private static int ecoScale = ECO_SCALE_MIN;
    /**
     * The dial's seal. #136 wrote its law as prose — "written once by Main
     * before any Simulation exists, never after" — and prose enforces nothing.
     * The seal closes on whichever comes first, the write or the first read:
     * after that the dial is what the world was built from, and a second write
     * could only make the label disagree with the city. One flag, both clauses.
     */
    private static boolean dialSealed = false;

    /**
     * The dial as the world reads it, and the act that seals it (#882). Every
     * read goes through here because the field is private, so "nothing writes
     * the dial after the world has looked at it" needs no cooperation from the
     * reader — a future world-boot path that never heard of this law still
     * seals the dial by using it.
     */
    public static int ecoScale() {
        dialSealed = true;
        return ecoScale;
    }

    /**
     * The dial's only door (#882). #826 gave the dial one law and pointed the
     * two known doors at it; the field stayed public and mutable, so the law
     * was something a caller chose to walk through. This is the wall behind
     * it: an out-of-range scale is refused with {@link #scaleRefusal}'s own
     * sentence, and a write to a sealed dial is refused whatever its value.
     *
     * <p>Both refusals throw. The two CLI doors ask {@code scaleRefusal}
     * first and die with a sentence and exit 2, because a stack trace is not
     * a user-facing refusal; this method is what makes the third door — the
     * one nobody has written yet — impossible rather than merely discouraged.
     *
     * @throws IllegalArgumentException when the scale is outside 1..100
     * @throws IllegalStateException when the dial is already sealed
     */
    public static void setEcoScale(int scale) {
        String refusal = scaleRefusal(scale);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal);
        }
        if (dialSealed) {
            throw new IllegalStateException("the homecoming dial is written once,"
                    + " before the world reads it — it already carries " + ecoScale);
        }
        ecoScale = scale;
        dialSealed = true;
    }

    /**
     * The dial's one gate (#826): {@code null} when the scale is legal, the
     * refusal sentence when it is not. Two doors open onto the dial — the
     * daemon's {@code --scale} and the probe bench's positional scale — and
     * until this method existed only one of them was guarded, so
     * {@code AllocMeter 42 0} printed a well-formed D-027 budget row for a
     * city with no ecosystem at all: the seeding loop
     * ({@code populationCap * ecoScale()}) never runs at 0 or below, and the
     * bare human census measures like a triumph. One law, stated here, obeyed
     * at both doors; each door still chooses how to die (the daemon exits 2).
     */
    public static String scaleRefusal(int scale) {
        if (scale < ECO_SCALE_MIN || scale > ECO_SCALE_MAX) {
            return "--scale wants " + ECO_SCALE_MIN + ".." + ECO_SCALE_MAX
                    + " — the city is large, not infinite";
        }
        return null;
    }

    /**
     * The dial's gate, asserted rather than commented (#882, the
     * {@link matrix.realworld.Bond#retailOrderLine()} precedent). Called from
     * --selftest, after the doors have had their turn, so both refusals are
     * live: an out-of-range write and a write to the sealed dial are each
     * attempted here and each must be refused. A gate that stops refusing
     * fails the build instead of quietly re-opening #826 for the next probe.
     *
     * @throws IllegalStateException when either refusal no longer refuses
     */
    public static String dialLockLine() {
        String broken = null;
        try {
            setEcoScale(ECO_SCALE_MAX + 1);
            broken = "scale " + (ECO_SCALE_MAX + 1) + " was accepted";
        } catch (IllegalArgumentException expected) {
            // the daemon's own sentence, from the law both doors read
        }
        if (broken == null && !dialSealed) {
            // Main writes the dial before it dispatches, so an unsealed dial
            // here means the door stopped using the setter — the rewrite probe
            // below would seal it itself and report a pass it did not earn.
            broken = "the dial reached the selftest unsealed";
        }
        if (broken == null) {
            try {
                // the same value it already carries: the refusal is about the
                // moment, not the number.
                setEcoScale(ecoScale);
                broken = "the sealed dial was written again";
            } catch (IllegalStateException expected) {
                // written once, before the world reads it
            }
        }
        if (broken != null) {
            throw new IllegalStateException("DIAL gate broken: " + broken
                    + " — the homecoming dial is written through setEcoScale or"
                    + " not at all, which is the whole of #882");
        }
        return "DIAL gate held: eco_scale=" + ecoScale + " sealed=" + dialSealed
                + " — out of range refused, rewrite refused";
    }
    public static final int GRACE_TICKS = 25;
    public static final int SMITH_SPEED_CM = 320;
    public static final int COPY_SPEED_CM = 240;

    // D-008 substrate (crowns #32, #124): which compute story runs, and its
    // knobs. PROCESSOR is canon per the #19 verdict; BATTERY keeps the
    // zero-cost flavor path — flip here for the A/B, digest family unchanged.
    public static final matrix.machine.ComputeModel COMPUTE_MODEL =
            matrix.machine.ComputeModel.PROCESSOR;
    /** Budget reference: the full farm at boot (BLUE_START + RED_START) — a constant, never a live read. */
    public static final int PODS_REFERENCE = 196;
    /** The budget may starve the map down to this many HOT slots, never below. */
    public static final int SLOTS_FLOOR = 2;
    /** Below this many plugged pods the emergency cadence stretch engages. */
    public static final int PODS_MIN = 64;

    public static final int RIG_CAPACITY = 3;
    public static final int RIG_STATION_TICKS = 400;
    public static final int TRANSIT_TICKS = 150;
    public static final long SHIP_LOSS_TICK = -1; // #119 scenario knob: -1 = off, the canonical fate
    public static final int FLEET_MAX = 2;
    public static final int EXIT_REACH_CM = 300;
    public static final int RECALL_TIMEOUT_TICKS = 200;
    public static final int LOD_LINGER_TICKS = 200;
    public static final int LOD_COLD_STRETCH = 4;
    public static final int ATTN_EVERY_TICKS = 100;
    // D-024 P2, the parking family (#132): a region un-HOT for this many
    // CONSECUTIVE ticks parks its catalog residents into the aggregate.
    public static final int LOD_PARK_AFTER_TICKS = 600;
    // A Park the flush cannot perform — refused by a self-replicating resident,
    // or nothing catalog left to fold — changes no state, so the gatekeeper's
    // level-triggered test stays true and re-proposes it on the very next tick.
    // The deferral gives that outcome a memory: the region re-arms only after
    // this many further cold ticks. One ECO beat, the cadence parked reality
    // itself breathes at — the question is re-asked at the rate the world can
    // plausibly have changed its answer, not once per tick (#807).
    public static final int LOD_PARK_RETRY_TICKS = 100;
    // Parked life at ECO cadence: the event draw fires a birth on 0 and a
    // death on 1 — 1/8 each, a power of two so the compare is bit-exact
    // everywhere (the KID_SPIKE_DENOM precedent).
    public static final int LOD_AGG_EVENT_DENOM = 8;
    // The species draw's bound is lcm(1..12): modulo ANY candidate count a
    // parked region can offer (1..12 catalog rows) divides evenly, so the
    // pick is unbiased as well as bit-stable.
    public static final int LOD_AGG_SPECIES_BOUND = 27_720;

    // ── The heart (D-045, gate #215) ───────────────────────────────────────
    // Bonds are EARNED, never drawn: a candidate pair is two minds whose
    // avatars keep standing near each other on accrual windows. The knobs
    // below are the whole formation story, and retuning any of them is a
    // commit (D-006) — not a flag, not an argument.
    /**
     * Co-presence radius. 20 m: close enough that the city put them in the
     * same room, wide enough that two commuters who arrive at the same
     * district count as together (COMMUTE_ARRIVE_CM is 500, so a shared
     * destination lands well inside this). Not the contact radius — an
     * Agent's kill reach is 1 m and love is not a collision.
     */
    public static final int BOND_NEAR_CM = 2_000;
    /**
     * Bounded discovery (D-018/D-027): pairs OFFERED per accrual window. The
     * scan is a rotating walk of the pair space, so cost per window is this
     * constant and never a function of the census — a candidate set that
     * grows with the square of the population is a performance bug wearing a
     * love story.
     */
    public static final int BOND_SCAN_PAIRS = 24;
    /**
     * The book's ceiling. Past this the world stops noticing new pairs: the
     * registry is walked by the digest and by the clause's guard, and an
     * unbounded book would make both unbounded.
     */
    public static final int BOND_MAX_EDGES = 64;
    /**
     * The guard rail against speed-run love (#215): accrual windows of
     * co-presence a candidate edge must earn before it is load-bearing.
     * Twelve windows is 120 ticks at the current wheel — long enough that a
     * pair who merely passed each other in a corridor never qualifies, short
     * enough that two commuters sharing a district (COMMUTE_SWITCH_TICKS is
     * 1,000) cross it well inside one posting. This is the number the Room
     * 303 clause is forbidden to fire below, so retuning it retunes the
     * miracle's admission price — a commit, and an argued one (D-006).
     */
    public static final int BOND_WEAVE_WINDOWS = 12;
    /**
     * The price of the miracle, and the most expensive line on the retail
     * price list (#212's unifier): the list is ordered by how hard a frame
     * is to reject, and no frame is harder than "you are dead". Refusing it
     * is therefore the maximal disbelief event, and this constant must
     * stand strictly above every other disbelief item this repo prices —
     * RESIDUE_RED, RESIDUE_BLUE, KID_SPIKE, DEJA_RESIDUE_SPIKE, and the
     * allegiance terms arriving in #332/#345. That ordering is not a
     * comment: {@code Bond.retailOrderLine()} asserts it inside --selftest,
     * so a retune that silently demotes the miracle fails the build.
     *
     * <p>Scale: 16x the deja-vu spike, and 13% of LEDGER_BOUND — one
     * miracle is a serious dent in the debt that grows The One, and seven
     * of them would summon him outright. That is the intended shape. The
     * debt belongs to the WORLD (#383), so this number is what the world
     * pays, never what the lover pays.
     */
    public static final long ROOM_303_DEPOSIT = 4_000;

    // ── The inward door (D-046, gate #216) ─────────────────────────────────
    // The door that opens the other way, priced. These four shipped on the
    // classes that read them because core/ belonged to another crew while
    // Season Three built the door; #862 brings them home. The values are the
    // values they were and the chain is byte-identical across the move — a
    // constant that moves when it changes files was never really a constant.
    // What a relocation DOES move is the genesis fingerprint, which hashes
    // this file and only this file: recordings cut before the move are
    // refused as foreign physics by a build after it, and the universe they
    // recorded is the same universe.
    /**
     * The petition threshold's floor, and with {@link #PETITION_JITTER} its
     * span (#335): a freed mind's breaking point is
     * {@code PETITION_BASE + [0, PETITION_JITTER)} — 48..143 — derived from
     * the NAME and never drawn, the D-033 KID precedent read backwards.
     * Nobody scripts Cypher: the propensity is birth data.
     */
    public static final long PETITION_BASE = 48;
    /** The span above {@link #PETITION_BASE} the name selects within (#335). */
    public static final int PETITION_JITTER = 96;
    /**
     * What one death costs every living freed mind's account (#335). At 24,
     * the cheapest mind in the city needs two funerals and the dearest six —
     * the griefs are the world's own, and no draw is spent on either.
     */
    public static final long PETITION_GRIEF_SPIKE = 24;
    /**
     * The treaty's reinsertion clause (#338): grants per run, until D-049's
     * regime gives the treaty its own text. Two is not a placeholder — the
     * whole census at canonical scale is six citizens, and a quota that
     * could take them all is not a bargain, it is a harvest, and the door
     * would stop being a door.
     */
    public static final int REINSERTION_QUOTA = 2;

    private Config() {}
}
