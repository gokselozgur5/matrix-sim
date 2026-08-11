package matrix.realworld;

import matrix.entities.Avatar;

/**
 * One live connection: the jack. The death rule lives HERE, on the
 * connection, exactly as the lore has it (D-013): when the avatar dies
 * inside, the link flatlines the brain and flushes the pod outside.
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

    /** The treaty's door: a clean exit — the link closes, the brain LIVES, the avatar leaves the world. */
    public void closeClean() {
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
        human.pod.flush();
        human.link = null;
        return true;
    }
}
