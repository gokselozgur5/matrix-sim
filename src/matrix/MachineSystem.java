package matrix;

import matrix.core.Director;
import matrix.core.World;

/** The machine side as one system node (D-031): ticks the World, then the Director. */
public final class MachineSystem implements SystemNode {
    private final World world;
    private final Director director;

    public MachineSystem(World world, Director director) {
        this.world = world;
        this.director = director;
    }

    @Override
    public String name() {
        return "matrix";
    }

    @Override
    public void tick(long tick) {
        world.step();
        director.tick(world.tick());
    }
}
