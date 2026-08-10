package matrix.entities.eco;

/**
 * One catalog row = one kind of thing in the world (D-015).
 * A thousand kinds is a thousand rows, never a thousand classes.
 * tickPeriod is the cadence budget of D-018: flowers barely think.
 */
public record Species(String id, Kingdom kingdom, MovementKind movement,
                      int tickPeriod, int populationCap, int speedCm) {
    public Species {
        if (tickPeriod < 1 || populationCap < 0 || speedCm < 0) {
            throw new IllegalArgumentException("bad catalog row: " + id);
        }
    }
}
