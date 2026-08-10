package matrix.core;

import matrix.entities.MatrixEntity;

/**
 * A pending world mutation (D-005), named for the future it will grow into
 * (D-023: one day the event log is the state). Queued during a tick,
 * flushed in order at tick end — never applied mid-iteration.
 */
public sealed interface WorldEvent permits WorldEvent.Spawn, WorldEvent.Remove, WorldEvent.Replace {

    record Spawn(MatrixEntity entity) implements WorldEvent {}

    record Remove(int entityId) implements WorldEvent {}

    /** In-place substitution at the same registry slot — iteration order survives infection (D-001/D-010). */
    record Replace(int entityId, MatrixEntity replacement) implements WorldEvent {}
}
