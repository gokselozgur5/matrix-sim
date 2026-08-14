package matrix.core;

import matrix.entities.Avatar;
import matrix.entities.MatrixEntity;
import matrix.entities.eco.Bestiary;
import matrix.entities.eco.EnvironmentProgram;
import matrix.entities.eco.Species;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The attention ledger of space (D-024). At boot every spatial-hash
 * cell is assigned to its nearest PlaceGraph zone center — data, not dice;
 * the partition is immutable after boot and region index IS zone index.
 * Each step the map re-reads attention from SNAPSHOT coordinates (D-017's
 * law: everyone senses the world as it WAS at tick start): a region is HOT
 * while any live Avatar's snapshot position lies in it, and stays HOT for
 * LOD_LINGER_TICKS after the last such sighting (hysteresis — attention
 * does not flicker). Avatars only: connected minds hold attention;
 * programs are the dream.
 *
 * P0 measured, P1 (#131) stretched cadence in the COLD, and P2 (#132)
 * parks: a region un-HOT for LOD_PARK_AFTER_TICKS consecutive ticks folds
 * its catalog residents into a per-region aggregate — per-species stored
 * ID lists, because exact restore is the SmithCopy-safe answer to the
 * gate's identity question — and they leave the walk entirely. Parked
 * reality breathes at ECO cadence through counted draws in region-index
 * order, and re-materializes the moment attention returns: same crowd,
 * different faces (#133). The reframe is law: determinism means the
 * DEGRADED film replays bit-identically, and the digest segment below
 * keeps parked reality fingerprinted. Park/Unpark queue through the
 * event path — this map never mutates the entity list; the state walk
 * runs in region-index order.
 *
 * P0 was measure-only: the map watched and nothing read it. P1 (#131)
 * opens the ledger for its first reader — EnvironmentProgram stretches
 * its Scheduler period by LOD_COLD_STRETCH while its snapshot lies in
 * a COLD region. Eco only: Avatars, agents and every other program
 * keep their cadence; connected minds are never degraded. A skipped
 * tick skips its rng draws too, so the DIGEST chain moves by design —
 * the reframe is law: determinism means the DEGRADED film replays
 * bit-identically, not that it matches the unstretched one. Zero rng
 * draws in the map itself; the state walk runs in region-index order.
 * Parking, aggregates, and the digest segment arrive only if and as
 * the D-024 verdict rules.
 *
 * P3 (#134, D-008): the substrate budget's HOT-slot cap arrives as a
 * scalar. Attention is measured exactly as before — the cap never edits
 * lastAttended, so linger hysteresis stays a pure attention memory —
 * and then capacity rules: when more regions are WATCHED
 * (attention-HOT) than the budget buys, only the top slotCap by
 * (avatarCount desc, region-index asc — the ATTN ranking) stay HOT;
 * the rest are forcibly demoted to the COLD path. Whatever COLD means
 * this phase (today the P1 stretch, parking when P2 lands), a demoted
 * region simply IS cold — composition, no special case. Each NEW
 * demotion fires the glitch counter once: demand exceeding substrate,
 * made visible (the D-008 dossier's instrument). The budget only says
 * how many; this map decides which, with zero draws.

 */
public final class RegionMap {

    /** The P2 state ladder. Ordinals are digest law — the region segment freezes them. */
    public enum LodState { HOT, COLD, PARKED }

    /** No park has been deferred in this cold era: the gatekeeper may propose one. */
    private static final int ARMED = -1;

    private final SpatialHash hash;
    private final int[] regionOfCell;
    private final int[][] cellsOfRegion;
    private final int[] avatarCounts;
    private final long[] lastAttended;
    private final boolean[] watched;
    private final boolean[] keep;
    private final boolean[] demotedPrev;
    private final boolean[] hot;
    private final int[] coldStreak;
    private final boolean[] parked;
    private final boolean[] refusalLogged;
    private final int[] parkDeferredAt;
    private final List<Integer>[][] parkedIds;
    private int hotCount;
    private long glitches;

    @SuppressWarnings("unchecked")
    public RegionMap(SpatialHash hash, PlaceGraph places) {
        this.hash = hash;
        List<PlaceGraph.Zone> zones = places.zones();
        this.regionOfCell = new int[hash.cellCount()];
        for (int c = 0; c < regionOfCell.length; c++) {
            long cx = hash.cellCenterXCm(c);
            long cy = hash.cellCenterYCm(c);
            int best = 0;
            long bestD = Long.MAX_VALUE;
            for (int z = 0; z < zones.size(); z++) {
                Position center = zones.get(z).center();
                long dx = cx - center.xCm();
                long dy = cy - center.yCm();
                long d = dx * dx + dy * dy;
                if (d < bestD) { // ties keep the lower zone index — the map is not a dice roll
                    bestD = d;
                    best = z;
                }
            }
            regionOfCell[c] = best;
        }
        // The partition, inverted (P2): the cells each region owns, in cell-index
        // order — re-materialization draws a cell, then a point inside it.
        int[] owned = new int[zones.size()];
        for (int c : regionOfCell) {
            owned[c]++;
        }
        this.cellsOfRegion = new int[zones.size()][];
        for (int r = 0; r < owned.length; r++) {
            cellsOfRegion[r] = new int[owned[r]];
        }
        int[] fill = new int[zones.size()];
        for (int c = 0; c < regionOfCell.length; c++) {
            int r = regionOfCell[c];
            cellsOfRegion[r][fill[r]++] = c;
        }
        this.avatarCounts = new int[zones.size()];
        this.lastAttended = new long[zones.size()];
        Arrays.fill(lastAttended, Long.MIN_VALUE / 2); // never attended, and no overflow on subtract
        this.watched = new boolean[zones.size()];
        this.keep = new boolean[zones.size()];
        this.demotedPrev = new boolean[zones.size()];
        this.hot = new boolean[zones.size()];
        this.coldStreak = new int[zones.size()];
        this.parked = new boolean[zones.size()];
        this.refusalLogged = new boolean[zones.size()];
        this.parkDeferredAt = new int[zones.size()];
        Arrays.fill(parkDeferredAt, ARMED);
        this.parkedIds = new List[zones.size()][Bestiary.ALL.size()];
        for (int r = 0; r < zones.size(); r++) {
            for (int s = 0; s < Bestiary.ALL.size(); s++) {
                parkedIds[r][s] = new ArrayList<>();
            }
        }
    }

    /**
     * Called by World.step() immediately after the hash rebuild: snapshots
     * are fresh, fresh positions are never read. Reused arrays, no
     * allocation (D-027); entity walk in list order (D-010), region walk
     * in region-index order. Two passes since P3: attention first
     * (unchanged law), then capacity — {@code slotCap} is the substrate
     * budget's scalar, Integer.MAX_VALUE when nothing commands one, and
     * an uncapped refresh is the P1 refresh bit for bit.
     * in region-index order. The cold streak counts consecutive un-HOT
     * ticks — attention (or its linger) resets it, and a fresh cold era
     * may log a fresh parking refusal.

     */
    public void refresh(long tick, List<MatrixEntity> entities, int slotCap) {
        Arrays.fill(avatarCounts, 0);
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Avatar) {
                avatarCounts[regionOfCell[hash.cellIndexOf(e.snapXCm, e.snapYCm)]]++;
            }
        }
        int watchedCount = 0;
        for (int r = 0; r < hot.length; r++) {
            if (avatarCounts[r] > 0) {
                lastAttended[r] = tick;
            }
            watched[r] = tick - lastAttended[r] <= Config.LOD_LINGER_TICKS;
            if (watched[r]) {
                watchedCount++;
            }
        }
        boolean capped = watchedCount > slotCap;
        if (capped) {
            // demand exceeds substrate: keep the top slotCap watched regions
            // by (avatarCount desc, region-index asc) — the ATTN ranking,
            // ties to the lower index; a lingering ghost region ranks last
            // and is the first the cap eats. Selection is pure arithmetic.
            Arrays.fill(keep, false);
            for (int k = 0; k < slotCap; k++) {
                int best = -1;
                for (int r = 0; r < hot.length; r++) {
                    if (watched[r] && !keep[r]
                            && (best == -1 || avatarCounts[r] > avatarCounts[best])) {
                        best = r;
                    }
                }
                keep[best] = true; // watchedCount > slotCap > k: a candidate always remains
            }
        }
        hotCount = 0;
        for (int r = 0; r < hot.length; r++) {
            hot[r] = watched[r] && (!capped || keep[r]);
            boolean demoted = watched[r] && !hot[r];
            if (demoted && !demotedPrev[r]) {
                glitches++; // a watched room went dim — the users will call it déjà vu
            }
            demotedPrev[r] = demoted;
            if (hot[r]) {
                hotCount++;
                coldStreak[r] = 0;
                refusalLogged[r] = false;
                parkDeferredAt[r] = ARMED; // attention ended the cold era; the next one asks fresh
            } else {
                coldStreak[r]++;
            }
        }
    }

    public int regionCount() {
        return hot.length;
    }

    /** Region owning the given snapshot coordinates — the cell's zone, pure index math, zero draws. */
    public int regionAt(int xCm, int yCm) {
        return regionOfCell[hash.cellIndexOf(xCm, yCm)];
    }

    /** Live Avatars whose snapshot lay in the region at the last refresh. */
    public int avatarCount(int region) {
        return avatarCounts[region];
    }

    /**
     * HOT at the last refresh: attended now or within the linger — AND
     * inside the substrate budget's slot cap (P3). What every fidelity
     * consumer reads; a capped-out region answers cold here while its
     * attention memory stays warm.
     */
    public boolean isHot(int region) {
        return hot[region];
    }

    public int hotCount() {
        return hotCount;
    }

    public LodState lodState(int region) {
        if (parked[region]) {
            return LodState.PARKED;
        }
        return hot[region] ? LodState.HOT : LodState.COLD;
    }

    /**
     * Un-HOT for LOD_PARK_AFTER_TICKS consecutive ticks, not yet parked, and
     * not holding a deferral: the region wants to fold. The deferral is the
     * memory the flush's failure paths used to lack (#807) — a Park that could
     * not be performed leaves the region exactly as it was, so without it this
     * level-triggered test stays true and re-proposes the same impossible park
     * on every following tick, forever.
     */
    boolean wantsPark(int region) {
        return !parked[region] && !hot[region]
                && coldStreak[region] >= Config.LOD_PARK_AFTER_TICKS
                && (parkDeferredAt[region] == ARMED
                        || coldStreak[region] - parkDeferredAt[region] >= Config.LOD_PARK_RETRY_TICKS);
    }

    /**
     * The flush could not fold this region — it refused, or found nothing
     * catalog to fold. Records the cold tick it answered on; the gatekeeper
     * re-arms LOD_PARK_RETRY_TICKS further cold ticks later, or immediately on
     * the next attention. Same clock as the streak, so no second time base
     * enters the class.
     */
    void deferPark(int region) {
        parkDeferredAt[region] = coldStreak[region];
    }

    /** The moment attention returns to a parked region — any live Avatar's snapshot enters it. */
    boolean wantsUnpark(int region) {
        return parked[region] && avatarCounts[region] > 0;
    }

    /** First refusal of this cold era? The TRACE line speaks once, not six hundred times. */
    boolean markRefused(int region) {
        if (refusalLogged[region]) {
            return false;
        }
        refusalLogged[region] = true;
        return true;
    }

    /** Catalog row of a species, or -1 for one-off rows (the sunrise): only the catalog parks. */
    static int catalogIndex(Species species) {
        return Bestiary.ALL.indexOf(species);
    }

    /** Opens the aggregate at the flush point; the World then folds residents one by one. */
    void beginPark(int region) {
        parked[region] = true;
        refusalLogged[region] = false;
        parkDeferredAt[region] = ARMED;
        coldStreak[region] = 0;
    }

    /** One resident folds in: the ID is stored — exact restore, the SmithCopy-safe choice. */
    void fold(int region, int catalogRow, int id) {
        parkedIds[region][catalogRow].add(id);
    }

    /** The aggregate population of one region — 0 unless parked. */
    public int parkedPopulation(int region) {
        int n = 0;
        for (List<Integer> ids : parkedIds[region]) {
            n += ids.size();
        }
        return n;
    }

    /**
     * The coarse life of parked reality: at ECO cadence every parked region
     * takes exactly TWO draws from the single stream, in region-index order
     * — an event draw (birth on 0, death on 1: 1/LOD_AGG_EVENT_DENOM each)
     * and a species draw, consumed whether or not the event fires, so the
     * stream's shape depends only on which regions are parked, never on
     * outcomes. A birth picks among species that are present and under
     * their catalog cap (statistics never invent a species and never break
     * a D-018 budget) and takes its id from nextId; a death forgets a
     * present species' newest face. Unbiased picks by construction: the
     * species bound is lcm(1..12).
     */
    void coarseTick(World w) {
        for (int r = 0; r < parked.length; r++) {
            if (!parked[r]) {
                continue;
            }
            int event = w.rng().nextInt(Config.LOD_AGG_EVENT_DENOM);
            int pick = w.rng().nextInt(Config.LOD_AGG_SPECIES_BOUND);
            if (event == 0) {
                int candidates = 0;
                for (int s = 0; s < parkedIds[r].length; s++) {
                    if (roomFor(r, s)) {
                        candidates++;
                    }
                }
                if (candidates == 0) {
                    continue;
                }
                int idx = pick % candidates;
                for (int s = 0; s < parkedIds[r].length; s++) {
                    if (roomFor(r, s) && idx-- == 0) {
                        parkedIds[r][s].add(w.allocateId());
                        break;
                    }
                }
            } else if (event == 1) {
                int candidates = 0;
                for (int s = 0; s < parkedIds[r].length; s++) {
                    if (!parkedIds[r][s].isEmpty()) {
                        candidates++;
                    }
                }
                if (candidates == 0) {
                    continue;
                }
                int idx = pick % candidates;
                for (int s = 0; s < parkedIds[r].length; s++) {
                    List<Integer> ids = parkedIds[r][s];
                    if (!ids.isEmpty() && idx-- == 0) {
                        ids.remove(ids.size() - 1);
                        break;
                    }
                }
            }
        }
    }

    private boolean roomFor(int region, int s) {
        int size = parkedIds[region][s].size();
        return size > 0 && size < Bestiary.ALL.get(s).populationCap();
    }

    /**
     * Seeded re-placement at the flush point: stored ids return in
     * catalog-then-fold order, each with a position drawn from the main
     * stream INSIDE the region — a cell the region owns, then a point in
     * the cell; edge cells clip to the world bound exactly as movement
     * does. Three draws per resident, count a pure function of the
     * aggregate. Clears the aggregate and the PARKED flag; the caller
     * owns the spawn, the log line and the ledger.
     */
    List<EnvironmentProgram> materialize(int region, Rng rng) {
        List<EnvironmentProgram> back = new ArrayList<>(parkedPopulation(region));
        int[] cells = cellsOfRegion[region];
        int half = Config.HASH_CELL_CM / 2;
        for (int s = 0; s < parkedIds[region].length; s++) {
            List<Integer> ids = parkedIds[region][s];
            for (int i = 0; i < ids.size(); i++) {
                int cell = cells[rng.nextInt(cells.length)];
                int x = Math.min(Config.WORLD_W_CM,
                        hash.cellCenterXCm(cell) - half + rng.nextInt(Config.HASH_CELL_CM));
                int y = Math.min(Config.WORLD_H_CM,
                        hash.cellCenterYCm(cell) - half + rng.nextInt(Config.HASH_CELL_CM));
                back.add(new EnvironmentProgram(ids.get(i), new Position(x, y), Bestiary.ALL.get(s)));
            }
            ids.clear();
        }
        parked[region] = false;
        return back;
    }

    /**
     * The crown's region segment (#180): rides after the entity walk —
     * putCount(regions), then per region in region-index order: lodState
     * ordinal, aggregate population, and the aggregate as framed
     * per-catalog-row id lists. The count frame IS the species count, and
     * the stored ids are restore state the referee must see (two worlds
     * differing only in who is parked must never hash equal — the
     * SmithCopy finding, one level up). Unparked regions carry an empty
     * aggregate: their residents are already in the walk. Fixed shape, no
     * entity type tag, prefix-free under the StateFraming grammar; the
     * Snapshot retains it in lockstep because both sinks share the walk.
     */
    void digestInto(StateSink sink) {
        sink.putCount(parked.length);
        for (int r = 0; r < parked.length; r++) {
            sink.putInt(lodState(r).ordinal());
            sink.putInt(parkedPopulation(r));
            for (List<Integer> ids : parkedIds[r]) {
                sink.putCount(ids.size());
                for (int i = 0; i < ids.size(); i++) {
                    sink.putInt(ids.get(i));
                }
            }
        }
    }

    /**
     * Cumulative D-008 glitch count: how many times a WATCHED region was
     * forcibly demoted by the slot cap (counted once per demotion edge).
     * The SUBSTRATE line quotes it; the root carries the number across.
     */
    public long capGlitches() {
        return glitches;
    }
}
