package matrix.realworld;

/**
 * The person (D-011). Exists whether plugged or not — opt-out is liberation,
 * not deletion. Owns the Brain (composition: same fate); is hosted by a Pod
 * (aggregation: may leave); holds at most one NeuralLink while dreaming.
 */
public final class Human {
    public final String name;
    public final Brain brain;
    public final Pod pod;
    NeuralLink link;

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
