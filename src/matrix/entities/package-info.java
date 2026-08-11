/**
 * Everyone inside the simulation — citizens, daemons, programs, and the two
 * kinds of exception that drive the whole story.
 *
 * <p>The type lattice is the film's ontology: {@link matrix.entities.Avatar}
 * is a rendered mind (its body hangs elsewhere — see {@code matrix.realworld});
 * {@link matrix.entities.Program} is machine-born and carries the
 * {@code handleDeletion} template — the recorded assumption that processes
 * accept SIGTERM. The trilogy is the collapse of that assumption, and the
 * collapse has a name: {@link matrix.entities.DeletionRefusedException}.
 *
 * <p>Two protected irregularities live here on purpose:
 * <ul>
 *   <li>{@link matrix.entities.AgentSmith} is a deliberate, documented Liskov
 *       violation (D-014). Do not "fix" him; he is load-bearing.</li>
 *   <li>Infection is a Decorator (D-001): {@link matrix.entities.SmithCopy}
 *       keeps the victim's object untouched inside itself, so the finale's
 *       mass restore is type-guaranteed, not remembered. Delete the copy and
 *       the original snaps back. Hijack logging is sampled (the rng draw is
 *       unconditional — determinism survives the silence).</li>
 * </ul>
 *
 * <p>Digest visibility rule: a wrapped original is still state — the digest
 * recurses into copies. Two realities differing only inside a copy must not
 * hash equal.
 *
 * <p>Governing records: D-001, D-002 (catch mechanics), D-003 (refusal as a
 * thrown exception), D-014, D-021 (the perception feed), D-025 (grace and
 * orphans, from the collector's side).
 */
package matrix.entities;
