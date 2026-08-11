package matrix.core;

import matrix.entities.MatrixEntity;

/**
 * A pending world mutation (D-005), named for the future it will grow into
 * (D-023: one day the event log is the state). Queued during a tick,
 * flushed in order at tick end — never applied mid-iteration.
 */
public sealed interface WorldEvent permits WorldEvent.Spawn, WorldEvent.Remove, WorldEvent.Replace,
        WorldEvent.Park, WorldEvent.Unpark {

    record Spawn(MatrixEntity entity) implements WorldEvent {}

    record Remove(int entityId) implements WorldEvent {}

    /** In-place substitution at the same registry slot — iteration order survives infection (D-001/D-010). */
    record Replace(int entityId, MatrixEntity replacement) implements WorldEvent {}

    /**
     * D-024 P2 (#132): fold a long-COLD region's catalog residents into the
     * RegionMap aggregate and take them out of the walk. Carries the region
     * only — the aggregate folds AT the flush point, after this tick's
     * infections have settled, which is where the SmithCopy-safe membership
     * is knowable; the crown's aggregate lives in the RegionMap.
     */
    record Park(int regionId) implements WorldEvent {}

    /** D-024 P2 (#132): attention returned — re-materialize the region's parked residents. */
    record Unpark(int regionId) implements WorldEvent {}
}
