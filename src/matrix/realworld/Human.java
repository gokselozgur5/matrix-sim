package matrix.realworld;

/**
 * The person (D-011). Exists whether plugged or not — opt-out is liberation,
 * not deletion. Owns the Brain (composition: same fate); may be hosted by a
 * Pod (aggregation: may leave, or never have entered); holds at most one
 * NeuralLink while dreaming.
 */
public final class Human {
    public final String name;
    public final Brain brain;
    /** Honestly 0..1 (D-032, per the crown): the farm's slot, or null for the free-born. */
    public final Pod pod;
    NeuralLink link;

    /** {@code pod} may be null — the free-born never had one; every reader guards. */
    public Human(String name, Brain brain, Pod pod) {
        this.name = name;
        this.brain = brain;
        this.pod = pod;
    }

    public boolean alive() {
        return brain.alive();
    }

    public NeuralLink link() {
        return link;
    }
}
