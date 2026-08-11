package matrix.machine;

/**
 * Boot-time policy selector (D-008, crown #32): which substrate story this
 * universe runs. BATTERY is the story the sleepers were told — flavor
 * only, the zero-cost path. PROCESSOR is the theory the farm bills for:
 * plugged pods ARE the render substrate, and SubstrateBudget (#124) turns
 * their count into capacity.
 *
 * <p>STATELESS by law (the crown amendment): enum constants are JVM-wide
 * singletons, and determinism tests run several Simulations per JVM — any
 * mutable "how much" here would leak between universes (D-010). This enum
 * answers only "which rules"; the numbers live on SubstrateBudget, owned
 * per-universe by MachineSystem.
 */
public enum ComputeModel {
    BATTERY("the sleepers are batteries; the render farm is elsewhere"),
    PROCESSOR("the inmates render their own cells");

    private final String desc;

    ComputeModel(String desc) {
        this.desc = desc;
    }

    public String desc() {
        return desc;
    }

    /** Budget coupling is PROCESSOR's law alone — under BATTERY rendering stays free (D-008). */
    public boolean coupled() {
        return this == PROCESSOR;
    }
}
