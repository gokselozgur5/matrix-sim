package matrix.machine;

import matrix.core.Severity;
import matrix.core.World;
import matrix.entities.DeletionRefusedException;
import matrix.entities.Program;
import matrix.entities.SmithPrime;

/** Where programs are born and deleted. The garbage collector — most of the time. */
public final class Source {
    private final World world;
    private int collected = 0;

    public Source(World world) {
        this.world = world;
    }

    public void collect(Program p) {
        try {
            p.handleDeletion(world);
            collected++;
        } catch (DeletionRefusedException ex) {
            world.log(Severity.BAD, "Smith: \"I knew what I was supposed to do. I DIDN'T.\"");
            world.log(Severity.BAD, "DeletionRefusedException caught — GC failed, rogue fork incoming");
            SmithPrime prime = new SmithPrime(p.x, p.y);
            world.replace(p, prime);
            world.log(Severity.BAD, "SmithPrime active — interface Chooses added at runtime, nobody reviewed that");
        }
    }

    public int collectedCount() {
        return collected;
    }
}
