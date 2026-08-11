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
    private long unparks = 0;
    private ChronosLog chronosTap;

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
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            if (e.alive) {
                e.tick(this);
            }
        }
        if (tick % Config.ECO_EVERY_TICKS == 0) {
            regions.coarseTick(this); // parked reality breathes: counted draws, region-index order (D-024 P2)
        }
        // The LOD gatekeeper (D-024 P2): decisions queue AFTER the walk, in
        // region-index order, and therefore flush LAST — behind this tick's
        // infections, so the Park handler reads settled membership.
        for (int r = 0; r < regions.regionCount(); r++) {
            if (regions.wantsUnpark(r)) {
                queue(new WorldEvent.Unpark(r));
            } else if (regions.wantsPark(r)) {
                queue(new WorldEvent.Park(r));
            }
        }
        flush();
    }

    /** Boot-time flush so tick 1 already sees the seeded population. */
    public void flush() {
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
            } else if (ev instanceof WorldEvent.Park p) {
                // Chronos sees the fold as what it is to the walk: removals.
                removes += park(p.regionId());
            } else if (ev instanceof WorldEvent.Unpark u) {
                spawns += unpark(u.regionId());
            }
        }
        pending.clear();
        if (chronosTap != null) {
            chronosTap.onFlush(tick, spawns, removes, replaces);
        }
    }

    /**
     * The Park flush (D-024 P2, #132): folds the region's catalog residents
     * into the RegionMap aggregate and takes them out of the walk. Runs at
     * the end of the flush order, so it sees this tick's infections settled
     * — and refuses the whole region while anything self-replicating stands
     * in it: a wrapped mind must stay in the walk where the digest recurses
     * into it, and stored-id restore is only safe when nobody parked is
     * secretly someone else (the gate's identity ruling). One-off species
     * stay rendered — nobody will delete the sunrise, and nobody parks it
     * either. Membership is the snapshot cell's zone, the same D-017 law
     * attention reads; an empty region folds nothing and stays awake.
     */
    private int park(int regionId) {
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof matrix.entities.SelfReplicating
                    && regions.regionAt(e.snapXCm, e.snapYCm) == regionId) {
                if (regions.markRefused(regionId)) {
                    log(Severity.TRACE, "LOD: parking of " + places.zones().get(regionId).name()
                            + " refused — something self-replicating walks its streets");
                }
                return 0;
            }
        }
        List<MatrixEntity> folding = new ArrayList<>();
        for (MatrixEntity e : entities) {
            if (e.alive && e instanceof matrix.entities.eco.EnvironmentProgram p
                    && regions.regionAt(e.snapXCm, e.snapYCm) == regionId
                    && RegionMap.catalogIndex(p.species) >= 0) {
                folding.add(p);
            }
        }
        if (folding.isEmpty()) {
            return 0;
        }
        regions.beginPark(regionId);
        for (MatrixEntity e : folding) {
            regions.fold(regionId,
                    RegionMap.catalogIndex(((matrix.entities.eco.EnvironmentProgram) e).species), e.id);
        }
        entities.removeAll(folding);
        log(Severity.TRACE, "LOD: " + places.zones().get(regionId).name() + " parks — "
                + folding.size() + " residents fold into statistics; nobody is watching");
        return folding.size();
    }

    /**
     * The Unpark flush: seeded re-materialization at the flush point. Same
     * crowd — the stored ids — different faces: positions re-drawn inside
     * the region, headings fresh, and the residents re-enter the walk at
     * the back exactly like any spawn (D-010 order is list order, and both
     * films append identically). Every unpark IS a déjà vu (#133): the
     * FATE line names the zone and the count, and cache invalidation gets
     * priced exactly as the ops-console patch does — the D-022 mechanical-
     * meaning precedent — with DEJA_RESIDUE_SPIKE landing on the ledger.
     */
    private int unpark(int regionId) {
        List<matrix.entities.eco.EnvironmentProgram> back = regions.materialize(regionId, rng);
        entities.addAll(back);
        unparks++;
        ledger.accrue(Config.DEJA_RESIDUE_SPIKE);
        log(Severity.FATE, "déjà vu in " + places.zones().get(regionId).name()
                + " — " + back.size() + " residents re-materialize");
        return back.size();
    }

    /** Cumulative Unpark count — the ledger's second mechanical déjà-vu source, read by the probe bench (#133). */
    public long unparks() {
        return unparks;
    }

    /** Smith eats everything — except The One, until a surrender is on the table (v3 canon). */
    public MatrixEntity nearestNonReplicating(int fromXCm, int fromYCm, int selfId) {
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
        regions.digestInto(sink); // the crown's region segment: parked reality stays fingerprinted (D-024 P2)
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
