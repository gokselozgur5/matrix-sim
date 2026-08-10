package matrix.machine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The ledger of everything that refused to die (D-025). One entry per
 * orphan, however many times it survives collection — the count is
 * a census, not an event tally (skeptic finding).
 */
public final class OrphanRegistry {
    public record Orphan(String name, long refusedAtTick) {}

    private final Set<String> known = new LinkedHashSet<>();
    private final List<Orphan> orphans = new ArrayList<>();

    /** Returns true only on first registration — the log must not claim what the ledger did not do. */
    public boolean register(String name, long tick) {
        if (known.add(name)) {
            orphans.add(new Orphan(name, tick));
            return true;
        }
        return false;
    }

    public int count() {
        return orphans.size();
    }
}
