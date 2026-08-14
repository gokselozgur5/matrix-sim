/**
 * The rendered ecosystem: birds, insects, strays — a healthy program is
 * invisible.
 *
 * <p>The design rule of this room is D-015: <strong>species are catalog data;
 * classes are for behavior only.</strong> There is exactly one entity class,
 * {@link matrix.entities.eco.EnvironmentProgram}; what KIND of thing it is
 * (kingdom, gait, speed, population cap) is a {@link matrix.entities.eco.Species}
 * row in the {@link matrix.entities.eco.Bestiary}. Adding a species is adding
 * a datum, not a type — resist the urge to subclass, and do not mint a row at
 * a call site either: a species the Bestiary has never heard of is outside
 * every budget derived from it (#974). One-offs the story mints once live in
 * {@code Bestiary.ONE_OFFS} and are counted by {@code Bestiary.EVERY}.
 *
 * <p>Movement composes from {@code matrix.entities.behavior} strategies
 * (D-016); flocking runs on fixed-point integer arithmetic like everything
 * else (D-004) and reads neighbors through the spatial hash (D-017), which
 * means it too perceives snapshots: the flock you steer by is the flock as
 * of tick start. Heading is digested state — two skies whose birds bank
 * differently are different universes.
 *
 * <p>Governing records: D-015, D-016, D-017, D-018 (per-species caps and
 * cadence).
 */
package matrix.entities.eco;
