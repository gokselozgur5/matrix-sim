/**
 * The stat kernel: sheets derive, contests resolve, and the world does not
 * know yet. This package is the D-042 proposal (one contest grammar, four
 * family vocabularies) as compiling code — built so the gate thread (#212)
 * can argue over derived sheets and sample contests instead of prose.
 *
 * <p>The layer's law, in force until the Architect's verdict:
 * <ul>
 *   <li><b>The domain does not import this package.</b> Not {@code World},
 *       not {@code Config}, not an entity, not a probe wired into the build.
 *       Digest-neutrality is therefore BY CONSTRUCTION, not by care: code
 *       nothing calls cannot move a byte of state. The declared digest
 *       segment D-042 promises arrives only with the verdict, in its own
 *       PR, as its own declared move.</li>
 *   <li><b>This package imports next to nothing back.</b> No
 *       {@code matrix.core}, no rng, no clock — {@link java.util.Locale#ROOT}
 *       and {@code Arrays} are the whole import surface. A sheet is a pure
 *       function of identity ({@link matrix.character.Sheets}); a contest is
 *       subtraction ({@link matrix.character.Contest}).</li>
 *   <li><b>Vocabulary discipline.</b> A human never grows
 *       {@code replication}; a system never dodges. The axis lists live on
 *       {@link matrix.character.Family} and {@code Sheet.stat} refuses
 *       foreign words — reviewers get a stack trace, not a style note.</li>
 * </ul>
 *
 * <p>Cross-family contests are legal and are the whole point — evasion vs
 * privilege, will vs authority, tolerance vs replication. Smith's license to
 * CARRY a stolen human sheet (D-014, cross-family status) is expressible
 * here precisely because a {@code Sheet} is a value: hand a program a HUMAN
 * sheet and nothing in the grammar objects — but wiring that is verdict-side
 * work, not draft-side.
 *
 * <p>Governing records: D-042 (proposed — gate #212, this package is its
 * exhibit), D-033 (the first stat's birthplace; the name-hash grammar
 * {@code matrix.realworld.AcceptanceLoop} proved), D-041 (the season's
 * charter), D-014 (the license the grammar must not preclude).
 */
package matrix.character;
