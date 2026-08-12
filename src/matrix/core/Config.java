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
    /**
     * The homecoming dial (#136) — the one deliberately mutable knob in the
     * file. {@code --scale N} multiplies every Bestiary population at seeding
     * (x11 puts ~5,269 entities in the city, the D-027 retargeted row's
     * scale); humans, agents, exiles and the arc keep their canon counts.
     * Written once by Main before any Simulation exists, never after; 1 is
     * canonical and multiplies into byte-identical digests by construction.
     * Refused alongside --replay and --chronos: the fold's genesis line does
     * not carry a scale, so a scaled recording would be a lie.
     */
    public static int ECO_SCALE = 1;
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

    private Config() {}
}
