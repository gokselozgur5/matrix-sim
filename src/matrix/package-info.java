/**
 * The composition root and nothing else.
 *
 * <p>{@link matrix.Simulation} is the ONLY place where both worlds are held at
 * once (D-012): it grows humans, jacks avatars in, wires the {@code NeuralLink}
 * bridge, and owns the clock. Everything else in this package is bootstrap
 * ({@link matrix.Main} — the one class allowed to touch the wall clock, and only
 * for the PERF/BENCH harness, never for domain logic) or system plumbing
 * (the {@code SystemNode} composite of D-031, ticked in canonical order:
 * machine, realworld, zion).
 *
 * <p><b>What the canonical order does and does not buy (#830).</b> This
 * door used to weld two claims into one sentence — "the free city LAST, so
 * liberations queued this tick are absorbed this tick; the order fixes
 * draw order". The first was never true and the second was not true yet.
 * They are separate facts and only one of them is structural:
 *
 * <p><i>Same-tick absorption is not bought by zion's slot.</i> The drain
 * sits in {@link matrix.Simulation#tickOnce()} AFTER the whole node loop
 * and after the treaty block, and that line's position is the entire
 * reason a liberation queued in tick T is in the census before T ends.
 * Zion's slot ticks before {@code optOut} fires, so a slot-side drain
 * would land every treaty liberation a tick late — moving the drain into
 * {@code Zion.tick} is the refactor this sentence used to invite. All six
 * orders of the three nodes absorb in the same tick, measured; the
 * guarantee travels with that one line, never with the ordering. It now
 * has a keeper: {@code probes/SameTick} fails the moment a liberation
 * survives the tick that queued it, which is the only way this paragraph
 * can be falsified without editing it.
 *
 * <p><i>The order fixes draw order for four of the five moves, and not for
 * the fifth.</i> Zion draws inside its slot (crew draws per sortie, the
 * zone cursor's seeded start), so a node's position fixes where its draws
 * land in the rng stream — but only relative to MACHINE. Realworld and
 * zion commute. All six orders at seed 42, 6,000 ticks:
 *
 * <pre>
 *   machine, realworld, zion   the pinned seal (canonical)
 *   machine, zion, realworld   byte-identical to it, whole log, also at seeds 7 and 1
 *   realworld, machine, zion   moved
 *   zion, machine, realworld   moved
 *   realworld, zion, machine   moved, and equal to the row below
 *   zion, realworld, machine   moved
 * </pre>
 *
 * <p>So the free city can be taken off LAST silently — the exact move the
 * old sentence called a declared break, and the one a maintainer reading
 * "zion LAST" is most likely to make. The four that do move are declared
 * breaks. Measure the move you are making, not the class of moves: this
 * table has a keeper now (#1013), {@code probes/OrderTable}, which runs
 * every permutation of the node list it FINDS — a fourth {@code SystemNode}
 * is measured, not skipped — and prints each order's seal beside the first
 * digest link that differs from canonical, so those figures live in a run
 * instead of in this paragraph. The shape is three counters and the bench
 * judges them exactly: {@code orders=6 classes=4 silent=1} at seed 42 over
 * 6,000 ticks. A node that starts drawing splits a class, one that stops
 * merges two, and the day {@code silent} is not 1 this door is wrong on the
 * number that moved. The seal and the divergence tick this paragraph used to
 * quote for last-to-first were right when they were written and were re-rolled
 * by the next declared digest move — which is not a scandal, it is what a
 * declared move does to every row of this table at once, and it is exactly the
 * thing no run re-measured. The counters are also a reading at ONE seed: in
 * the QUIET universe at seed 1 the free city never launches, so it draws
 * nothing, its slot costs nothing and TWO of the six are silent. At #187's
 * merge {@code Zion.tick} was an empty method and every one of the six was
 * identical, which is how the old sentence's second half came to be written
 * as structural when it is contingent on some node drawing — the probe
 * calls that world
 * {@code ORDER_TABLE_VACUOUS} rather than passing it.
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
