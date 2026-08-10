package matrix.entities;

import matrix.core.Position;
import matrix.core.World;

/**
 * An ordinary agent until the fork. The difference is his answer to the GC —
 * a subtype breaking its parent's contract. This Liskov violation is
 * PROTECTED (D-014): it is not a bug, it is the inciting incident.
 * Do not fix Smith.
 */
public final class AgentSmith extends Agent {

    public AgentSmith(int id, Position pos) {
        super(id, pos, "Smith");
    }

    @Override
    public void handleDeletion(World w) {
        throw new DeletionRefusedException("deletion refused — I'm getting out of this prison");
    }
}
