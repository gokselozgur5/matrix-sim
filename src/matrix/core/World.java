package matrix.core;

import matrix.entities.Agent;
import matrix.entities.AgentSmith;
import matrix.entities.Avatar;
import matrix.entities.ExileProgram;
import matrix.entities.MatrixEntity;
import matrix.entities.Oracle;
import matrix.entities.Pill;
import matrix.entities.SmithCopy;
import matrix.entities.SmithPrime;

import java.util.ArrayList;
import java.util.List;

/**
 * The Matrix itself: entity registry, tick, spatial state. Contains NO
 * real-world objects (D-012). Mutations queue as WorldEvents and flush in
 * order at tick end (D-005); iteration is spawn/id order (D-010).
 */
public final class World {
    private final Rng rng;
    private final EventBus bus;
    private final PlaceGraph places;
    private final SpatialHash hash = new SpatialHash(Config.WORLD_W_CM, Config.WORLD_H_CM, Config.HASH_CELL_CM);
    private final RegionMap regions;
    private final List<MatrixEntity> entities = new ArrayList<>();
    private final List<WorldEvent> pending = new ArrayList<>();
    private final AnomalyLedger ledger = new AnomalyLedger();
    private SystemState state = SystemState.NORMAL;
    private int version = 6;
    private long tick = 0;
    private int nextId = 1;
    private ChronosLog chronosTap;
    // #135 hunt-index state: the ring hunts may only trust the buckets while
    // the entity list is exactly the list the rebuild walked. True from
    // rebuild to flush — the entity loop, where every hot hunt lives; false
    // everywhere else (boot, observers, console), where hunts take the
    // linear scan and stay exact by definition.
    private boolean huntIndexValid = false;
    private int huntAgents;
    private int huntReds;
    private int huntNonRep;

    public World(Rng rng, EventBus bus, PlaceGraph places) {
        this.rng = rng;
        this.bus = bus;
        this.places = places;
        this.regions = new RegionMap(hash, places);
    }

    /** Snapshot neighbor query (D-017): both sides use tick-start perception coordinates. */
    public List<MatrixEntity> nearby(MatrixEntity self, int radiusCm) {
        return hash.near(self, radiusCm);
    }

    public Rng rng() {
        return rng;
    }

    public PlaceGraph places() {
        return places;
    }

    public RegionMap regions() {
        return regions;
    }

    public long tick() {
        return tick;
    }

    public List<MatrixEntity> entities() {
        return entities;
    }

    public AnomalyLedger ledger() {
        return ledger;
    }

    public SystemState state() {
        return state;
    }

    public void setState(SystemState s) {
        state = s;
    }

    public int version() {
        return version;
    }

    public void bumpVersion() {
        version++;
    }

    public int allocateId() {
        return nextId++;
    }

    public void queue(WorldEvent event) {
        pending.add(event);
    }

    /** D-023 stage 1: installed at boot, the tap observes every flushed batch; it never steers one. */
    public void installChronosTap(ChronosLog tap) {
        this.chronosTap = tap;
    }

    /** During a negotiation the world holds its breath but the clock does not — instruments stay honest. */
    public void advanceFrozen() {
        tick++;
    }

