package matrix;

import matrix.core.Director;
import matrix.core.World;
import matrix.machine.Source;

/** The machine side as one system node (D-031): World, then Director, then the Source's grace clock. */
public final class MachineSystem implements SystemNode {
    private final World world;
    private final Director director;
    private final Source source;

    public MachineSystem(World world, Director director, Source source) {
        this.world = world;
        this.director = director;
        this.source = source;
    }

    @Override
    public String name() {
        return "matrix";
    }

    @Override
    public void tick(long tick) {
        world.step();
        director.tick(world.tick());
        source.tick(world.tick());
    }
}
