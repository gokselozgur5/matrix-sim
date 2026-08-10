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

    public static final int EXILE_COUNT = 6;
    public static final int EXILE_COLLECT_EVERY_TICKS = 900;
    public static final int MYTH_EVERY_TICKS = 600;
    public static final int COOKIES_EVERY_AWAKENINGS = 5;

    public static final long SMITH_FORK_TICK = 1_500;
    public static final long AGENT_DECOMMISSION_TICK = 3_000;
    public static final int GRACE_TICKS = 25;
    public static final int SMITH_SPEED_CM = 320;
    public static final int COPY_SPEED_CM = 240;

    private Config() {}
}
