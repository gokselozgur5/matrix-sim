package matrix.realworld;

/**
 * The person (D-011). Exists whether plugged or not — opt-out is liberation,
 * not deletion. Owns the Brain (composition: same fate); may be hosted by a
 * Pod (aggregation: may leave, or never have entered); holds at most one
 * NeuralLink while dreaming. Since D-033 the breaking point is not stored:
 * disbelief is a pure function — of the BIRTH, since #373, not of the name
 * (see AcceptanceLoop), and the rng stream never hears about it.
 */
public final class Human {

    /**
     * What this mind answers when asked what it is (#658, leaf of #350).
     *
     * <p>The wing answers for itself. Until this method, the question *which
     * family is a Human, and which string is its identity* was decided inside
     * a probe — `SheetDump` picked `Family.HUMAN` and `human.name` on the
     * wing's behalf, out loud in a comment saying it would stop doing so when
     * #350 landed. This is the wing taking its own decision back.
     *
     * <p>DERIVED, NEVER STORED. There is no field behind this and there must
     * not be: the deleted `Human.threshold` is the litigated precedent, a
     * cached derived value being a second source of truth that drifts the
     * moment anything authors an override. `probes/SheetFence` reads that as
     * a grep and `SheetDump`'s `cached=` reads it as a heap walk, and both
     * stay at zero because this returns and forgets.
     *
     * <p>THE NAME AT BIRTH, and today that is the only name there is —
     * {@code name} is final and nothing renames a mind. #342's birth record
     * is what makes the invariant structural; until it lands this method is
     * correct by the field's immutability rather than by construction, which
     * is a weaker guarantee and worth saying rather than assuming.
     *
     * <p>Birth-invariants only: no pod, no link, no pill, no position, no
     * tick. Every one of those is a temptation and none of them is an input —
     * a mind's sheet is what it always was, not what happened to it.
     */
    public matrix.character.Sheet sheet() {
        return matrix.character.SheetDoor.at(name, matrix.character.Family.HUMAN);
    }

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
    /** Canonical causal identity of this person; names and avatars are not bindings. */
    public final matrix.causal.CausalRecord.Subject subject;
    /**
     * The birth event, mixed once and never again (#373, the #212 law): the
     * seed, the tick, the rack unit, the growth ordinal and the name — one
     * immutable long, computed here at construction so the derivation
     * downstream stays pure and the digest honest. A rename would not move
     * it; nothing can. The free-born carry no rack, so theirs reads the empty
     * string in that field and differs by construction from a podded birth.
     *
     * <p>The five inputs are exactly the five the birth record now carries
     * (#847 landed {@code rack} and {@code id} beside {@code tick} and
     * {@code name}; genesis carries the seed), so the key is re-derivable
     * from a recording by anyone who has the mixer. The key itself is not on
     * the record and this unit does not put it there.
     */
    public final long birthKey;
    /**
     * Persistent lived state: owned by this Human, never by an Avatar or link.
     * The value is immutable; #1695 will own its only lawful replacement path.
     */
    private MindState mindState;
    NeuralLink link;

    /** {@code pod} may be null — the free-born never had one; every reader guards. */
    public Human(String name, Brain brain, Pod pod, int id, long seed, long birthTick) {
        if (id < 0) {
            throw new IllegalArgumentException("human id must be nonnegative");
        }
        this.name = name;
        this.brain = brain;
        this.pod = pod;
        this.id = id;
        this.subject = new matrix.causal.CausalRecord.Subject("human-" + id);
        this.birthKey = AcceptanceLoop.birthKey(
                seed, birthTick, pod == null ? "" : pod.rackUnit, id, name);
        this.mindState = MindState.initial(subject);
    }

    public boolean alive() {
        return brain.alive();
    }

    public NeuralLink link() {
        return link;
    }

    /** Stable across avatar, link, disconnect, reinsertion and Matrix reload. */
    public MindState mindState() {
        return mindState;
    }
}
