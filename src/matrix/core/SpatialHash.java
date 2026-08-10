package matrix.core;

import matrix.entities.MatrixEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Neighbor queries without O(n) shame (D-017). A fixed grid of buckets
 * over the fixed-point city; rebuilt once per tick in entity-list order,
 * queried cell-block by cell-block — both orders canonical, so the hash
 * can never introduce nondeterminism. Buckets are reused across ticks:
 * no per-tick allocation storm (D-027).
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
                buckets[bucketIndex(e.pos)].add(e);
            }
        }
    }

    /** Exact-radius hits, canonical order: cells scanned y-major then x, insertion order within. */
    public List<MatrixEntity> near(Position from, int radiusCm) {
        List<MatrixEntity> out = new ArrayList<>();
        int minX = Math.max(0, (from.xCm() - radiusCm) / cellCm);
        int maxX = Math.min(cellsX - 1, (from.xCm() + radiusCm) / cellCm);
        int minY = Math.max(0, (from.yCm() - radiusCm) / cellCm);
        int maxY = Math.min(cellsY - 1, (from.yCm() + radiusCm) / cellCm);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                for (MatrixEntity e : buckets[cy * cellsX + cx]) {
                    if (from.within(e.pos, radiusCm)) {
                        out.add(e);
                    }
                }
            }
        }
        return out;
    }

    private int bucketIndex(Position p) {
        int cx = Math.min(cellsX - 1, Math.max(0, p.xCm() / cellCm));
        int cy = Math.min(cellsY - 1, Math.max(0, p.yCm() / cellCm));
        return cy * cellsX + cx;
    }
}
