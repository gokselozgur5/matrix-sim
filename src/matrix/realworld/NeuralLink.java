package matrix.realworld;

import matrix.entities.Avatar;

/**
 * One live connection: the jack. The death rule lives HERE, on the
 * connection, exactly as the lore has it (D-013): when the avatar dies
 * inside, the link flatlines the brain and flushes the pod outside —
 * when there is one; the free-born ride in podless (D-032). Three
 * endings: closeClean (the treaty's door, the brain lives), observeDeath
 * (the avatar died first), severUnclean (the wire itself dies first).
 * This is the only place the two worlds touch.
 */
public final class NeuralLink {
    public final Human human;
    public final Avatar avatar;
    public final LinkKind kind;
    private boolean closed = false;
    /**
     * The personal account of D-033 — spike-only disbelief, link-local per
     * the crown-smith ruling (open point a: it dies with the link). Written
     * by AcceptanceLoop in registration order, read by RealWorld for the
     * door, the FATE line and the digest. {@code windows} counts the spike
     * draws taken (live BLUE accrual windows), {@code spikes} the ones that
     * landed — the FATE line cites both; only the residue itself enters the
     * digest, exactly the addendum's framing.
     */
    long personalResidue = 0;
    int spikes = 0;
    int windows = 0;

    public NeuralLink(Human human, Avatar avatar, LinkKind kind) {
        this.human = human;
        this.avatar = avatar;
        this.kind = kind;
        human.link = this;
    }

    public boolean closed() {
        return closed;
    }

    /**
     * The treaty's door: a clean exit — the link closes, the brain LIVES,
     * the avatar leaves the world, and the vacated pod opens (the selfsub
     * FATE line always said "pod opens"; since #134 the substrate budget
     * actually notices). The free-born have no pod to open (D-032).
     */
    public void closeClean() {
        closed = true;
        if (human.pod != null) {
            human.pod.flush();
        }
        human.link = null;
    }

    /**
     * The third ending (D-032): the CONNECTION dies first. Brain flatlined,
     * link closed, nothing to flush — a pirate has no pod. The caller queues
     * the avatar's Remove; inside, a red simply stops moving. A link already
     * closed is a no-op: a mind that left clean cannot be killed by a wire
     * it no longer wears.
     */
    public void severUnclean() {
        if (closed) {
            return;
        }
        human.brain.flatline();
        closed = true;
        human.link = null;
    }

    /** Returns true exactly once — when it observes the avatar's death and executes the rule. */
    public boolean observeDeath() {
        if (closed || avatar.alive) {
            return false;
        }
        closed = true;
        human.brain.flatline();
        if (human.pod != null) {
            human.pod.flush(); // the free-born have no pod to flush (D-032)
        }
        human.link = null;
        return true;
    }
}
