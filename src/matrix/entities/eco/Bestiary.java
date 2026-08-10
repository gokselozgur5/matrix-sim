package matrix.entities.eco;

import java.util.List;

/**
 * All species in one place (D-015): adding a creature to the universe is
 * one line here — and one new fate branch everywhere, so add with intent.
 * Caps are the D-018 budgets; periods are the cadence (flowers barely
 * think, birds think every tick).
 */
public final class Bestiary {
    public static final List<Species> ALL = List.of(
            new Species("sparrow", Kingdom.FAUNA_BIRD, MovementKind.FLOCK, 1, 60, 400),
            new Species("pigeon", Kingdom.FAUNA_BIRD, MovementKind.FLOCK, 1, 50, 350),
            new Species("crow", Kingdom.FAUNA_BIRD, MovementKind.FLOCK, 1, 30, 380),
            new Species("ant", Kingdom.FAUNA_INSECT, MovementKind.SWARM, 2, 80, 60),
            new Species("bee", Kingdom.FAUNA_INSECT, MovementKind.SWARM, 2, 40, 150),
            new Species("moth", Kingdom.FAUNA_INSECT, MovementKind.SWARM, 2, 30, 120),
            new Species("rose", Kingdom.FLORA, MovementKind.ROOTED, 16, 40, 0),
            new Species("oak", Kingdom.FLORA, MovementKind.ROOTED, 16, 20, 0),
            new Species("ivy", Kingdom.FLORA, MovementKind.ROOTED, 16, 30, 0),
            new Species("black cat", Kingdom.FAUNA_MAMMAL, MovementKind.WANDER, 4, 4, 180),
            new Species("stray dog", Kingdom.FAUNA_MAMMAL, MovementKind.WANDER, 4, 6, 220),
            new Species("rain", Kingdom.WEATHER, MovementKind.DRIFT, 1, 70, 500));

    private Bestiary() {}
}
