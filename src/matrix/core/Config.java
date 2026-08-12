package matrix.core;

import matrix.entities.eco.Bestiary;
import matrix.entities.eco.MovementKind;
import matrix.entities.eco.Species;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    /**
     * The ring hunt's displacement law (#135). Ring <i>d</i>'s live-distance
     * floor is {@code (d-1)*HASH_CELL_CM - HUNT_DISP_BOUND_CM}; anything that
     * outruns the bound latches onto the far-mover ledger
     * ({@code MatrixEntity.noteDisplacement}) and every hunt sweeps that
     * ledger <b>linearly</b> after the rings.
     *
     * <p>Correctness never depends on this number — the ledger keeps the
     * answer exact at any value, which is why lowering it moves no digest.
     * The <b>complexity guarantee</b> does depend on it: the ledger term is
     * (movers past the bound) x (hunts per tick), and both factors scale with
     * population, so a gait that outgrows the bound is #135's quadratic term
     * coming back. Measured at seed 42 over 6,000 ticks, two species crossing
     * multiplies ledger candidates 423x (2,912 to 1,232,801) with a
     * byte-identical digest and a passing selftest (#825).
     *
     * <p>Which is why the derivation below is executed rather than asserted.
     * The comment this replaced named three gait maxima by hand and was
     * exactly right on the day it was written; the day a gait outgrew it,
     * nothing anywhere would have said so.
     */
    public static final int HUNT_DISP_BOUND_CM = 640;
    public static final long HUNT_DISP_BOUND_SQ_CM2 =
            (long) HUNT_DISP_BOUND_CM * HUNT_DISP_BOUND_CM;

    /** A gait that is not bounded: a declared teleport, and therefore a declared ledger tenant. */
    private static final int GAIT_TELEPORTS = -1;

    /**
     * One mover's single-tick reach: what moves, and the largest euclidean
     * displacement one tick of its gait can put between it and the snapshot
     * the hunt buckets were built on. The label is the key the bench measures
     * against — a catalog row is {@code eco:<species id>}, anything else is
     * its class name.
     */
    public record GaitReach(String mover, int maxDisplacementCm) {}

    /**
     * Every bounded gait in the world, widest first — the derivation
     * {@link #HUNT_DISP_BOUND_CM} used to state in prose (#825).
     *
     * <p>The eco half reads the catalog, so adding a species is covered for
     * free; the named half is the program society, whose gaits spend the
     * speed constants above through {@code MatrixEntity.stepToward} and
     * {@code wander}. Both doors are Chebyshev — a clamped step per axis —
     * so a gait's reach is its per-axis step on the diagonal.
     *
     * <p>{@code Avatar} carries the RED speed rather than the BLUE one
     * because a pill is a field, not a type: the same object commutes at 120
     * on one tick and flees at 200 on the next. {@code Agent} likewise
     * carries the hunting speed it spends outside PEACE.
     */
    public static List<GaitReach> huntGaitReaches() {
        List<GaitReach> reaches = new ArrayList<>();
        for (Species s : Bestiary.ALL) {
            int axis = gaitAxisStepCm(s);
            if (axis != GAIT_TELEPORTS) {
                reaches.add(new GaitReach("eco:" + s.id(), diagonalCm(axis)));
            }
        }
        reaches.add(new GaitReach("TheOne", diagonalCm(ONE_SPEED_CM)));
        reaches.add(new GaitReach("SmithPrime", diagonalCm(SMITH_SPEED_CM)));
        reaches.add(new GaitReach("Agent", diagonalCm(AGENT_SPEED_CM)));
        reaches.add(new GaitReach("AgentSmith", diagonalCm(AGENT_SPEED_CM)));
        reaches.add(new GaitReach("SmithCopy", diagonalCm(COPY_SPEED_CM)));
        reaches.add(new GaitReach("Avatar", diagonalCm(RED_SPEED_CM)));
        reaches.add(new GaitReach("Oracle", diagonalCm(BLUE_SPEED_CM)));
        reaches.sort(Comparator.comparingInt(GaitReach::maxDisplacementCm).reversed());
        return reaches;
    }

    /**
     * The movers that ride the far-mover ledger by design: rain's ground
     * recycle ({@code DriftMovement} wraps the field) and an exile gone to
     * ground ({@code ExileProgram.handleDeletion} places at a fresh draw).
     * An exile walks a bounded 120 the rest of the time; it is listed here
     * because one of its two doors is a teleport, and a mover is a tenant if
     * any of its doors is.
     */
    public static List<String> huntTeleporters() {
        List<String> out = new ArrayList<>();
        for (Species s : Bestiary.ALL) {
            if (gaitAxisStepCm(s) == GAIT_TELEPORTS) {
                out.add("eco:" + s.id());
            }
        }
        out.add("ExileProgram");
        return out;
    }

    /**
     * The far-mover ledger's stated ceiling: every declared tenant teleporting
     * on the same tick. It is deliberately loose — seed 42 peaks at 2 over
     * 6,000 ticks against a ceiling of 76 — because it is the backstop, not
     * the tight check. The tight check is {@link #huntBoundLine()}, which
     * reads the gait table and needs no run at all; this one catches what a
     * table cannot see, namely a mover that reaches the ledger through a door
     * nobody declared. Scales with {@code --scale}, because its tenants do.
     */
    public static int huntLedgerCeiling() {
        int tenants = EXILE_COUNT;
        for (Species s : Bestiary.ALL) {
            if (gaitAxisStepCm(s) == GAIT_TELEPORTS) {
                tenants += s.populationCap() * ecoScale();
            }
        }
        return tenants;
    }

    /**
     * The displacement law as a lock rather than a comment (#825), printed by
     * {@code --selftest} beside the retail-order line it is modelled on
     * (#382): a bound the gaits have outgrown fails the build instead of
     * quietly re-growing the term #135 was cut to remove.
     *
     * <p>It throws rather than warns, and that is the argued part. Crossing
     * the bound is not incorrect, so nothing here is protecting an answer —
     * it is protecting a budget that no digest, no selftest and no referee
     * can see move. The remedy is one line: raise {@code
     * HUNT_DISP_BOUND_CM} to the printed figure and the rings widen by one
     * cell's worth of arithmetic, which is the trade this constant exists to
     * make. What the throw refuses is making that trade by accident.
     *
     * @throws IllegalStateException when a bounded gait can outrun the bound
     */
    public static String huntBoundLine() {
        GaitReach widest = huntGaitReaches().get(0);
        if (widest.maxDisplacementCm() > HUNT_DISP_BOUND_CM) {
            throw new IllegalStateException("HUNT bound outgrown: " + widest.mover()
                    + " reaches " + widest.maxDisplacementCm() + " cm in one tick and"
                    + " HUNT_DISP_BOUND_CM=" + HUNT_DISP_BOUND_CM + " — every tick of that"
                    + " gait now latches onto the far-mover ledger, which every hunt sweeps"
                    + " linearly. Raise the bound to at least " + widest.maxDisplacementCm()
                    + " and the rings widen instead (#825).");
        }
        return "HUNT bound held: HUNT_DISP_BOUND_CM=" + HUNT_DISP_BOUND_CM + " > "
                + widest.mover() + "=" + widest.maxDisplacementCm()
                + " (headroom " + (HUNT_DISP_BOUND_CM - widest.maxDisplacementCm())
                + " cm), ledger ceiling=" + huntLedgerCeiling();
    }

    /**
     * One catalog row's largest per-axis step, or {@link #GAIT_TELEPORTS}.
     *
     * <p>The switch is exhaustive with no {@code default} on purpose: a
     * thirteenth gait added to {@link MovementKind} does not compile until
     * somebody states what one tick of it can spend. That is the half of
     * #825 a runtime check cannot reach — the instance is caught by the
     * arithmetic below, the class is caught by the compiler.
     */
    private static int gaitAxisStepCm(Species s) {
        return switch (s.movement()) {
            // One clamped speed per axis: FlockMovement clamps its heading,
            // WanderMovement draws inside +/-speed, CommuteMovement clamps
            // its step toward the destination.
            case FLOCK, WANDER, COMMUTE -> s.speedCm();
            // SwarmMovement draws inside +/-speed and then adds a half-speed
            // pull toward the nearest of its own kind.
            case SWARM -> s.speedCm() + s.speedCm() / 2;
            // Flowers hold the line.
            case ROOTED -> 0;
            // DriftMovement wraps the field: the ledger's intended tenant.
            case DRIFT -> GAIT_TELEPORTS;
        };
    }

    /** Both axes spent at once, rounded up — the diagonal is the worst case a Chebyshev step can reach. */
    private static int diagonalCm(int axisStepCm) {
        return ceilSqrt(2L * axisStepCm * axisStepCm);
    }

    /** Exact integer ceiling square root: the seed is a double, the answer is corrected by integers. */
    private static int ceilSqrt(long v) {
        int r = (int) Math.sqrt((double) v);
        while ((long) r * r < v) {
            r++;
        }
        while (r > 0 && (long) (r - 1) * (r - 1) >= v) {
            r--;
        }
        return r;
    }
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
