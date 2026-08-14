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
        int birds = 0;
        for (var e : world.entities()) {
            if (e.alive && e instanceof matrix.entities.eco.EnvironmentProgram p
                    && p.species.kingdom() == matrix.entities.eco.Kingdom.FAUNA_BIRD) {
                if (birds == birdXCm.length) {
                    int cap = Math.max(64, birds * 2);
                    birdXCm = java.util.Arrays.copyOf(birdXCm, cap);
                    birdYCm = java.util.Arrays.copyOf(birdYCm, cap);
                }
                birdXCm[birds] = e.xCm();
                birdYCm[birds] = e.yCm();
                birds++;
            }
        }
        if (birds < 2) {
            return String.format(java.util.Locale.ROOT, "ECO tick=%d birds=%d", tick, birds);
        }
        long meanNn = flockMeanNearestCm(birds);
        double density = birds / ((double) matrix.core.Config.WORLD_W_CM * matrix.core.Config.WORLD_H_CM);
        long baseline = Math.round(0.5 / Math.sqrt(density));
        return String.format(java.util.Locale.ROOT,
                "ECO tick=%d birds=%d flock_mnn_cm=%d random_baseline_cm=%d insects=%d flora=%d mammals=%d weather=%d",
                tick, birds, meanNn, baseline,
                kingdomCount(matrix.entities.eco.Kingdom.FAUNA_INSECT),
                kingdomCount(matrix.entities.eco.Kingdom.FLORA),
                kingdomCount(matrix.entities.eco.Kingdom.FAUNA_MAMMAL),
                kingdomCount(matrix.entities.eco.Kingdom.WEATHER));
    }

    // The ECO line's own index (#1026), and why the observer does not simply
    // borrow the world's. The flock the SIMULATION steers by is a snapshot
    // flock: SpatialHash freezes perception coordinates at rebuild and both
    // sides of every query use them (D-017), because a mid-tick rebuild would
    // let a bird perceive the future. The flock the OBSERVER reports on is the
    // live one — ecoLine runs at the METRIC boundary, after the walk and after
    // the flush, and reads xCm()/yCm(). Those are the same values only when
    // nothing has moved since rebuild, so reading the steering index here
    // would silently republish flock_mnn_cm as a different statistic. The
    // observer therefore indexes the positions it is actually reporting, and
    // the published figure does not move.
    //
    // Buffers are fields, not locals: an instrument that fires every
    // ECO_EVERY_TICKS ticks allocates once and refills afterwards (D-027),
    // exactly as the hash's buckets do. Single-threaded engine, and every one
    // of these is dead between two ecoLine calls.
    private int[] birdXCm = new int[0];
    private int[] birdYCm = new int[0];
    private int[] cellHead = new int[0];
    private int[] cellNext = new int[0];

    /**
     * Mean nearest-neighbour distance in centimetres over the {@code n} birds
     * already loaded into {@code birdXCm}/{@code birdYCm}.
     *
     * <p>The arithmetic is the all-pairs loop's, unchanged: for each bird the
     * squared distance to the nearest OTHER bird, rounded to cm, summed and
     * divided by the count. A minimum over a set does not depend on the order
     * the set is visited, so each bird's term — and therefore the printed
     * figure — is identical to the nested loop's. Pure read either way: no
     * draw, no state, nothing the digest can notice.
     *
     * <p>The index is a uniform grid over the birds' own bounding box, so no
     * coordinate is ever clamped into a cell it does not lie in, and the ring
     * floor is therefore exact: nothing in a cell {@code d} rings out from
     * the anchor can be nearer than {@code (d-1)} cells, so the search stops
     * exactly one ring past the best it holds — never sooner. That is the
     * ring hunts' argument (#135) without their displacement term, because
     * here the index and the distances read the same coordinates.
     *
     * <p>The grain is the world's own, {@link Config#HASH_CELL_CM}. A grain
     * fitted to the flock instead — a cell sized from the bounding box so a
     * couple of birds share it — was written, measured against this one and
     * removed: at {@code --scale 100} it wins while the flock is spread (9.0
     * ms against 14.4 ms at tick 300) and loses once the flock has condensed
     * (165 ms against 148 ms at tick 1,000), because a bounding box says
     * nothing about where inside it 14,000 birds actually are. Two lines of
     * arithmetic for a wash. The floor argument above holds at ANY cell size
     * and the answer is a minimum either way, so the grain is a speed knob
     * only — and this one is the knob the rest of the world is already set
     * to.
     */
    private long flockMeanNearestCm(int n) {
        int cell = Config.HASH_CELL_CM;
        int minX = birdXCm[0];
        int minY = birdYCm[0];
        int maxX = minX;
        int maxY = minY;
        for (int i = 1; i < n; i++) {
            minX = Math.min(minX, birdXCm[i]);
            maxX = Math.max(maxX, birdXCm[i]);
            minY = Math.min(minY, birdYCm[i]);
            maxY = Math.max(maxY, birdYCm[i]);
        }
        int nx = (int) (((long) maxX - minX) / cell) + 1;
        int ny = (int) (((long) maxY - minY) / cell) + 1;
        int cells = nx * ny;
        if (cellHead.length < cells) {
            // Grown to the CEILING, not to the need. nx and ny follow the
            // flock's bounding box, and that box breathes every hundred ticks
            // — sized to the need, this buffer is reallocated whenever the
            // flock widens, which is a per-tick allocation with a slow
            // heartbeat and AllocMeter measures it as one: 371 B/tick before
            // this unit, 402 with the buffer grown to the need, 351 with it
            // grown to the ceiling (D-027). Birds are in-world, so the box's
            // grid can never outgrow the hash's own, and one allocation
            // covers every later call.
            cellHead = new int[Math.max(cells,
                    (Config.WORLD_W_CM / cell + 1) * (Config.WORLD_H_CM / cell + 1))];
        }
        java.util.Arrays.fill(cellHead, 0, cells, -1);
        if (cellNext.length < n) {
            cellNext = new int[n];
        }
        for (int i = 0; i < n; i++) {
            int c = ((birdYCm[i] - minY) / cell) * nx + (birdXCm[i] - minX) / cell;
            cellNext[i] = cellHead[c];
            cellHead[c] = i;
        }
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += Math.round(Math.sqrt((double) nearestSqCm(i, minX, minY, nx, ny, cell)));
        }
        return sum / n;
    }

    /** One bird's nearest-other squared distance, by rings out of its own cell. */
    private long nearestSqCm(int i, int minX, int minY, int nx, int ny, int cell) {
        int ax = (birdXCm[i] - minX) / cell;
        int ay = (birdYCm[i] - minY) / cell;
        long best = Long.MAX_VALUE;
        int maxRing = Math.max(Math.max(ax, nx - 1 - ax), Math.max(ay, ny - 1 - ay));
        for (int d = 0; d <= maxRing; d++) {
            if (best != Long.MAX_VALUE) {
                long floor = (long) (d - 1) * cell;
                if (floor > 0 && floor * floor > best) {
                    break; // nothing at ring >= d can beat the best already held
                }
            }
            if (d == 0) {
                best = cellBest(ay * nx + ax, i, best);
                continue;
            }
            int x0 = Math.max(0, ax - d);
            int x1 = Math.min(nx - 1, ax + d);
            if (ay - d >= 0) {
                for (int gx = x0; gx <= x1; gx++) {
                    best = cellBest((ay - d) * nx + gx, i, best);
                }
            }
            if (ay + d < ny) {
                for (int gx = x0; gx <= x1; gx++) {
                    best = cellBest((ay + d) * nx + gx, i, best);
                }
            }
            int y0 = Math.max(0, ay - d + 1);
            int y1 = Math.min(ny - 1, ay + d - 1);
            if (ax - d >= 0) {
                for (int gy = y0; gy <= y1; gy++) {
                    best = cellBest(gy * nx + ax - d, i, best);
                }
            }
            if (ax + d < nx) {
                for (int gy = y0; gy <= y1; gy++) {
                    best = cellBest(gy * nx + ax + d, i, best);
                }
            }
        }
        return best;
    }

    /** One cell's chain against the running best — self skipped, live coordinates both sides. */
    private long cellBest(int cellIndex, int self, long best) {
        for (int j = cellHead[cellIndex]; j >= 0; j = cellNext[j]) {
            if (j == self) {
                continue;
            }
            long d = Geo.distSqCm(birdXCm[self], birdYCm[self], birdXCm[j], birdYCm[j]);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    /**
     * The attention instrument (D-024 P0): region census plus the three
     * most-watched zones, ranked by avatar count with region-index
     * tiebreak. Region index is zone index, so the names are PlaceGraph's
     * own. Byte-stable: integers, fixed names, Locale.ROOT. Since P3
     * (#134) hot= is the EFFECTIVE count — attention capped by the
     * substrate budget's slots — while top= stays raw attention; read
     * beside the SUBSTRATE line, the gap between demand and capacity
     * is the story.
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
     * <p>The root passes zion's pirate BOARD in (D-012); the Matrix ships no
     * code and learns nothing — the collector reads positions it could
     * always read. Shape rule (#374's statement, widened by #808 and stated
     * here in full): the suffix (leading space, {@code Locale.ROOT}) extends
     * the ZION line exactly when a body ON THE RIGS' BOARDS is ALIVE AND
     * PRESENT IN THE WORLD and both populations are measurable; otherwise it
     * is absent — the ECO line's short-form precedent.
     *
     * <p>The suffix follows BODIES; {@code links=} follows WIRES, counted
     * straight off the rigs. They are two different facts and they disagree
     * at both ends of a session, by design. At the open: a wire is
     * registered the instant it opens but its avatar only enters the world
     * at the next flush (D-005), so the opening tick prints {@code links>0}
     * with no suffix. At the close: a wire closes in the zion slot — the
     * last node — so its body stands in the Matrix for the rest of that tick
     * and can be measured while {@code links} has already fallen. Both are
     * the same rule read from the two ends: this metric measures the world,
     * not the board's bookkeeping.
     *
     * <p>Membership follows from the same rule (#808, #118's judgment call
     * 1 kept honest): the resident field is the hunting ground WITHOUT the
     * visitors, so a body the rig owns is never a resident — wire open, wire
     * cut, wire closed clean, all the same. Built from the open subset, the
     * exclusion let a pirate cross from the treatment group into the control
     * group for exactly one tick, and the tick it crossed on was the tick it
     * stood at an exit booth: the least representative red on the map, added
     * to the reference the whole argument is measured against. Pure read: no
     * draw, no state, the digest cannot notice.
     */
    public String traceSuffix(java.util.List<matrix.entities.Avatar> pirateBoard) {
        java.util.List<matrix.entities.Avatar> pirates = new java.util.ArrayList<>();
        for (var p : pirateBoard) {
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
            best = Math.min(best, Geo.distSqCm(from.xCm(), from.yCm(), t.xCm(), t.yCm()));
        }
        return Math.round(Math.sqrt((double) best));
    }

    public MetricSnapshot sample(long tick, long selfsub) {
        int alive = world.countAlive();
        double infected = alive == 0 ? 0.0 : (double) world.countInfected() / alive;
        return new MetricSnapshot(tick,
                world.count(Pill.BLUE),
                world.count(Pill.RED),
                world.countAgents(),
                alive,
                infected,
                (double) world.ledger().balance(),
                selfsub);
    }
}
