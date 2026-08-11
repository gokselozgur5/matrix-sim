/**
 * The Matrix's kernel: state, law, and the instruments that make it auditable.
 *
 * <p>This package holds the determinism canon (D-010) in executable form.
 * One seeded {@link matrix.core.Rng} feeds every draw and counts them (the
 * count is digested — an unused draw is still a state change). No wall clock
 * exists here. Iteration is id/spawn order, never hash order. The world
 * mutates only through pending {@link matrix.core.WorldEvent} queues flushed
 * at tick end (D-005) — Spawn appends, Remove deletes, Replace swaps
 * <em>in place</em>, preserving the victim's iteration slot.
 *
 * <p>Observation is contract, not courtesy (D-020): the event log (owned
 * UTF-8, explicit {@code \n}, {@code Locale.ROOT} everywhere), METRIC lines,
 * and the tagged, prefix-free SHA-256 DIGEST chain
 * ({@link matrix.core.DigestCalculator}). The digest is the referee: any
 * optimization that changes it did not optimize — it changed the universe
 * (D-027). Perception is snapshot-based (D-017): both sides of a neighbor
 * query use tick-start coordinates, so a same-tick death may still be
 * perceived — the news has not reached you yet.
 *
 * <p>The {@link matrix.core.EventBus} seals on first publish and forbids
 * publishing while receiving. {@link matrix.core.AnomalyLedger} keeps the
 * city's unpaid resistance (D-022): residue in, balance up, and when the
 * bound is crossed the system owes the world an anomaly.
 *
 * <p>Governing records: D-004 (fixed-point cm space), D-005, D-010, D-017,
 * D-018 (tick budgets), D-020, D-022, D-027.
 */
package matrix.core;
