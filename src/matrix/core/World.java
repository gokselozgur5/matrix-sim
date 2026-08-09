package matrix.core;

import matrix.entities.MatrixEntity;
import matrix.entities.SelfReplicating;
import matrix.machine.Source;
import matrix.realworld.PodFarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class World {
    public static final int W = Config.WIDTH;
    public static final int H = Config.HEIGHT;

    private final List<MatrixEntity> entities = new ArrayList<>();
    private final List<MatrixEntity> toAdd = new ArrayList<>();
    private final Set<MatrixEntity> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Rng rng;
    private final EventBus bus;
    private final PodFarm pods = new PodFarm();
    private final Source source;

    private long tick = 0;
    private int version = 6;
    private SystemState state = SystemState.NORMAL;
    private double anomaly = 0;
    private int dejaFlash = 0;

    public World(long seed, EventBus bus) {
        this.rng = new Rng(seed);
        this.bus = bus;
        this.source = new Source(this);
    }

    public Rng rng() { return rng; }
    public EventBus bus() { return bus; }
    public PodFarm pods() { return pods; }
    public Source source() { return source; }
    public long tick() { return tick; }
    public int version() { return version; }
    public SystemState state() { return state; }
    public double anomaly() { return anomaly; }
    public int dejaFlash() { return dejaFlash; }
    public List<MatrixEntity> entities() { return entities; }

    public void setState(SystemState s) { state = s; }
    public void bumpVersion() { version++; }
    public void setAnomaly(double a) { anomaly = a; }
    public void addAnomaly(double d) { anomaly = Math.min(Config.ANOMALY_MAX, anomaly + d); }
    public void setDejaFlash(int ticks) { dejaFlash = ticks; }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void spawn(MatrixEntity e) {
        e.alive = true;
        toAdd.add(e);
    }

    public void remove(MatrixEntity e) {
        e.alive = false;
        toRemove.add(e);
    }

    public void replace(MatrixEntity oldE, MatrixEntity newE) {
        newE.x = oldE.x;
        newE.y = oldE.y;
        remove(oldE);
        spawn(newE);
    }

    public MatrixEntity nearest(MatrixEntity from, Predicate<MatrixEntity> p) {
        MatrixEntity best = null;
        int bd = Integer.MAX_VALUE;
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            if (!e.alive || e == from || !p.test(e)) continue;
            int d = from.dist(e);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    public int count(Predicate<MatrixEntity> p) {
        int n = 0;
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            if (e.alive && p.test(e)) n++;
        }
        return n;
    }

    public double infectedFraction() {
        int total = 0, infected = 0;
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            if (!e.alive) continue;
            total++;
            if (e instanceof SelfReplicating) infected++;
        }
        return total == 0 ? 0 : (double) infected / total;
    }

    public void log(Severity s, String m) {
        bus.publish(new Event(tick, s, m));
    }

    public void step(Director d) {
        tick++;
        if (state == SystemState.NEGOTIATION) {
            d.negotiationTick(this);
            flushPending();
            return;
        }
        for (int i = 0; i < entities.size(); i++) {
            MatrixEntity e = entities.get(i);
            if (e.alive) e.tick(this);
        }
        d.tick(this);
        flushPending();
        if (dejaFlash > 0) dejaFlash--;
    }

    public void flushPending() {
        if (!toRemove.isEmpty()) {
            entities.removeIf(toRemove::contains);
            toRemove.clear();
        }
        if (!toAdd.isEmpty()) {
            entities.addAll(toAdd);
            toAdd.clear();
        }
    }
}
