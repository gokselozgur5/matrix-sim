/**
 * The composition root and nothing else.
 *
 * <p>{@link matrix.Simulation} is the ONLY place where both worlds are held at
 * once (D-012): it grows humans, jacks avatars in, wires the {@code NeuralLink}
 * bridge, and owns the clock. Everything else in this package is bootstrap
 * ({@link matrix.Main} — the one class allowed to touch the wall clock, and only
 * for the PERF/BENCH harness, never for domain logic) or system plumbing
 * (the {@code SystemNode} composite of D-031, ticked in canonical order:
 * machine side first, then the real world).
 *
 * <p>Invariants you must not break here:
 * <ul>
 *   <li>Boot order is canon: citizens, then the Oracle (she subscribes BEFORE
 *       the first publish — the bus seals, by law), then the named agent, the
 *       daemons, the exiles, the ecosystem.</li>
 *   <li>All output lines end with an explicit {@code \n} and flow through the
 *       three D-020 instruments; nothing prints from the domain directly.</li>
 *   <li>No new composition roots. If a class needs both worlds, it does not
 *       need both worlds — it needs a redesign.</li>
 * </ul>
 *
 * <p>Governing records: D-009 (plain javac), D-010 (determinism), D-012
 * (composition root), D-019 (backend only), D-020 (observability), D-027
 * (budgets), D-031 (system of systems).
 */
package matrix;
