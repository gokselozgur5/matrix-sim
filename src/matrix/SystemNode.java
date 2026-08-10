package matrix;

/**
 * The contract every top-level system honors (D-031): the universe is a
 * composite of these. Fence: no methods beyond name/tick until a third
 * implementor exists.
 */
public interface SystemNode {
    String name();

    void tick(long tick);
}
