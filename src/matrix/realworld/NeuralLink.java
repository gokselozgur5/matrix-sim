package matrix.realworld;

import matrix.core.World;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.entities.TheOne;

/** The jack: the brain stays in the real world; only live I/O reaches the Matrix. An Avatar is a Proxy. */
public final class NeuralLink {

    private NeuralLink() {}

    public static Avatar jackIn(World w, Brain brain, Pill pill) {
        Avatar a = new Avatar(brain, pill, w.rng().nextInt(World.W), w.rng().nextInt(World.H));
        w.spawn(a);
        return a;
    }

    public static TheOne jackInTheOne(World w, Brain brain) {
        TheOne one = new TheOne(brain, World.W / 2, World.H / 2);
        w.spawn(one);
        return one;
    }
}
