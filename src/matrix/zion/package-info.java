/**
 * The free city: where liberation stops being a log line.
 *
 * <p>{@link matrix.zion.Zion} is the census of freed Humans — every exit
 * D-011 promised (treaty opt-outs first, Kid self-substantiations later)
 * lands here with an origin tag, absorbed in liberation order. Wings are
 * packages (the zoning rule), and a peer system under its own SystemNode
 * earns its own room with its own door; {@code matrix.realworld}'s door
 * stays honest that way — the farm, the people, the links are biology,
 * and the city of the free is not. Flagged, not final: this address rides
 * the D-032 gate's Q2 (aggregate-root ownership) — if the verdict moves
 * the room, the door moves with it.
 *
 * <p>Zoning: this room sits on the realworld side of the NeuralLink fire
 * door. {@code zion} may import {@code realworld}; {@code entities} never
 * imports {@code zion}; and {@code realworld} never imports {@code zion}
 * either — the handoff of freed Humans rides the composition root, the
 * one class allowed to hold both banks (D-012), so no package cycle ever
 * forms.
 *
 * <p>Invariants you must not break here:
 * <ul>
 *   <li>The census grows ONLY through {@code absorb} — link closed, no
 *       flush, nobody deleted (D-011: liberation, not deletion).</li>
 *   <li>Absorb order is liberation order: the root drains pending
 *       liberations in link registration order, every tick.</li>
 *   <li>At this stage (floors) the wing draws NOTHING from the rng — the
 *       fleet and its seeded crew draws arrive behind the D-032 verdict,
 *       through {@code world.rng()} in the zion tick slot, never before.</li>
 *   <li>Nothing here prints. Zion RETURNS its {@code ZION} line
 *       ({@code Locale.ROOT}, byte-stable); only the root emits.</li>
 * </ul>
 *
 * <p>Governing records: D-031 (third system node), D-032 (the gate this
 * wing awaits), D-011 (Human as first-class, never deleted).
 */
package matrix.zion;
