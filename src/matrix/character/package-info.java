/**
 * The character kernel: sheets derive, contests resolve, and the world does
 * not know yet. This package is D-042 (one contest grammar, four family
 * vocabularies) as law rather than proposal — the gate closed by the
 * Architect's verdict on 2026-08-12 — and it comes aboard as a RESIDENT,
 * not as a dependency.
 *
 * <p>The layer's law:
 * <ul>
 *   <li><b>The domain does not import this package.</b> Not {@code World},
 *       not {@code Config}, not an entity, not a probe wired into the build.
 *       Adoption is residency; coupling arrives per unit, downstream, each
 *       one declaring its own digest move. Digest-neutrality here is
 *       therefore BY CONSTRUCTION, not by care: code nothing calls cannot
 *       move a byte of state, and the proof is a grep over the dependency
 *       graph rather than a measurement that could go stale.</li>
 *   <li><b>This package imports next to nothing back.</b> No
 *       {@code matrix.core}, no rng, no clock — {@link java.util.Locale#ROOT},
 *       {@link java.util.List} and a charset are the whole import surface.
 *       A sheet is a pure function of identity
 *       ({@link matrix.character.Sheets}); a contest is subtraction
 *       ({@link matrix.character.Contest}).</li>
 *   <li><b>The layer spends no fate.</b> Deriving every soul in the city
 *       costs the rng stream nothing: there is no draw, and there is no
 *       read of world state either — derivation inputs are birth-invariants
 *       only. DrawMeter polices the draws; the second half is policed by
 *       declaration, because a meter that counts consumption cannot see a
 *       read.</li>
 *   <li><b>Vocabulary discipline.</b> A human never grows
 *       {@code replication}; a system never dodges. The axis lists live on
 *       {@link matrix.character.Family} and {@code Sheet.stat} refuses
 *       foreign words — reviewers get a stack trace, not a style note.
 *       Axis ORDER is canonical: append on verdict, never reorder.</li>
 *   <li><b>Capitalization is identity.</b> Names are mixed as UTF-8 bytes,
 *       so {@code the Architect} and {@code The Architect} are two
 *       different souls. Every roster that feeds this package quotes the
 *       record's spellings exactly.</li>
 * </ul>
 *
 * <p>Cross-family contests are legal and are the whole point — evasion vs
 * pursuit, will vs authority, tolerance vs replication. Smith's license to
 * CARRY a stolen human sheet (D-014, cross-family status) is expressible
 * here precisely because a {@link matrix.character.Sheet} is a value: hand a
 * program a HUMAN sheet and nothing in the grammar objects — but wiring that
 * is a coupling unit's work, not the kernel's.
 */
package matrix.character;
