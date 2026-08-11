package matrix.realworld;

/**
 * The person (D-011). Exists whether plugged or not — opt-out is liberation,
 * not deletion. Owns the Brain (composition: same fate); is hosted by a Pod
 * (aggregation: may leave); holds at most one NeuralLink while dreaming.
 * Since D-033: carries a personal disbelief threshold, drawn once at grow
 * time — same seed, same Thomas, same breaking point.
 */
public final class Human {
    public final String name;
    public final Brain brain;
    public final Pod pod;
    /**
     * The personal overflow bound (D-033): where THIS mind stops believing.
     * Drawn at the pod, never redrawn; the fated carry {@link Long#MAX_VALUE}
     * — The One never self-substantiates.
     */
    public final long threshold;
    NeuralLink link;

    public Human(String name, Brain brain, Pod pod, long threshold) {
        this.name = name;
        this.brain = brain;
        this.pod = pod;
        this.threshold = threshold;
    }

    public boolean alive() {
        return brain.alive();
    }

    public NeuralLink link() {
        return link;
    }
}
