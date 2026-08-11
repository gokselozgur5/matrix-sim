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
 * P0 is measure-only. Nothing downstream reads this map, no entity is
 * parked, no digested state is added — the fingerprint of the world is
 * byte-identical with the map or without it. Zero rng draws ever; the
 * state walk runs in region-index order. Parking, aggregates, and the
 * digest segment arrive only if and as the D-024 verdict rules.
 */
public final class RegionMap {
    private final SpatialHash hash;
    private final int[] regionOfCell;
    private final int[] avatarCounts;
    private final long[] lastAttended;
    private final boolean[] hot;
    private int hotCount;

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
        this.hot = new boolean[zones.size()];
    }

    /**
     * Called by World.step() immediately after the hash rebuild: snapshots
     * are fresh, fresh positions are never read. Reused arrays, no
     * allocation (D-027); entity walk in list order (D-010), region walk
     * in region-index order.
     */
    public void refresh(long tick, List<MatrixEntity> entities) {
        Arrays.fill(avatarCounts, 0);
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Avatar) {
                avatarCounts[regionOfCell[hash.cellIndexOf(e.snapXCm, e.snapYCm)]]++;
            }
        }
        hotCount = 0;
        for (int r = 0; r < hot.length; r++) {
            if (avatarCounts[r] > 0) {
                lastAttended[r] = tick;
            }
            hot[r] = tick - lastAttended[r] <= Config.LOD_LINGER_TICKS;
            if (hot[r]) {
                hotCount++;
            }
        }
    }

    public int regionCount() {
        return hot.length;
    }

    /** Live Avatars whose snapshot lay in the region at the last refresh. */
    public int avatarCount(int region) {
        return avatarCounts[region];
    }

    /** HOT at the last refresh: attended now, or within the linger of the last attention. */
    public boolean isHot(int region) {
        return hot[region];
    }

    public int hotCount() {
        return hotCount;
    }
}
