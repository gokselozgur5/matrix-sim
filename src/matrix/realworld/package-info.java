/**
 * The biological side: pods, brains, and the only place two worlds touch.
 *
 * <p>{@link matrix.realworld.RealWorld} is the aggregate root of everything
 * fleshy (D-012) — the farm, the humans, the links. Nothing in
 * {@code matrix.core} may reference anything here; the dependency points one
 * way, and the {@link matrix.realworld.NeuralLink} is the single, audited
 * crossing (D-013). The mind-body rule executes ON the link, exactly as the
 * lore has it: when the avatar dies inside, the link — not the world, not the
 * simulation — flatlines the brain and flushes the pod. If you find yourself
 * wanting an {@code Avatar.brain} field, you are about to break the
 * architecture; the link observes, the link acts.
 *
 * <p>Two ways a link ends, and they mean different things: {@code closeClean}
 * is the treaty's open door (the brain LIVES, the census keeps them);
 * {@code observeDeath} is the hard way. The v3 ghost-HARDLINE finding is the
 * cautionary tale: any path that removes an avatar while leaving it
 * {@code alive=true} leaves an open jack accruing residue forever — mark the
 * death, let the bridge do the rest.
 *
 * <p>The dream is negotiated, not pushed (D-022):
 * {@link matrix.realworld.AcceptanceLoop} turns every live link's unaccepted
 * remainder into ledger residue — awake minds strain the simulation harder
 * than sleeping ones. {@link matrix.realworld.PerceptionFrame} is the JSONL
 * shape of one pilot's dream (D-021), the system's truest output.
 *
 * <p>Governing records: D-011 (Human as first-class), D-012, D-013, D-021,
 * D-022.
 */
package matrix.realworld;
