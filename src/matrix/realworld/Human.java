package matrix.realworld;

/**
 * The person (D-011). Exists whether plugged or not — opt-out is liberation,
 * not deletion. Owns the Brain (composition: same fate); may be hosted by a
 * Pod (aggregation: may leave, or never have entered); holds at most one
 * NeuralLink while dreaming. Since D-033 the breaking point is not stored:
 * disbelief is a pure function of the name (see AcceptanceLoop) — fate was
 * always in the name, and the rng stream never hears about it.
 */
public final class Human {
    public final String name;
    public final Brain brain;
    /** Honestly 0..1 (D-032, per the crown): the farm's slot, or null for the free-born. */
    public final Pod pod;
    /**
     * The growth ordinal: which mind this universe grew, counting from zero
     * at the first (#847). Assigned once, at the birth, and never moved.
     *
     * <p>It lives on the mind rather than on the {@link Pod} because it is a
     * fact about the birth and not about the slot: #121's free-born will be
     * grown with no rack unit at all and still needs an ordinal, since the
     * die keys to the birth event and a mind with nothing but a name is a
     * mind whose fate a rename could move.
     *
     * <p>This is NOT {@code Avatar.id} — that number is the world's handle on
     * a body in the dream, allocated by {@code World.allocateId} and reused
     * by nobody here. The two never appear in the same sentence and never
     * name the same thing.
     */
    public final int id;
    NeuralLink link;

    /** {@code pod} may be null — the free-born never had one; every reader guards. */
    public Human(String name, Brain brain, Pod pod, int id) {
        this.name = name;
        this.brain = brain;
        this.pod = pod;
        this.id = id;
    }

    public boolean alive() {
        return brain.alive();
    }

    public NeuralLink link() {
        return link;
    }
}
