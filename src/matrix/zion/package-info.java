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
 *   <li>Every draw the wing makes — the crew draw at a launch, the
 *       insertion zone at a session — goes through {@code world.rng()}
 *       inside the zion tick slot, and only when the fleet actually acts:
 *       an idle wing consumes no fate.</li>
 *   <li>Pirate links are Zion's book: the rig registers, observes, and
 *       ends them in registration order — {@code RealWorld}'s walk never
 *       sees them, and the acceptance loop never accrues them (a pirate
 *       rides in lucid; flagged for the gate).</li>
 *   <li>Nothing here prints. Zion RETURNS its {@code ZION} line
 *       ({@code Locale.ROOT}, byte-stable); only the root emits.</li>
 * </ul>
 *
 * <p>Governing records: D-031 (third system node), D-032 (the gate this
 * wing awaits), D-011 (Human as first-class, never deleted).
 */
package matrix.zion;
