import matrix.Simulation;
import matrix.core.Config;
import matrix.core.Geo;
import matrix.core.World;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.EnvironmentProgram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Probe: does the running world obey the displacement law the gait table
 * declares, and how full does the far-mover ledger actually get?
 *
 * <p>{@code Config.HUNT_DISP_BOUND_CM} buys the ring hunt its stopping rule
 * (#135): ring <i>d</i> may be skipped once its live-distance floor beats the
 * best so far, and anything that outruns the bound rides the far-mover ledger
 * instead — swept <b>linearly</b> by every hunt. #825 measured what crossing
 * the bound costs: two species crossing multiplies ledger candidates 428x
 * while the digest, the selftest and the hunt referee all report that nothing
 * happened.
 *
 * <p>{@code Config.huntBoundLine()} is the tight check and it runs inside
 * {@code --selftest}, so this probe deliberately does not repeat it. What it
 * adds is the half no table can prove about itself: the table is a
 * <b>model of the gait code</b>, and only a run can say whether the code
 * obeys it. {@code FlockMovement} clamps its heading, {@code SwarmMovement}
 * adds a half-speed pull to a full-speed draw, {@code CommuteMovement} clamps
 * its step — each of those is one edit away from spending more than the row
 * that speaks for it, and that edit would leave every other instrument green.
 *
 * <p>Three legs, and they fail for different reasons:
 *
 * <ol>
 * <li><b>Reach.</b> Every mover's largest measured single-tick displacement
 * from the snapshot its bucket was built on, against its declared row. Over
 * is a {@code CROSS}.</li>
 * <li><b>Census.</b> A mover the run produced that the table does not name is
 * an {@code UNDECLARED} — the shape a new entity class arrives in. Something
 * outside the table that never displaces is reported as {@code UNNAMED} and
 * not judged: only a displacement can reach the ledger.</li>
 * <li><b>Ledger.</b> The occupancy #825 found nobody could see: peak, mean,
 * and the stated ceiling. Reported here, judged by {@code --bench}.</li>
 * </ol>
 *
 * <p>Measured maxima are <b>at or below</b> their declared rows by
 * construction — a row is a worst case, and a worst case a 6,000-tick arc
 * never reaches is not a break. Seed 42, 6,000 ticks: 566 declared and 566
 * measured for {@code eco:sparrow}, ledger peak 2 against a ceiling of 76.
 *
 * <p>Usage: {@code java -cp out:probes/out HuntBound [ticks] [seed]}
 */
public final class HuntBound {

    public static void main(String[] args) throws Exception {
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Map<String, Integer> declared = new LinkedHashMap<>();
        for (Config.GaitReach r : Config.huntGaitReaches()) {
            declared.put(r.mover(), r.maxDisplacementCm());
        }
        List<String> teleporters = Config.huntTeleporters();

        Simulation sim = new Simulation(seed, null, null);
        World world = Probes.world(sim);
        // Sorted by label so two identical runs print identical bytes — the
        // bench's --twice pass is the reason, and a heap-ordered map would
        // fail it for reasons that live in the coroner, not the corpse.
        Map<String, Integer> measured = new TreeMap<>();

        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            for (MatrixEntity e : world.entities()) {
                if (!e.alive) {
                    continue;
                }
                // The exact quantity the law is written about: live position
                // against the snapshot the buckets were built on this tick.
                // noteDisplacement compares these two points, so this probe
                // measures what latches, not what a gait meant to spend.
                int d = ceilSqrt(Geo.distSqCm(e.xCm(), e.yCm(), e.snapXCm, e.snapYCm));
                String label = label(e);
                Integer best = measured.get(label);
                if (best == null || d > best) {
                    measured.put(label, d);
                }
            }
        }

        System.out.println("HUNT seed=" + seed + " ticks=" + ticks
                + " bound_cm=" + Config.HUNT_DISP_BOUND_CM
                + " ledger_ceiling=" + Config.huntLedgerCeiling());

        List<String> breaks = new ArrayList<>();
        for (Map.Entry<String, Integer> row : declared.entrySet()) {
            String mover = row.getKey();
            int declaredCm = row.getValue();
            Integer seen = measured.remove(mover);
            System.out.println("REACH " + mover + " declared=" + declaredCm
                    + " measured=" + (seen == null ? "absent" : seen)
                    + " headroom=" + (Config.HUNT_DISP_BOUND_CM - declaredCm));
            if (seen != null && seen > declaredCm) {
                breaks.add("CROSS " + mover + " measured=" + seen + " declared=" + declaredCm
                        + " — the gait spends more than the table says it can");
            }
        }
        for (String tenant : teleporters) {
            Integer seen = measured.remove(tenant);
            System.out.println("TENANT " + tenant + " declared=teleport"
                    + " measured=" + (seen == null ? "absent" : seen));
        }
        // What is left is in the world and not in the table. The rule is the
        // law's own: the table must name everything that MOVES, because only
        // a displacement can reach the ledger. A row that never displaces is
        // reported and not judged — the epilogue's sunrise is one, a Species
        // minted at the reboot outside Bestiary (MachineCity), rooted at
        // speed 0. It is still printed, because the reason it is harmless is
        // its speed, not its provenance, and the next one-off may have one.
        for (Map.Entry<String, Integer> orphan : measured.entrySet()) {
            if (orphan.getValue() > 0) {
                breaks.add("UNDECLARED " + orphan.getKey() + " measured=" + orphan.getValue()
                        + " — a mover the gait table does not name");
            } else {
                System.out.println("UNNAMED " + orphan.getKey() + " measured=0"
                        + " — outside the table, and never displaces");
            }
        }

        long sum = world.farMoverTickSum();
        long stepped = Math.max(1, world.farMoverTicks());
        System.out.println(String.format(Locale.ROOT,
                "LEDGER peak=%d mean=%.3f ceiling=%d ticks=%d",
                world.farMoverPeak(), (double) sum / stepped,
                Config.huntLedgerCeiling(), world.farMoverTicks()));

        for (String b : breaks) {
            System.out.println(b);
        }
        System.out.println("VERDICT " + (breaks.isEmpty() ? "HUNT_BOUND_HELD" : "HUNT_BOUND_CROSSED")
                + " movers=" + declared.size() + " breaks=" + breaks.size());
    }

    /** The key the gait table is written in: a catalog row is its species, anything else is its class. */
    private static String label(MatrixEntity e) {
        return e instanceof EnvironmentProgram ep ? "eco:" + ep.species.id()
                : e.getClass().getSimpleName();
    }

    /** Centimetres from squared centimetres, rounded up — the same direction the table rounds. */
    private static int ceilSqrt(long v) {
        int r = (int) Math.sqrt((double) v);
        while ((long) r * r < v) {
            r++;
        }
        while (r > 0 && (long) (r - 1) * (r - 1) >= v) {
            r--;
        }
        return r;
    }

    private HuntBound() {}
}
