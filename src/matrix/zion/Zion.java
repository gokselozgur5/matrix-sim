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
    /** The fleet: ships built and lost here (composition — crown #84). LOST hulls stay listed: nobody deleted, not even ships. */
    private final List<Hovercraft> fleet = new ArrayList<>();
    /** A filed sink order (#119): executes at the top of the next zion tick, then clears. */
    private boolean sinkOrdered = false;
    /** Hard endings that went down with a hull — the city keeps the count a sunk rig can no longer report. */
    private int tracedFallen = 0;
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
        // #119: a loss lands before the fleet flies its tick — the filed sink
        // order (ops console / --sink-at) or the SHIP_LOSS_TICK knob, one
        // trigger, one slot, deterministic either way.
        if (sinkOrdered || tick == Config.SHIP_LOSS_TICK) {
            sinkOrdered = false;
            sinkActiveShip();
        }
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
     * The sink order (#119): operator-driven loss, filed by the root —
     * exactly like reload, except the execution waits for the canonical
     * zion slot so the cascade always lands deterministically. Natural
     * loss causes (sentinels, squiddies, sabotage) arrive with later
     * units; today the operator IS fate.
     */
    public void orderSink() {
        sinkOrdered = true;
    }

    /**
     * The active ship goes down: first hull in fleet order still afloat.
     * The rig severs every open wire through the bridge (one BAD line per
     * wire), the ship closes with its FATE line, and the city absorbs the
     * fallen rig's hard-ending tally — a count must not sink with a hull.
     */
    private void sinkActiveShip() {
        for (Hovercraft ship : fleet) {
            if (ship.state() != Hovercraft.MissionState.LOST) {
                ship.destroy(world);
                tracedFallen += ship.rig().traced();
                return;
            }
        }
        world.log(Severity.SYS, "sink order refused: no hull afloat");
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
     * locales; the caller prints, never this class. The #197 note is
     * cashed (#119): a lost hull's tally moved up to the city the moment
     * it went down, so the sum walks only ships still afloat and no count
     * is forgotten. {@code fleet=} keeps counting every hull ever built —
     * the fallen stay on the registry, ships included (D-011's spirit).
     */
    public String zionLine(long tick) {
        int links = 0;
        int traced = tracedFallen;
        for (Hovercraft ship : fleet) {
            if (ship.state() == Hovercraft.MissionState.LOST) {
                continue;
            }
            links += ship.rig().openLinks();
            traced += ship.rig().traced();
        }
        return String.format(Locale.ROOT,
                "ZION tick=%d census=%d fleet=%d links=%d traced=%d",
                tick, census.size(), fleet.size(), links, traced);
    }

    /**
     * Every open pirate avatar across the fleet — ship order, then the
     * board's registration order. The root carries this to the collector
     * for the #118 trace metric (D-012: only the root holds both banks);
     * nothing here computes, and an empty list is the quiet answer.
     */
    public List<matrix.entities.Avatar> openPirateAvatars() {
        List<matrix.entities.Avatar> out = new ArrayList<>();
        for (Hovercraft ship : fleet) {
            ship.rig().openAvatarsInto(out);
        }
        return out;
    }

    public List<Human> census() {
        return census;
    }
}
