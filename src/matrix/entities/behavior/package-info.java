/**
 * Gaits, not minds: movement as composable strategy (D-016).
 *
 * <p>Each {@link matrix.entities.behavior.Movement} implementation is one way
 * of crossing the city — wander, flock, swarm, commute, and kin. Entities
 * COMPOSE a gait instead of inheriting one, so a species can change how it
 * moves without changing what it is (the D-015 boundary, seen from the other
 * side).
 *
 * <p>House rules:
 * <ul>
 *   <li>All arithmetic is fixed-point centimeters (D-004). No doubles in
 *       positions; accumulate error nowhere.</li>
 *   <li>Every random step draws from the world's one {@code Rng} — a gait
 *       that draws conditionally must draw the SAME number of times on every
 *       code path, or two identical universes diverge (D-010).</li>
 *   <li>Neighbor queries go through the spatial hash and see tick-start
 *       snapshots (D-017); a gait never reads live positions.</li>
 *   <li>A gait moves its OWN entity. World mutation (spawn/remove/replace)
 *       is not a movement concern — queue a {@code WorldEvent} elsewhere.</li>
 * </ul>
 *
 * <p>Governing records: D-004, D-010, D-015, D-016, D-017.
 */
package matrix.entities.behavior;
