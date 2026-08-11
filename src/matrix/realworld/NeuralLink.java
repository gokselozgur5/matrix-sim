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
