package matrix.core;

/** Every tunable in one place (D-006 discipline). City-scale centimeters, not screen numbers (D-004). */
public final class Config {
    public static final int WORLD_W_CM = 400_000;
    public static final int WORLD_H_CM = 200_000;
    public static final int CONTACT_RADIUS_CM = 100;

    public static final int METRIC_EVERY_TICKS = 100;
    public static final int DIGEST_EVERY_TICKS = 100;

    private Config() {}
}
