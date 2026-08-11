package matrix.core;

import matrix.entities.MatrixEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Neighbor queries without O(n) shame (D-017), under SNAPSHOT semantics:
 * at rebuild every entity's perception coordinates (snapXCm/snapYCm) are
 * frozen, and BOTH sides of every query use them — the seeker's center
 * and the sought's location. Everyone perceives the world exactly as it
 * was when the tick began; a same-tick death may still be perceived
 * (the news has not reached you yet). Buckets are reused across ticks:
 * no per-tick allocation storm (D-027). Result order is cell-major
 * canonical — never read it as proximity.
 */
public final class SpatialHash {
    private final int cellCm;
    private final int cellsX;
    private final int cellsY;
    private final List<MatrixEntity>[] buckets;

    @SuppressWarnings("unchecked")
    public SpatialHash(int worldWidthCm, int worldHeightCm, int cellCm) {
        this.cellCm = cellCm;
        this.cellsX = worldWidthCm / cellCm + 1;
        this.cellsY = worldHeightCm / cellCm + 1;
        this.buckets = new List[cellsX * cellsY];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new ArrayList<>();
        }
    }

    public void rebuild(List<MatrixEntity> entities) {
        for (List<MatrixEntity> bucket : buckets) {
            bucket.clear();
        }
        for (MatrixEntity e : entities) {
            if (e.alive) {
                e.snapXCm = e.xCm();
                e.snapYCm = e.yCm();
                buckets[bucketIndex(e.snapXCm, e.snapYCm)].add(e);
            }
        }
    }

    private final List<MatrixEntity> scratch = new ArrayList<>();

    /**
     * Snapshot-exact hits around the seeker's OWN snapshot position.
     * Returns a REUSED buffer, valid only until the next query: consume
     * it inside your own tick, never store it (caller audit: the two
     * gaits do exactly that; AllocMeter said this was the hot list).
     */
    public List<MatrixEntity> near(MatrixEntity self, int radiusCm) {
        List<MatrixEntity> out = scratch;
        out.clear();
        int fx = self.snapXCm;
        int fy = self.snapYCm;
        int minX = Math.max(0, (fx - radiusCm) / cellCm);
        int maxX = Math.min(cellsX - 1, (fx + radiusCm) / cellCm);
        int minY = Math.max(0, (fy - radiusCm) / cellCm);
        int maxY = Math.min(cellsY - 1, (fy + radiusCm) / cellCm);
        long r2 = (long) radiusCm * radiusCm;
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                for (MatrixEntity e : buckets[cy * cellsX + cx]) {
                    long dx = (long) e.snapXCm - fx;
                    long dy = (long) e.snapYCm - fy;
                    if (dx * dx + dy * dy <= r2) {
                        out.add(e);
                    }
                }
            }
        }
        return out;
    }

    private int bucketIndex(int xCm, int yCm) {
        int cx = Math.min(cellsX - 1, Math.max(0, xCm / cellCm));
        int cy = Math.min(cellsY - 1, Math.max(0, yCm / cellCm));
        return cy * cellsX + cx;
    }

    // Cell-geometry views for the RegionMap (D-024): one clamp law rules
    // both maps — a coordinate lands in the same cell here and there.

    int cellCount() {
        return buckets.length;
    }

    int cellIndexOf(int xCm, int yCm) {
        return bucketIndex(xCm, yCm);
    }

    int cellCenterXCm(int cellIndex) {
        return (cellIndex % cellsX) * cellCm + cellCm / 2;
    }

    int cellCenterYCm(int cellIndex) {
        return (cellIndex / cellsX) * cellCm + cellCm / 2;
    }
}
