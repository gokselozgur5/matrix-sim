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
        farMovers.clear();
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            e.seq = i; // the linear scan's tie-break, frozen while the list is (#135)
            e.grid = this;
            e.farMover = false;
            if (e.alive) {
                e.snapXCm = e.xCm();
                e.snapYCm = e.yCm();
                buckets[bucketIndex(e.snapXCm, e.snapYCm)].add(e);
                if (e.snapXCm < 0 || e.snapXCm > (cellsX - 1) * cellCm + cellCm
                        || e.snapYCm < 0 || e.snapYCm > (cellsY - 1) * cellCm + cellCm) {
                    // A snapshot outside the grid was clamped into an edge cell it
                    // does not lie in — the ring bound cannot reason about it, so
                    // it rides the ledger from birth. No mover does this today
                    // (moveBy clamps, drift and exiles stay in-world); the guard
                    // costs two compares and outlives that assumption.
                    noteFarMover(e);
                }
            }
        }
    }

    /**
     * The far-mover ledger (#135): every entity whose live position has left
     * the displacement law's reach of its snapshot, appended once, cleared at
     * rebuild. The ring search sweeps this list linearly after the rings —
     * snapshot cells index the sedate; the ledger indexes the teleports.
     */
    private final List<MatrixEntity> farMovers = new ArrayList<>();

    public void noteFarMover(MatrixEntity e) {
        if (e.farMover) {
            return;
        }
        e.farMover = true;
        farMovers.add(e);
    }

    /**
     * Snapshot-exact hits around the seeker's OWN snapshot position, appended
     * into the CALLER'S list after clearing it. Returns that same list so the
     * call reads as an expression.
     *
     * <p>This class deliberately owns no result buffer (#823). #175 kept one
     * here and lent it out with the reuse law written on this method — but the
     * lender was private and the borrowers all came through {@code
     * World.nearby()}, whose contract said nothing. The warning lived on the
     * method nobody could call. A hash that owns no buffer cannot lend one, so
     * two callers can no longer be handed the same list; whoever wants a result
     * brings somewhere to put it, and holds it for exactly as long as they like.
     *
     * <p>The allocation D-027 cares about is unchanged: a caller-owned list is
     * built once and refilled, which is what the shared one was doing.
     */
    public List<MatrixEntity> nearInto(MatrixEntity self, int radiusCm, List<MatrixEntity> out) {
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

    // Cell-geometry views for the RegionMap (D-024) and the ring hunts
    // (#135): one clamp law rules every map — a coordinate lands in the
    // same cell here, there, and in the hunt's anchor.

    int cellCount() {
        return buckets.length;
    }

    int cellsXCount() {
        return cellsX;
    }

    int cellsYCount() {
        return cellsY;
    }

    int cellSizeCm() {
        return cellCm;
    }

    /** Bucket at grid coordinates — the ring search's read window; never mutate. */
    List<MatrixEntity> bucketAt(int cx, int cy) {
        return buckets[cy * cellsX + cx];
    }

    /** The current tick's far movers — sweep after the rings; never mutate. */
    List<MatrixEntity> farMovers() {
        return farMovers;
    }

    /** How many entities this tick's rings must sweep linearly — the census World keeps (#825). */
    int farMoverCount() {
        return farMovers.size();
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
