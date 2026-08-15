package matrix.entities.eco;

import java.util.ArrayList;
import java.util.List;

/**
 * All species in one place (D-015): adding a creature to the universe is
 * one line here — and one new fate branch everywhere, so add with intent.
 * Caps are the D-018 budgets; periods are the cadence (flowers barely
 * think, birds think every tick).
 *
 * <p>Two lists, because two different questions are asked of this file and
 * they have different answers (#974). {@link #CATALOG} is what SEEDS and
 * PARKS — the rows {@code Simulation} spends its caps on and the only rows
 * a region may fold (D-024 P2). {@link #EVERY} is what EXISTS, catalog plus
 * the one-offs the arc mints, and it is the one a budget over "every
 * species" wants. The field that used to be called {@code ALL} was the
 * first of these wearing the name of the second, and every derivation taken
 * over it inherited the hole by default: the sunrise was a full catalog row
 * — kingdom, gait, cadence, cap, speed — written inline at the call site
 * that spawned it, where no reader could see it and no budget could count
 * it. The rename is the lock: {@code Bestiary.ALL} no longer compiles, so
 * the next derivation states which of the two sets it means.
 */
public final class Bestiary {
    /**
     * The catalog proper: seeded at boot, capped by D-018, parkable by
     * D-024 P2. NOT the census of what the world renders — {@link #EVERY}
     * is.
     */
    public static final List<Species> CATALOG = List.of(
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

    /**
     * Sati's sunrise, painted once at the reboot ({@code MachineCity}) — it
     * was never in the spec, and nobody will delete it. Rooted at speed 0,
     * so it costs the ring hunt nothing today; the row is here rather than
     * inline at the spawn so that the day somebody gives it a gait, the
     * gait is in the table that judges gaits.
     */
    public static final Species SUNRISE =
            new Species("sunrise", Kingdom.OBJECT, MovementKind.ROOTED, 16, 1, 0);

    /**
     * Rows the story mints one at a time: not seeded, not capped by the
     * seeding loop, never parked. A one-off is still catalog DATA (D-015) —
     * it carries a kingdom, a gait, a cadence and a speed like any other
     * row — so it lives in this file, where the readers are.
     *
     * <p><b>The cap on a one-off row binds nothing, and saying so is the
     * point.</b> {@code SUNRISE} carries {@code populationCap = 1} and no
     * reader enforces it: the seeding loop never reaches a one-off, and
     * {@code huntLedgerCeiling} consults a cap only for a row that can
     * teleport, which a ROOTED sunrise does not. The field is the shape #1113
     * names — a constant that looks like a bound, is documented as a bound,
     * and cannot cause anything to happen. It stays 1 because it is the true
     * count and a reader deriving "how many of these exist" is right; it is
     * not load-bearing, and the next one-off inherits a number that has never
     * bitten. Enforcing it would be a decision (#1113), not a patch.
     */
    public static final List<Species> ONE_OFFS = List.of(SUNRISE);

    /**
     * Every species the world can render, catalog first then one-offs.
     * Anything derived from what the world CONTAINS asks this: the ring
     * hunt's displacement bound (D-027) does, because a one-off displaces
     * exactly as hard as its speed says and the ledger cannot tell where
     * its row was written.
     */
    public static final List<Species> EVERY = every();

    private static List<Species> every() {
        List<Species> every = new ArrayList<>(CATALOG);
        every.addAll(ONE_OFFS);
        return List.copyOf(every);
    }

    private Bestiary() {}
}
