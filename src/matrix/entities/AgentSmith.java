package matrix.entities;

import matrix.core.World;

/** An ordinary agent until the fork. The difference is his answer to the GC. */
public final class AgentSmith extends Agent {

    public AgentSmith(int x, int y) {
        super("Smith", x, y);
    }

    @Override
    public void handleDeletion(World w) {
        throw new DeletionRefusedException("deletion refused — I'm getting out of this prison");
    }
}
