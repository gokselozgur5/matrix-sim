package matrix.machine;

import java.util.ArrayList;
import java.util.List;

/** The ledger of everything that refused to die (D-025). Mythology becomes queryable data. */
public final class OrphanRegistry {
    public record Orphan(String name, long refusedAtTick) {}

    private final List<Orphan> orphans = new ArrayList<>();

    public void register(String name, long tick) {
        orphans.add(new Orphan(name, tick));
    }

    public int count() {
        return orphans.size();
    }
}
