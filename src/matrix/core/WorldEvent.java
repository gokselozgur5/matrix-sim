package matrix.core;

import matrix.entities.MatrixEntity;

/**
 * A pending world mutation (D-005), named for the future it will grow into
 * (D-023: one day the event log is the state). Queued during a tick,
 * flushed in order at tick end — never applied mid-iteration.
 */
public sealed interface WorldEvent permits WorldEvent.Spawn, WorldEvent.Remove {

    record Spawn(MatrixEntity entity) implements WorldEvent {}

    record Remove(int entityId) implements WorldEvent {}
}
