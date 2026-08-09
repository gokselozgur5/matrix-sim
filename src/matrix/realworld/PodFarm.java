package matrix.realworld;

import matrix.core.Severity;
import matrix.core.World;

import java.util.ArrayList;
import java.util.List;

public final class PodFarm {
    private final List<Pod> pods = new ArrayList<>();

    public Brain grow(String owner) {
        Brain b = new Brain(owner);
        int i = pods.size();
        String rackUnit = String.format("R%02d/U%02d", 1 + i / 24, 1 + i % 24);
        pods.add(new Pod(rackUnit, b));
        return b;
    }

    public void flush(World w, Brain b) {
        for (Pod p : pods) {
            if (p.brain == b && p.occupied()) {
                p.flush();
                w.log(Severity.BAD, "pod " + p.rackUnit + " flushed — faulty node down the drain (" + b.owner + ")");
                return;
            }
        }
    }

    public void release(Brain b) {
        for (Pod p : pods) {
            if (p.brain == b && p.occupied()) {
                p.flush();
                return;
            }
        }
    }

    public int occupiedCount() {
        int n = 0;
        for (Pod p : pods) if (p.occupied()) n++;
        return n;
    }
}
