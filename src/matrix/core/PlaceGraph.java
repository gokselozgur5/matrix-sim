package matrix.core;

import java.util.List;

/**
 * The semantic layer of D-004: places are addresses, not coordinates.
 * v1 ships the skeleton — named zones and phone-booth exit nodes at fixed
 * fractions of the city (data, not randomness: the map is not a dice roll).
 * Chases get their fiction: a fleeing mind runs for an exit, not for infinity.
 */
public final class PlaceGraph {

    public record Zone(String name, Position center) {}

    private final List<Zone> zones;
    private final List<Position> exits;

    public PlaceGraph(int worldWidthCm, int worldHeightCm) {
        int w = worldWidthCm;
        int h = worldHeightCm;
        this.zones = List.of(
                new Zone("downtown", new Position(w / 2, h / 2)),
                new Zone("industrial district", new Position(w / 6, h / 4)),
                new Zone("chinatown", new Position(w / 4, (3 * h) / 4)),
                new Zone("financial district", new Position((3 * w) / 4, h / 4)),
                new Zone("old city", new Position((5 * w) / 6, (3 * h) / 4)),
                new Zone("the loop", new Position(w / 2, h / 6)));
        this.exits = List.of(
                new Position(w / 8, h / 2),
                new Position(w / 2, h / 8),
                new Position((7 * w) / 8, h / 2),
                new Position(w / 2, (7 * h) / 8),
                new Position(w / 4, h / 4),
                new Position((3 * w) / 4, (3 * h) / 4));
    }

    public List<Zone> zones() {
        return zones;
    }

    public List<Position> exits() {
        return exits;
    }

    /** Nearest phone booth; ties break by list order — deterministic. */
    public Position nearestExit(Position from) {
        Position best = exits.get(0);
        long bestD = from.euclidSqCm(best);
        for (int i = 1; i < exits.size(); i++) {
            long d = from.euclidSqCm(exits.get(i));
            if (d < bestD) {
                bestD = d;
                best = exits.get(i);
            }
        }
        return best;
    }
}
