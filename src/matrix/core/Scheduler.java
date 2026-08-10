package matrix.core;

/**
 * The tick-rate wheel (D-018): cadence as arithmetic. Phase is spread by
 * entity id so a whole species never thinks on the same tick; the answer
 * is a pure function of (tick, period, id) — deterministic by construction.
 */
public final class Scheduler {

    private Scheduler() {}

    public static boolean due(long tick, int period, int id) {
        if (period <= 1) {
            return true;
        }
        return (tick + id) % period == 0;
    }
}
