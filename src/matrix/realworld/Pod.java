package matrix.realworld;

/**
 * Life-support rack slot. Hosts a Human; does not own their fate
 * (aggregation, D-011).
 *
 * <p>It also owns every sentence the tree speaks about a rack slot (#849).
 * The three clauses below were written on {@code RealWorld} by #813 for one
 * reason and one only: {@code matrix.zion} may import {@code matrix.realworld}
 * and never the reverse, so the aggregate root was the one file in that
 * crew's territory both books could call. Territory is not a shelf. The
 * subject of the sentence is a rack unit, so the sentence lives on the rack
 * unit, and the broadcast rig no longer imports the biological aggregate root
 * in order to say one thing about a slot.
 *
 * <p>Each clause takes the MIND and not the slot, and that is the entire
 * guard. A signature of {@code (Pod)} would make every caller write
 * {@code h.pod} to ask the question — the exact reach D-033's line took at
 * {@code RealWorld:88} and paid for — while {@code (Human)} leaves no way to
 * ask that skips the check. The parameter is why the invariant has a home
 * rather than a habit: it is not that callers are trusted to guard, it is
 * that they cannot get at the field to fail to.
 *
 * <p>{@code Human} holds a {@code Pod} and {@code Pod} names a {@code Human}
 * in three static parameters. That is a reference in a signature, not state,
 * and it is what the first line of this class already claimed — a pod hosts
 * a person. The two are peers in one package; nothing here can reach a mind
 * it was not handed.
 */
public final class Pod {
    public final String rackUnit;
    private boolean occupied = true;

    public Pod(String rackUnit) {
        this.rackUnit = rackUnit;
    }

    public boolean occupied() {
        return occupied;
    }

    public void flush() {
        occupied = false;
    }

    /**
     * What happened to a body's rack slot when the mind died — one sentence,
     * one guard, one home (#813).
     *
     * <p>#189 made {@code Human.pod} honestly 0..1 and installed the guards
     * to match, and its body promised "a tree-wide {@code .pod} null audit".
     * That audit was true on the day it was run: at {@code fc5a557}
     * {@code RealWorld} held two {@code .pod} reads and both were guarded.
     * D-033's self-substantiation line landed after it and dereferenced the
     * field bare. A sweep is a promise about a moment; an invariant needs a
     * place to live, and since #849 it also needs a reader —
     * {@code probes/PodOptional} drives a podless mind through all four
     * endings, so the next bare deref fails a lock instead of waiting for
     * #121's free-born Kid to find it in production.
     */
    public static String flushClause(Human h) {
        return h.pod != null
                ? " (pod " + h.pod.rackUnit + " flushed)"
                : " (no pod to flush — they died free)";
    }

    /**
     * The same sentence for the other ending (#813): the mind walked out and
     * the slot opened behind it, rather than being flushed under it. Since
     * #134 the substrate budget actually notices the opening — and the mind
     * D-033 was written for is exactly the one that may have no slot at all,
     * so this branch is the one #121's Kid walks through.
     */
    public static String opensClause(Human h) {
        return h.pod != null
                ? "(pod " + h.pod.rackUnit + " opens)"
                : "(no pod to open — the free-born were never racked)";
    }

    /**
     * The third sentence (#811): the ending that flushes NOTHING.
     * {@code severUnclean} — the rig's timeout cut, and its death with the
     * ship — kills the mind and closes the wire and never touches the rack.
     * The old line said "nothing to flush", which was accidentally true of
     * the ACT and false about the body: it was printed as a claim of
     * podlessness over citizens who all hold rack units, standing empty
     * since the day they walked out of them.
     */
    public static String untouchedClause(Human h) {
        return h.pod != null
                ? " (pod " + h.pod.rackUnit + " untouched — the cut takes the mind, not the rack)"
                : " (no rack unit behind them — they died free)";
    }
}