    public void step() {
        tick++;
        hash.rebuild(entities);
        regions.refresh(tick, entities); // attention reads the snapshots the rebuild just froze (D-024 P0)
        countHuntables();
        huntIndexValid = true;
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            if (e.alive) {
                e.tick(this);
            }
        }
        flush();
    }

    /** Boot-time flush so tick 1 already sees the seeded population. */
    public void flush() {
        huntIndexValid = false; // the list is about to change under the buckets
        int spawns = 0;
        int removes = 0;
        int replaces = 0;
        for (WorldEvent ev : pending) {
            if (ev instanceof WorldEvent.Spawn s) {
                entities.add(s.entity());
                spawns++;
            } else if (ev instanceof WorldEvent.Remove r) {
                entities.removeIf(e -> e.id == r.entityId());
                removes++;
            } else if (ev instanceof WorldEvent.Replace rp) {
                for (int i = 0; i < entities.size(); i++) {
                    if (entities.get(i).id == rp.entityId()) {
                        entities.set(i, rp.replacement());
                        break;
                    }
                }
                replaces++;
            }
        }
        pending.clear();
        if (chronosTap != null) {
            chronosTap.onFlush(tick, spawns, removes, replaces);
        }
    }

    /** Smith eats everything — except The One, until a surrender is on the table (v3 canon). */
    public MatrixEntity nearestNonReplicating(int fromXCm, int fromYCm, int selfId) {
        if (!huntIndexValid) {
            return scanNearestNonReplicating(fromXCm, fromYCm, selfId);
        }
        MatrixEntity ring = huntNonRep == 0 ? null : ringNearest(fromXCm, fromYCm, selfId, MODE_NONREP);
        if (Config.HUNT_VERIFY) {
            verify("nearestNonReplicating", ring, scanNearestNonReplicating(fromXCm, fromYCm, selfId),
                    fromXCm, fromYCm);
        }
        return ring;
    }

    private MatrixEntity scanNearestNonReplicating(int fromXCm, int fromYCm, int selfId) {
        MatrixEntity best = null;
        long bestD = Long.MAX_VALUE;
        for (MatrixEntity e : entities) {
            if (!e.alive || e.id == selfId || e instanceof matrix.entities.SelfReplicating
                    || e instanceof matrix.entities.TheOne) {
                continue;
            }
            long d = Geo.distSqCm(fromXCm, fromYCm, e.xCm(), e.yCm());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    public int countInfected() {
        int n = 0;
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof matrix.entities.SelfReplicating) n++;
        }
        return n;
    }

    public void kill(Avatar avatar, String by) {
        avatar.alive = false;
        log(Severity.BAD, by + ": session of " + avatar.pilotName + " terminated — the hard way");
    }

    public void log(Severity sev, String msg) {
        bus.publish(new Event(tick, sev, msg));
    }

    public Agent nearestAgent(int fromXCm, int fromYCm) {
        if (!huntIndexValid) {
            return scanNearestAgent(fromXCm, fromYCm);
        }
        Agent ring = huntAgents == 0 ? null : (Agent) ringNearest(fromXCm, fromYCm, -1, MODE_AGENT);
        if (Config.HUNT_VERIFY) {
            verify("nearestAgent", ring, scanNearestAgent(fromXCm, fromYCm), fromXCm, fromYCm);
        }
        return ring;
    }

    private Agent scanNearestAgent(int fromXCm, int fromYCm) {
        Agent best = null;
        long bestD = Long.MAX_VALUE;
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Agent a) {
                long d = Geo.distSqCm(fromXCm, fromYCm, a.xCm(), a.yCm());
                if (d < bestD) {
                    bestD = d;
                    best = a;
                }
            }
        }
        return best;
    }

    /** Agents hunt reds — but not The One: they tried that in three films. */
    public Avatar nearestRed(int fromXCm, int fromYCm) {
        if (!huntIndexValid) {
            return scanNearestRed(fromXCm, fromYCm);
        }
        Avatar ring = huntReds == 0 ? null : (Avatar) ringNearest(fromXCm, fromYCm, -1, MODE_RED);
        if (Config.HUNT_VERIFY) {
            verify("nearestRed", ring, scanNearestRed(fromXCm, fromYCm), fromXCm, fromYCm);
        }
        return ring;
    }

    private Avatar scanNearestRed(int fromXCm, int fromYCm) {
        Avatar best = null;
        long bestD = Long.MAX_VALUE;
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Avatar a && a.pill == Pill.RED
                    && !(e instanceof matrix.entities.TheOne)) {
                long d = Geo.distSqCm(fromXCm, fromYCm, a.xCm(), a.yCm());
                if (d < bestD) {
                    bestD = d;
                    best = a;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // The ring hunts (#135) — D-017's promised move of hunts onto the hash,
    // under the scans' own semantics, reproduced exactly:
    //
    //   * LIVE reads. The scans read live positions and live predicates
    //     (alive, pill) at call time; snapshot cells serve only as a
    //     candidate INDEX. Every distance below recomputes from live ints.
    //   * The tie-break. The scans keep the FIRST candidate in list order
    //     among equal distances (strict <). The rebuild stamps each
    //     entity's list position into seq; the ring keeps the lexicographic
    //     minimum of (distance, seq) — the same winner, portable across
    //     buckets.
    //   * The universe. Buckets hold exactly the entities alive at rebuild,
    //     and the list cannot change while huntIndexValid holds (mutations
    //     queue until flush). Nothing resurrects mid-tick, so alive-now
    //     implies bucketed. Mid-loop mutations only shrink the huntable
    //     sets (an agent's catch flips RED to BLUE; kill() flips alive) —
    //     the Director's and the console's awakenings run after flush,
    //     where the index already refused — so a zero count at rebuild is
    //     a zero for the whole loop and answers null in O(1).
    //   * Movers. A candidate found via its snapshot cell may have moved
    //     since rebuild, so ring d's live-distance floor is
    //     (d-1)*cell - HUNT_DISP_BOUND_CM; anything displaced beyond the
    //     bound latched itself onto the far-mover ledger at the moment it
    //     moved (MatrixEntity.noteDisplacement), and the ledger is swept
    //     linearly after the rings. The search stops only when the next
    //     ring's floor strictly exceeds the best distance — an equal
    //     distance farther out could carry a smaller seq and must be seen.
    //
    // Zero rng draws, zero allocation, zero digest surface: the referee
    // (Config.HUNT_VERIFY) replays the scan per call and throws on the
    // first divergence.
    // ------------------------------------------------------------------

    private static final int MODE_AGENT = 0;
    private static final int MODE_RED = 1;
    private static final int MODE_NONREP = 2;

    private static boolean matches(int mode, MatrixEntity e, int selfId) {
        if (!e.alive) {
            return false;
        }
        return switch (mode) {
            case MODE_AGENT -> e instanceof Agent;
            case MODE_RED -> e instanceof Avatar a && a.pill == Pill.RED
                    && !(e instanceof matrix.entities.TheOne);
            default -> e.id != selfId && !(e instanceof matrix.entities.SelfReplicating)
                    && !(e instanceof matrix.entities.TheOne);
        };
    }

    /** One walk at rebuild prices the three prey sets — alive-at-rebuild upper bounds, shrink-only mid-loop. */
    private void countHuntables() {
        int agents = 0;
        int reds = 0;
        int nonRep = 0;
        for (MatrixEntity e : entities) {
            if (!e.alive) {
                continue;
            }
            if (e instanceof Agent) {
                agents++;
            }
            if (e instanceof Avatar a && a.pill == Pill.RED
                    && !(e instanceof matrix.entities.TheOne)) {
                reds++;
            }
            if (!(e instanceof matrix.entities.SelfReplicating)
                    && !(e instanceof matrix.entities.TheOne)) {
                nonRep++;
            }
        }
        huntAgents = agents;
        huntReds = reds;
        huntNonRep = nonRep;
    }

    // Reused scratch of the running best's key — single-threaded engine,
    // valid only inside one ringNearest call (the SpatialHash.scratch law).
    private long huntBestD;
    private int huntBestSeq;

    private MatrixEntity ringNearest(int fromXCm, int fromYCm, int selfId, int mode) {
        int cell = hash.cellSizeCm();
        int nx = hash.cellsXCount();
        int ny = hash.cellsYCount();
        // One clamp law (SpatialHash.bucketIndex's): the anchor is the cell of
        // the from-point projected into the grid; the ring floor stays sound
        // for any from because projection onto the grid never lengthens a
        // distance to a point inside it.
        int ax = Math.min(nx - 1, Math.max(0, fromXCm / cell));
        int ay = Math.min(ny - 1, Math.max(0, fromYCm / cell));
        MatrixEntity best = null;
        huntBestD = Long.MAX_VALUE;
        huntBestSeq = Integer.MAX_VALUE;
        int maxRing = Math.max(Math.max(ax, nx - 1 - ax), Math.max(ay, ny - 1 - ay));
        for (int d = 0; d <= maxRing; d++) {
            if (best != null) {
                long floor = (long) (d - 1) * cell - Config.HUNT_DISP_BOUND_CM;
                if (floor > 0 && floor * floor > huntBestD) {
                    break; // no candidate at ring >= d can beat OR tie the best
                }
            }
            if (d == 0) {
                best = huntCell(ax, ay, fromXCm, fromYCm, selfId, mode, best);
                continue;
            }
            int x0 = ax - d;
            int x1 = ax + d;
            int y0 = ay - d;
            int y1 = ay + d;
            int cx0 = Math.max(0, x0);
            int cx1 = Math.min(nx - 1, x1);
            if (y0 >= 0) {
                for (int cx = cx0; cx <= cx1; cx++) {
                    best = huntCell(cx, y0, fromXCm, fromYCm, selfId, mode, best);
                }
            }
            if (y1 < ny) {
                for (int cx = cx0; cx <= cx1; cx++) {
                    best = huntCell(cx, y1, fromXCm, fromYCm, selfId, mode, best);
                }
            }
            int cy0 = Math.max(0, y0 + 1);
            int cy1 = Math.min(ny - 1, y1 - 1);
            if (x0 >= 0) {
                for (int cy = cy0; cy <= cy1; cy++) {
                    best = huntCell(x0, cy, fromXCm, fromYCm, selfId, mode, best);
                }
            }
            if (x1 < nx) {
                for (int cy = cy0; cy <= cy1; cy++) {
                    best = huntCell(x1, cy, fromXCm, fromYCm, selfId, mode, best);
                }
            }
        }
        // The teleports: whoever outran the displacement law is on the ledger,
        // swept whole — live distance, same lexicographic rule. Indexed walk:
        // an iterator here would be the hot path's only allocation.
        List<MatrixEntity> far = hash.farMovers();
        for (int i = 0; i < far.size(); i++) {
            MatrixEntity e = far.get(i);
            if (!matches(mode, e, selfId)) {
                continue;
            }
            long dd = Geo.distSqCm(fromXCm, fromYCm, e.xCm(), e.yCm());
            if (dd < huntBestD || (dd == huntBestD && e.seq < huntBestSeq)) {
                best = e;
                huntBestD = dd;
                huntBestSeq = e.seq;
            }
        }
        return best;
    }

    /** One bucket's candidates against the running best — live predicate, live distance, (dist, seq) rule. */
    private MatrixEntity huntCell(int cx, int cy, int fromXCm, int fromYCm,
            int selfId, int mode, MatrixEntity best) {
        List<MatrixEntity> bucket = hash.bucketAt(cx, cy);
        for (int i = 0; i < bucket.size(); i++) {
            MatrixEntity e = bucket.get(i);
            if (!matches(mode, e, selfId)) {
                continue;
            }
            long dd = Geo.distSqCm(fromXCm, fromYCm, e.xCm(), e.yCm());
            if (dd < huntBestD || (dd == huntBestD && e.seq < huntBestSeq)) {
                best = e;
                huntBestD = dd;
                huntBestSeq = e.seq;
            }
        }
        return best;
    }

    /** The referee's gavel: ring and scan must hand back the very same object, null included. */
    private void verify(String hunt, MatrixEntity ring, MatrixEntity scan, int fromXCm, int fromYCm) {
        if (ring == scan) {
            return;
        }
        throw new IllegalStateException("HUNT DIVERGENCE " + hunt
                + " tick=" + tick + " from=(" + fromXCm + "," + fromYCm + ")"
                + " ring=" + describe(ring, fromXCm, fromYCm)
                + " scan=" + describe(scan, fromXCm, fromYCm));
    }

    private static String describe(MatrixEntity e, int fromXCm, int fromYCm) {
        if (e == null) {
            return "null";
        }
        return "id " + e.id + " seq " + e.seq + " d2 "
                + Geo.distSqCm(fromXCm, fromYCm, e.xCm(), e.yCm())
                + (e.farMover ? " (far)" : "");
    }

    public List<Avatar> aliveAvatars(Pill pill) {
        List<Avatar> out = new ArrayList<>();
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Avatar a && a.pill == pill) {
                out.add(a);
            }
        }
        return out;
    }

    public int count(Pill pill) {
        return aliveAvatars(pill).size();
    }

    /** The cap counts LATENT reds too: a wrapped mind is still awake underneath (D-001; skeptic finding). */
    public int countRedIncludingWrapped() {
        int n = 0;
        for (MatrixEntity e : entities) {
            if (!e.alive) {
                continue;
            }
            if (e instanceof Avatar a && a.pill == Pill.RED) {
                n++;
            } else if (e instanceof SmithCopy c
                    && c.original instanceof Avatar wrapped && wrapped.pill == Pill.RED) {
                n++;
            }
        }
        return n;
    }

    public int countAgents() {
        int n = 0;
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof Agent) n++;
        }
        return n;
    }

    public int countAlive() {
        int n = 0;
        for (MatrixEntity e : entities) {
            if (e.alive) n++;
        }
        return n;
    }

    /** Identity membership — the Source must not collect what is no longer itself (ghost fix). */
    public boolean isPresent(MatrixEntity e) {
        for (MatrixEntity x : entities) {
            if (x == e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Canonical state feed for the digest chain — id order, type-tagged,
     * framed (D-020). SmithCopy recurses into its wrapped original, so
     * restore-relevant state is visible to the referee (skeptic finding:
     * two realities differing only inside a copy must not hash equal).
     * One walk, any sink (D-023 stage 3): the hashing sink turns it into
     * a DIGEST line, the retaining sink into a Snapshot — same order,
     * same frames, so the two can never tell different stories. Reads
     * only; the walk draws nothing and moves nothing.
     */
    public void digestInto(StateSink sink) {
        sink.putLong(tick);
        sink.putLong(rng.draws());
        sink.putInt(nextId);
        sink.putInt(state.ordinal());
        sink.putInt(version);
        sink.putLong(ledger.balance());
        sink.putCount(entities.size());
        for (MatrixEntity e : entities) {
            digestEntity(sink, e);
        }
    }

    private void digestEntity(StateSink sink, MatrixEntity e) {
        sink.putInt(typeTag(e));
        sink.putInt(e.id);
        sink.putInt(e.xCm());
        sink.putInt(e.yCm());
        sink.putInt(e.alive ? 1 : 0);
        sink.putInt(e instanceof Avatar a ? a.pill.ordinal() : -1);
        if (e instanceof matrix.entities.eco.EnvironmentProgram p) {
            sink.putInt(p.species.id().hashCode());
            sink.putInt(p.headingX);
            sink.putInt(p.headingY);
        }
        if (e instanceof SmithCopy c) {
            digestEntity(sink, c.original);
        }
    }

    private static int typeTag(MatrixEntity e) {
        if (e instanceof matrix.entities.TheOne) return 9;
        if (e instanceof matrix.entities.eco.EnvironmentProgram) return 8;
        if (e instanceof SmithCopy) return 7;
        if (e instanceof SmithPrime) return 6;
        if (e instanceof Oracle) return 4;
        if (e instanceof ExileProgram) return 5;
        if (e instanceof AgentSmith) return 3;
        if (e instanceof Agent) return 2;
        if (e instanceof Avatar) return 1;
        return 0;
    }
}
