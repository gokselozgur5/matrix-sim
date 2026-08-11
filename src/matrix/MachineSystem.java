package matrix;

import matrix.core.Director;
import matrix.core.World;
import matrix.machine.Source;
import matrix.machine.SubstrateBudget;

import java.util.function.IntSupplier;

/**
 * The machine side as one system node (D-031): World, then Director, then
 * the Source's grace clock. Since #134 it also owns the SubstrateBudget
 * (crown #124, composition): under PROCESSOR it recounts plugged pods at
 * the top of every tick, BEFORE the world steps — tick-start state, one
 * number, every entity sees the same capacity. Under BATTERY no budget
 * exists and this node is the pre-D-008 one, bit for bit.
 */
public final class MachineSystem implements SystemNode {
    private final World world;
    private final Director director;
    private final Source source;
    private final SubstrateBudget budget;
    private final IntSupplier pluggedPods;

    /**
     * {@code budget} is null under BATTERY (D-008): no coupling, zero cost.
     * {@code pluggedPods} is the named port to PodFarm.occupiedCount, wired
     * by the root at boot (#88) — the only realworld read this side makes,
     * and it is a scalar.
     */
    public MachineSystem(World world, Director director, Source source,
            SubstrateBudget budget, IntSupplier pluggedPods) {
        this.world = world;
        this.director = director;
        this.source = source;
        this.budget = budget;
        this.pluggedPods = pluggedPods;
    }

    @Override
    public String name() {
        return "matrix";
    }

    @Override
    public void tick(long tick) {
        if (budget != null) {
            // Once per tick, before the world moves (#102 amended): the
            // recount happens even on frozen ticks, so the budget always
            // states tick-start truth and never changes mid-tick. Then the
            // command crosses — two scalars, the D-031 chain, nothing else.
            budget.recount(pluggedPods.getAsInt());
            world.setSubstrate(budget.hotSlots(), budget.cadenceStretch());
        }
        if (world.state() == matrix.core.SystemState.NEGOTIATION) {
            world.advanceFrozen();
            director.negotiationTick();
            return;
        }
        world.step();
        director.tick(world.tick());
        source.tick(world.tick());
    }
}
