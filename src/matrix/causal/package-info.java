/**
 * Immutable identity and record vocabulary for the Human causal seam.
 *
 * <p>This package is deliberately neutral. Matrix packages and real-world
 * packages may both name its values, while neither side gains a reference to
 * the other's mutable objects. {@link matrix.Simulation} remains the only
 * composition root allowed to translate those values into cross-world work.
 */
package matrix.causal;
