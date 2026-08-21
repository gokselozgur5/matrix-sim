package matrix.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This {@code Config} constant is NOT part of the universe's physics (#790).
 *
 * <p>{@link ChronosLog#configFingerprint()} identifies a universe by hashing
 * {@code Config}'s public static final fields, and #195 made that hash a hard
 * gate: {@code ReplayHarness} refuses to fold a recording whose physics differ
 * from the build's. The selection was by MODIFIER, which is an accident rather
 * than a contract — {@code public static final} says how a field is declared,
 * not whether it describes the world.
 *
 * <p>#209 landed the counterexample. {@code Config.HUNT_VERIFY} is
 * {@code public static final} and reads {@code Boolean.getBoolean}, so its
 * value comes from the invocation rather than from the build, and one build at
 * one seed produced two fingerprints depending on a debug flag. The hash
 * stopped identifying a universe and started identifying a command line.
 *
 * <p>Marking a field is a claim with a reason attached, and the reason is
 * required rather than optional: an opt-out that can be spent silently is the
 * hole this closes reopened one field later. The claim is narrow — <em>results
 * do not depend on this value</em> — and a field that fails it belongs in the
 * hash however inconvenient that is.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotPhysics {

    /** Why this constant cannot change a result. Stated, not implied. */
    String value();
}
