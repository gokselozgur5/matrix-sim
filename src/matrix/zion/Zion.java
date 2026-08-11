package matrix.zion;

import matrix.realworld.Human;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The city: the free-human registry (D-011 cashed in — every liberation
 * lands HERE, somewhere real). The census grows only through
 * {@link #absorb}, and absorb order is liberation order: the root — the
 * only class allowed to hold both banks (D-012) — drains RealWorld's
 * pending liberations into this door each tick, in link registration
 * order. Refuses: deleting a Human (liberation, not deletion), touching
 * pods or links of the still-plugged, Matrix-side anything, and printing —
 * it returns its {@code ZION} line; only the root emits.
 */
public final class Zion {
    private final List<Human> census = new ArrayList<>();
    /** Index-aligned with the census: where each citizen came from ("treaty" today; #121 adds the Kid's own tag). */
    private final List<String> origins = new ArrayList<>();

    /** The door on this side: one freed Human enters the census — link already closed clean, nothing flushed. */
    public void absorb(Human human, String origin) {
        census.add(human);
        origins.add(origin);
    }

    /**
     * The canonical zion slot: last in the node order (#122), so
     * liberations queued this tick are absorbed this tick. At the floors
     * stage that absorption is the root's drain into {@link #absorb};
     * the fleet tick and sortie scheduling arrive behind the D-032
     * verdict. ZERO rng draws here — fate untouched.
     */
    public void tick(long tick) {
    }

    /**
     * The ZION instrument line (D-020, additive grammar): honest zeros
     * for fleet, links, and traced until those exist. {@code Locale.ROOT},
     * byte-stable across locales; the caller prints, never this class.
     */
    public String zionLine(long tick) {
        return String.format(Locale.ROOT,
                "ZION tick=%d census=%d fleet=0 links=0 traced=0", tick, census.size());
    }

    public List<Human> census() {
        return census;
    }
}
