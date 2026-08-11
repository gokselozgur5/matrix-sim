package matrix.zion;

import matrix.core.Config;
import matrix.core.Severity;
import matrix.core.World;
import matrix.realworld.Human;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The city: the free-human registry (D-011 cashed in — every liberation
 * lands HERE, somewhere real) and its economy (crown #84): the census is
 * spent on the fleet — crews drawn seeded from citizens, sorties scheduled
 * under the rig's budget. The census grows only through {@link #absorb},
 * and absorb order is liberation order: the root — the only class allowed
 * to hold both banks (D-012) — drains RealWorld's pending liberations into
 * this door each tick, in link registration order. Refuses: deleting a
 * Human (liberation, not deletion — the fallen stay on the registry),
 * touching pods or links of the still-plugged, Matrix-side anything, and
 * printing — it returns its {@code ZION} line; only the root emits.
 */
public final class Zion {
    private final List<Human> census = new ArrayList<>();
    /** Index-aligned with the census: where each citizen came from ("treaty" today; #121 adds the Kid's own tag). */
    private final List<String> origins = new ArrayList<>();
    /** The fleet: ships built and lost here (composition — crown #84). */
    private final List<Hovercraft> fleet = new ArrayList<>();
    private final World world;

    public Zion(World world) {
        this.world = world;
    }

    /** The door on this side: one freed Human enters the census — link already closed clean, nothing flushed. */
    public void absorb(Human human, String origin) {
        census.add(human);
        origins.add(origin);
    }

    /**
     * The canonical zion slot, per crown #84: absorb (the root's drain,
     * already landed) → fleet tick → sortie scheduling. The scheduler is
     * the minimal one this floor needs: ONE ship, laid down the first time
     * the census can man a full channel board, launched whenever it is
     * docked and {@code RIG_CAPACITY} citizens are free — alive, wearing
     * no wire. Draws happen only when a launch actually happens; an idle
     * wing consumes no fate.
     */
    public void tick(long tick) {
        if (fleet.isEmpty() && census.size() >= Config.RIG_CAPACITY) {
            fleet.add(new Hovercraft("the Nebuchadnezzar"));
            world.log(Severity.FATE,
                    "the first hull: the Nebuchadnezzar joins the fleet — the census learns to fly");
        }
        for (Hovercraft ship : fleet) {
            ship.tick(world);
        }
        for (Hovercraft ship : fleet) {
            if (ship.state() == Hovercraft.MissionState.DOCKED) {
                List<Human> drawn = drawCrew();
                if (!drawn.isEmpty()) {
                    ship.launch(drawn, world);
                }
            }
        }
    }

    /**
     * The seeded crew draw (crown #84): the free pool is walked in census
     * order — alive, no wire — and each of the {@code RIG_CAPACITY} berths
     * is filled by one {@code world.rng()} draw from what remains; crew
     * order is draw order. The minimal scheduler mans every channel, so
     * crew size equals rig capacity — #116 makes staffing a real decision.
     * Fewer than a full crew free: nobody flies, and nothing is drawn.
     */
    private List<Human> drawCrew() {
        List<Human> free = new ArrayList<>();
        for (Human h : census) {
            if (h.alive() && h.link() == null) {
                free.add(h);
            }
        }
        if (free.size() < Config.RIG_CAPACITY) {
            return List.of();
        }
        List<Human> crew = new ArrayList<>(Config.RIG_CAPACITY);
        for (int i = 0; i < Config.RIG_CAPACITY; i++) {
            crew.add(free.remove(world.rng().nextInt(free.size())));
        }
        return crew;
    }

    /**
     * The ZION instrument line (D-020, additive grammar), now with real
     * counts: fleet size, open pirate links across the fleet's rigs, and
     * sessions ended the hard way. {@code Locale.ROOT}, byte-stable across
     * locales; the caller prints, never this class. (When #119 lets a ship
     * be lost, the traced tally must move up to the city so a sunk rig's
     * count is not forgotten with it.)
     */
    public String zionLine(long tick) {
        int links = 0;
        int traced = 0;
        for (Hovercraft ship : fleet) {
            links += ship.rig().openLinks();
            traced += ship.rig().traced();
        }
        return String.format(Locale.ROOT,
                "ZION tick=%d census=%d fleet=%d links=%d traced=%d",
                tick, census.size(), fleet.size(), links, traced);
    }

    public List<Human> census() {
        return census;
    }
}
