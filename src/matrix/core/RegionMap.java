package matrix.core;

import matrix.entities.Avatar;
import matrix.entities.MatrixEntity;

import java.util.Arrays;
import java.util.List;

/**
 * The attention ledger of space (D-024, P0). At boot every spatial-hash
 * cell is assigned to its nearest PlaceGraph zone center — data, not dice;
 * the partition is immutable after boot and region index IS zone index.
 * Each step the map re-reads attention from SNAPSHOT coordinates (D-017's
 * law: everyone senses the world as it WAS at tick start): a region is HOT
 * while any live Avatar's snapshot position lies in it, and stays HOT for
 * LOD_LINGER_TICKS after the last such sighting (hysteresis — attention
 * does not flicker). Avatars only: connected minds hold attention;
 * programs are the dream.
 *
 * P0 was measure-only: the map watched and nothing read it. P1 (#131)
 * opens the ledger for its first reader — EnvironmentProgram stretches
 * its Scheduler period by LOD_COLD_STRETCH while its snapshot lies in
 * a COLD region. Eco only: Avatars, agents and every other program
 * keep their cadence; connected minds are never degraded. A skipped
 * tick skips its rng draws too, so the DIGEST chain moves by design —
 * the reframe is law: determinism means the DEGRADED film replays
 * bit-identically, not that it matches the unstretched one. Zero rng
 * draws in the map itself; the state walk runs in region-index order.
 * Parking, aggregates, and the digest segment arrive only if and as
 * the D-024 verdict rules.
 *
 * P3 (#134, D-008): the substrate budget's HOT-slot cap arrives as a
 * scalar. Attention is measured exactly as before — the cap never edits
 * lastAttended, so linger hysteresis stays a pure attention memory —
 * and then capacity rules: when more regions are WATCHED
 * (attention-HOT) than the budget buys, only the top slotCap by
 * (avatarCount desc, region-index asc — the ATTN ranking) stay HOT;
 * the rest are forcibly demoted to the COLD path. Whatever COLD means
 * this phase (today the P1 stretch, parking when P2 lands), a demoted
 * region simply IS cold — composition, no special case. Each NEW
 * demotion fires the glitch counter once: demand exceeding substrate,
 * made visible (the D-008 dossier's instrument). The budget only says
 * how many; this map decides which, with zero draws.
 */
public final class RegionMap {
    private final SpatialHash hash;
    private final int[] regionOfCell;
    private final int[] avatarCounts;
    private final long[] lastAttended;
    private final boolean[] watched;
    private final boolean[] keep;
    private final boolean[] demotedPrev;
    private final boolean[] hot;
    private int hotCount;
    private long glitches;

    public RegionMap(SpatialHash hash, PlaceGraph places) {
        this.hash = hash;
        List<PlaceGraph.Zone> zones = places.zones();
        this.regionOfCell = new int[hash.cellCount()];
        for (int c = 0; c < regionOfCell.length; c++) {
            long cx = hash.cellCenterXCm(c);
            long cy = hash.cellCenterYCm(c);
            int best = 0;
            long bestD = Long.MAX_VALUE;
            for (int z = 0; z < zones.size(); z++) {
                Position center = zones.get(z).center();
                long dx = cx - center.xCm();
                long dy = cy - center.yCm();
                long d = dx * dx + dy * dy;
                if (d < bestD) { // ties keep the lower zone index — the map is not a dice roll
                    bestD = d;
                    best = z;
                }
            }
            regionOfCell[c] = best;
        }
        this.avatarCounts = new int[zones.size()];
        this.lastAttended = new long[zones.size()];
        Arrays.fill(lastAttended, Long.MIN_VALUE / 2); // never attended, and no overflow on subtract
        this.watched = new boolean[zones.size()];
        this.keep = new boolean[zones.size()];
        this.demotedPrev = new boolean[zones.size()];
        this.hot = new boolean[zones.size()];
    }

    /**
     * Called by World.step() immediately after the hash rebuild: snapshots
     * are fresh, fresh positions are never read. Reused arrays, no
     * allocation (D-027); entity walk in list order (D-010), region walk
     * in region-index order. Two passes since P3: attention first
     * (unchanged law), then capacity — {@code slotCap} is the substrate
     * budget's scalar, Integer.MAX_VALUE when nothing commands one, and
     * an uncapped refresh is the P1 refresh bit for bit.
     */
    public void refresh(long tick, List<MatrixEntity> entities, int slotCap) {
        Arrays.fill(avatarCounts, 0);
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Avatar) {
                avatarCounts[regionOfCell[hash.cellIndexOf(e.snapXCm, e.snapYCm)]]++;
            }
        }
        int watchedCount = 0;
        for (int r = 0; r < hot.length; r++) {
            if (avatarCounts[r] > 0) {
                lastAttended[r] = tick;
            }
            watched[r] = tick - lastAttended[r] <= Config.LOD_LINGER_TICKS;
            if (watched[r]) {
                watchedCount++;
            }
        }
        boolean capped = watchedCount > slotCap;
        if (capped) {
            // demand exceeds substrate: keep the top slotCap watched regions
            // by (avatarCount desc, region-index asc) — the ATTN ranking,
            // ties to the lower index; a lingering ghost region ranks last
            // and is the first the cap eats. Selection is pure arithmetic.
            Arrays.fill(keep, false);
            for (int k = 0; k < slotCap; k++) {
                int best = -1;
                for (int r = 0; r < hot.length; r++) {
                    if (watched[r] && !keep[r]
                            && (best == -1 || avatarCounts[r] > avatarCounts[best])) {
                        best = r;
                    }
                }
                keep[best] = true; // watchedCount > slotCap > k: a candidate always remains
            }
        }
        hotCount = 0;
        for (int r = 0; r < hot.length; r++) {
            hot[r] = watched[r] && (!capped || keep[r]);
            boolean demoted = watched[r] && !hot[r];
            if (demoted && !demotedPrev[r]) {
                glitches++; // a watched room went dim — the users will call it déjà vu
            }
            demotedPrev[r] = demoted;
            if (hot[r]) {
                hotCount++;
            }
        }
    }

    public int regionCount() {
        return hot.length;
    }

    /** Region owning the given snapshot coordinates — the cell's zone, pure index math, zero draws. */
    public int regionAt(int xCm, int yCm) {
        return regionOfCell[hash.cellIndexOf(xCm, yCm)];
    }

    /** Live Avatars whose snapshot lay in the region at the last refresh. */
    public int avatarCount(int region) {
        return avatarCounts[region];
    }

    /**
     * HOT at the last refresh: attended now or within the linger — AND
     * inside the substrate budget's slot cap (P3). What every fidelity
     * consumer reads; a capped-out region answers cold here while its
     * attention memory stays warm.
     */
    public boolean isHot(int region) {
        return hot[region];
    }

    public int hotCount() {
        return hotCount;
    }

    /**
     * Cumulative D-008 glitch count: how many times a WATCHED region was
     * forcibly demoted by the slot cap (counted once per demotion edge).
     * The SUBSTRATE line quotes it; the root carries the number across.
     */
    public long capGlitches() {
        return glitches;
    }
}
