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
    public static final int FLOCK_NEIGHBOR_RADIUS_CM = 8_000;
    public static final int FLOCK_SEPARATION_CM = 1_500;
    public static final int FLOCK_MAX_NEIGHBORS = 6;
    public static final int SWARM_RADIUS_CM = 3_000;
    public static final int COMMUTE_SWITCH_TICKS = 1_000;
    public static final int COMMUTE_ARRIVE_CM = 500;
    public static final int ECO_EVERY_TICKS = 100;
    public static final int GRACE_TICKS = 25;
    public static final int SMITH_SPEED_CM = 320;
    public static final int COPY_SPEED_CM = 240;

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
    // Parked life at ECO cadence: the event draw fires a birth on 0 and a
    // death on 1 — 1/8 each, a power of two so the compare is bit-exact
    // everywhere (the KID_SPIKE_DENOM precedent).
    public static final int LOD_AGG_EVENT_DENOM = 8;
    // The species draw's bound is lcm(1..12): modulo ANY candidate count a
    // parked region can offer (1..12 catalog rows) divides evenly, so the
    // pick is unbiased as well as bit-stable.
    public static final int LOD_AGG_SPECIES_BOUND = 27_720;

    private Config() {}
}
