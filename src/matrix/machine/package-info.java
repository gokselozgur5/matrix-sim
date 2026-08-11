/**
 * The machine hierarchy above the city: the Source, the Architect, and the
 * city of the machines themselves.
 *
 * <p>{@link matrix.machine.Source} is supervisor-lite (D-025): deletion is
 * offered before it is imposed — a grace window, then collection; programs
 * that refuse (D-003) go rogue, and every collection is reconciled against
 * the {@link matrix.machine.OrphanRegistry} census so a program eaten during
 * its grace period is voided, not double-counted. Deletion-by-consent must
 * exist in the log, not just in the contract.
 *
 * <p>{@link matrix.machine.Architect} is the old answer — purge, restore,
 * forget, reload — and he is an {@code enum} singleton on purpose: there is
 * exactly one father of the equation, and the type system says so.
 * {@link matrix.machine.MachineCity} executes treaties: the finale's mass
 * restore is a single flush of identity swaps (the Decorator guarantee of
 * D-001 cashed in), followed by an open door that is honored, not merely
 * announced.
 *
 * <p>House rule: this package commands the world through queued
 * {@code WorldEvent}s and the log — it never mutates entity lists directly,
 * and it never reaches into {@code matrix.realworld} (the bridge belongs to
 * the links).
 *
 * <p>Governing records: D-003, D-025, D-001 (restore guarantee), D-022
 * (the ledger it must answer to).
 */
package matrix.machine;
