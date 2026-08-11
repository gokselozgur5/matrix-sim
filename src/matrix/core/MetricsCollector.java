package matrix.core;

import matrix.entities.Pill;

/**
 * Samples the world into the METRIC grammar (D-020). Also emits the ECO
 * line (additive grammar, v2.5): flock cohesion as mean nearest-neighbor
 * distance among birds versus the analytic random-uniform baseline
 * (0.5/sqrt(density)) — flocking proven by numbers, no eyes needed.
 * And the ATTN line (additive grammar, D-024 P0): the RegionMap's
 * attention census — where the connected minds are, spoken in numbers.
 */
public final class MetricsCollector {
    private final World world;

    public MetricsCollector(World world) {
        this.world = world;
    }

    public String ecoLine(long tick) {
        java.util.List<matrix.entities.MatrixEntity> birds = new java.util.ArrayList<>();
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.eco.EnvironmentProgram p
                    && p.species.kingdom() == matrix.entities.eco.Kingdom.FAUNA_BIRD) {
                birds.add(e);
            }
        }
        if (birds.size() < 2) {
            return String.format(java.util.Locale.ROOT, "ECO tick=%d birds=%d", tick, birds.size());
        }
        long sum = 0;
        for (var b : birds) {
            long best = Long.MAX_VALUE;
            for (var o : birds) {
                if (o != b) {
                    best = Math.min(best, Geo.distSqCm(b.xCm(), b.yCm(), o.xCm(), o.yCm()));
                }
            }
            sum += Math.round(Math.sqrt((double) best));
        }
        long meanNn = sum / birds.size();
        double density = birds.size() / ((double) matrix.core.Config.WORLD_W_CM * matrix.core.Config.WORLD_H_CM);
        long baseline = Math.round(0.5 / Math.sqrt(density));
        return String.format(java.util.Locale.ROOT,
                "ECO tick=%d birds=%d flock_mnn_cm=%d random_baseline_cm=%d insects=%d flora=%d mammals=%d weather=%d",
                tick, birds.size(), meanNn, baseline,
                kingdomCount(matrix.entities.eco.Kingdom.FAUNA_INSECT),
                kingdomCount(matrix.entities.eco.Kingdom.FLORA),
                kingdomCount(matrix.entities.eco.Kingdom.FAUNA_MAMMAL),
                kingdomCount(matrix.entities.eco.Kingdom.WEATHER));
    }

    /**
     * The attention instrument (D-024 P0): region census plus the three
     * most-watched zones, ranked by avatar count with region-index
     * tiebreak. Region index is zone index, so the names are PlaceGraph's
     * own. Byte-stable: integers, fixed names, Locale.ROOT.
     */
    public String attnLine(long tick) {
        RegionMap regions = world.regions();
        java.util.List<PlaceGraph.Zone> zones = world.places().zones();
        int n = regions.regionCount();
        int hot = regions.hotCount();
        StringBuilder top = new StringBuilder();
        boolean[] taken = new boolean[n];
        for (int k = 0; k < 3 && k < n; k++) {
            int best = -1;
            for (int r = 0; r < n; r++) {
                if (!taken[r] && (best == -1 || regions.avatarCount(r) > regions.avatarCount(best))) {
                    best = r;
                }
            }
            taken[best] = true;
            if (k > 0) {
                top.append(',');
            }
            top.append(zones.get(best).name()).append(':').append(regions.avatarCount(best));
        }
        return String.format(java.util.Locale.ROOT,
                "ATTN tick=%d regions=%d hot=%d cold=%d top=\"%s\"", tick, n, hot, n - hot, top);
    }

    /** Per-kingdom census — the D-018 caps become checkable in the instrument stream. */
    private int kingdomCount(matrix.entities.eco.Kingdom kingdom) {
        int n = 0;
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.eco.EnvironmentProgram p
                    && p.species.kingdom() == kingdom) {
                n++;
            }
        }
        return n;
    }

    /**
     * Trace pressure, measured (unit #118) — a metric, not a class, exactly
     * as the #95 fold ruling has it: the dossier's "a drop concentrates
     * reds" claim turned into arithmetic the instruments already know. For
     * every live Agent, the distance to its nearest open-pirate avatar
     * (mean over agents: {@code trace_mnn_cm}) against the distance to its
     * nearest resident red ({@code red_baseline_cm}) — the hunting field
     * without the visitors, The One excluded exactly as the hunt excludes
     * him. Convergence shows as {@code trace_mnn_cm} collapsing toward (or
     * under) the baseline while a session is live; the flock-cohesion line
     * proved cohesion the same way, measured mean against a reference.
     *
     * <p>The root passes zion's open links in (D-012); the Matrix ships no
     * code and learns nothing — the collector reads positions it could
     * always read. Shape rule: the suffix (leading space, {@code
     * Locale.ROOT}) extends the ZION line exactly when open pirate links
     * exist and both populations are measurable; otherwise it is absent —
     * the ECO line's short-form precedent. Pure read: no draw, no state,
     * the digest cannot notice.
     */
    public String traceSuffix(java.util.List<matrix.entities.Avatar> pirateAvatars) {
        java.util.List<matrix.entities.Avatar> pirates = new java.util.ArrayList<>();
        for (var p : pirateAvatars) {
            if (p.alive && world.isPresent(p)) {
                pirates.add(p);
            }
        }
        if (pirates.isEmpty()) {
            return "";
        }
        java.util.List<matrix.entities.MatrixEntity> agents = new java.util.ArrayList<>();
        java.util.List<matrix.entities.Avatar> residents = new java.util.ArrayList<>();
        for (var e : world.entities()) {
            if (!e.alive) {
                continue;
            }
            if (e instanceof matrix.entities.Agent) {
                agents.add(e);
            } else if (e instanceof matrix.entities.Avatar a && a.pill == Pill.RED
                    && !(e instanceof matrix.entities.TheOne) && !containsIdentity(pirates, a)) {
                residents.add(a);
            }
        }
        if (agents.isEmpty() || residents.isEmpty()) {
            return "";
        }
        long traceSum = 0;
        long redSum = 0;
        for (var agent : agents) {
            traceSum += nearestCm(agent, pirates);
            redSum += nearestCm(agent, residents);
        }
        return String.format(java.util.Locale.ROOT, " trace_mnn_cm=%d red_baseline_cm=%d",
                traceSum / agents.size(), redSum / agents.size());
    }

    /** Identity membership, not equals — the pirate list holds the very objects the world walks. */
    private static boolean containsIdentity(java.util.List<matrix.entities.Avatar> list,
            matrix.entities.Avatar avatar) {
        for (var p : list) {
            if (p == avatar) {
                return true;
            }
        }
        return false;
    }

    private static long nearestCm(matrix.entities.MatrixEntity from,
            java.util.List<? extends matrix.entities.MatrixEntity> targets) {
        long best = Long.MAX_VALUE;
        for (var t : targets) {
            best = Math.min(best, from.pos.euclidSqCm(t.pos));
        }
        return Math.round(Math.sqrt((double) best));
    }

    public MetricSnapshot sample(long tick) {
        int alive = world.countAlive();
        double infected = alive == 0 ? 0.0 : (double) world.countInfected() / alive;
        return new MetricSnapshot(tick,
                world.count(Pill.BLUE),
                world.count(Pill.RED),
                world.countAgents(),
                alive,
                infected,
                (double) world.ledger().balance());
    }
}
