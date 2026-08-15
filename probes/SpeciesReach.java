import matrix.Simulation;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.Bestiary;
import matrix.entities.eco.EnvironmentProgram;
import matrix.entities.eco.Species;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Probe: does the world contain a species no list in the Bestiary names?
 *
 * <p>#974 split {@link Bestiary} in three, and the difference between the parts
 * is a digest question: {@code CATALOG} is what the seeding loop walks,
 * {@code ONE_OFFS} is what the arc mints by hand, {@code EVERY} is what the
 * world can CONTAIN. {@code World.digestEntity} feeds
 * {@code p.species.id().hashCode()} into the seal for whatever is in the world
 * and cannot tell which list a row was written on.
 *
 * <p>The defect that split was written to end is exactly this probe's question.
 * The sunrise was a full catalog row — kingdom, gait, cadence, cap, speed —
 * written INLINE at the call site that spawned it, where no reader could see it
 * and no budget could count it. It was a digest input that appeared in no list.
 * The rename ({@code ALL} no longer compiles) makes the next derivation state
 * which set it means; it does not stop the next inline row from existing.
 *
 * <p>So this asks the world instead of the file: run the arc, collect the
 * species of every {@link EnvironmentProgram} that ever appears, and compare
 * that set against the three lists. What must hold is containment —
 * {@code reached ⊆ EVERY} — and the interesting number is the other direction:
 * a species in {@code EVERY} that the canonical arc never reaches is not a
 * defect, but it is a row no run of this seed has ever tested.
 *
 * <p>{@code SealHygiene} pins the hash of each id in {@code EVERY}. It cannot
 * ask this question, because it deliberately boots no world — "the one probe
 * that cannot be flaky" reads constants only. This one is the half that needs a
 * universe, and the two together say: the list is what the seal borrows, and
 * the world holds nothing outside the list.
 *
 * <pre>
 * java -cp out:probes/out SpeciesReach [ticks] [seed]
 * </pre>
 *
 * <p>Judged, so it owes an exit code (#1093): 0 when nothing in the world is
 * unlisted, 1 on the first row that is.
 */
public final class SpeciesReach {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);

        // Sampled every tick rather than at the end: a species that lives and
        // dies inside the arc is a digest input for exactly as long as it is in
        // the world, and a census taken at t=6000 would miss it entirely. The
        // sunrise is painted at the reboot and never removed, so the end-state
        // census would have caught THAT one — which is precisely why it is the
        // wrong instrument to build for the next one.
        Set<String> reached = new TreeSet<>();
        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            for (MatrixEntity e : world.entities()) {
                if (e instanceof EnvironmentProgram p) {
                    reached.add(p.species.id());
                }
            }
        }

        Set<String> catalog = ids(Bestiary.CATALOG);
        Set<String> oneOffs = ids(Bestiary.ONE_OFFS);
        Set<String> every = ids(Bestiary.EVERY);

        Set<String> unlisted = new TreeSet<>(reached);
        unlisted.removeAll(every);
        Set<String> untouched = new TreeSet<>(every);
        untouched.removeAll(reached);

        for (String id : unlisted) {
            System.out.println("REACH id=\"" + id + "\" IN THE WORLD AND IN NO LIST"
                    + " — a digest input the Bestiary does not name");
        }
        for (String id : untouched) {
            System.out.println("REACH id=\"" + id + "\" listed=EVERY reached=no"
                    + " — no run of this seed has ever exercised the row");
        }
        System.out.println("REACH seed=" + seed + " ticks=" + ticks
                + " reached=" + reached.size()
                + " catalog=" + catalog.size()
                + " one_offs=" + oneOffs.size()
                + " every=" + every.size()
                + " untouched=" + untouched.size());
        Probes.leave("VERDICT " + (unlisted.isEmpty() ? "EVERY_CONTAINS_THE_WORLD" : "WORLD_HOLDS_UNLISTED")
                + " unlisted=" + unlisted.size(), unlisted.isEmpty());
    }

    private static Set<String> ids(java.util.List<Species> list) {
        Set<String> out = new LinkedHashSet<>();
        for (Species s : list) {
            out.add(s.id());
        }
        return out;
    }

    private SpeciesReach() {}
}
