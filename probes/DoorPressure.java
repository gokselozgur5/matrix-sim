import matrix.Simulation;
import matrix.core.Config;
import matrix.machine.DoorPolicy;
import matrix.machine.SubstrateBudget;
import matrix.realworld.NeuralLink;
import matrix.realworld.RealWorld;
import matrix.zion.Zion;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: invariant I-1 — <i>no reinsertion without a pod and a HOT slot</i> —
 * put under pressure inside a running universe, so the two refusals
 * {@code DoorPolicy} was built to speak are spoken by the door the root
 * wired rather than by a bench rig (#886, cut from #885).
 *
 * <p>#885 shipped the bouncer and proved it by construction: a scratch driver
 * built a {@code DoorPolicy} out of a hand-made {@code IntSupplier} and a
 * hand-starved {@code SubstrateBudget} and watched it refuse. That proves
 * {@code decide()}. It cannot prove the DOOR, because the ports it refused
 * through were not the ports {@code Simulation} wires — and a driver that
 * builds its own subject cannot notice the root handing the real one a
 * supplier that reads the wrong number. Meanwhile the canonical film never
 * asks: {@code DOORCLAIM} below measures that, and the number that matters is
 * {@code free_pods_min_ashore} — the rack IS full for the first stretch of
 * every run and full again the tick the One is grown, just never while
 * anybody is standing at the door with an account to spend.
 *
 * <p>So both halves of I-1 shipped as guards no green lock could falsify.
 * Everything below runs through {@code Simulation.tickOnce} — the census
 * lane, {@code RealWorld.doorTick}, {@code drainPetitions}, the string across
 * the bridge, the boolean back — and reads the answers out of the daemon's
 * own log.
 *
 * <h2>Three universes, because one would not be enough</h2>
 *
 * <table>
 * <tr><th>universe</th><th>pod half</th><th>slot half</th><th>the door must</th></tr>
 * <tr><td>{@code healthy}</td><td>free</td><td>above the floor</td>
 *     <td>grant, exactly {@code REINSERTION_QUOTA} times, then say <i>quota spent</i></td></tr>
 * <tr><td>{@code rack_full}</td><td><b>0 free</b></td><td>above the floor</td>
 *     <td>refuse, <i>no slot</i>, quota untouched</td></tr>
 * <tr><td>{@code starved}</td><td>free</td><td><b>at the floor</b></td>
 *     <td>refuse, <i>no slot</i>, quota untouched</td></tr>
 * </table>
 *
 * <p>The pressured universes break ONE half each and leave the other healthy,
 * so each refusal has exactly one possible author. A single universe with
 * both halves broken would stay green with either comparison deleted, which
 * is the defect this probe exists to end.
 *
 * <p>{@code healthy} is not decoration either — it is the half that catches an
 * INVERTED comparison. A bench that only ever demands a refusal is green on a
 * door that refuses everybody, and a door that refuses everybody is the same
 * bug read from the other side.
 *
 * <h2>How the pressure is built, and by whom</h2>
 *
 * Every move below is a call the daemon already makes; the probe chooses the
 * moment and the count, never the mechanism.
 *
 * <ol>
 * <li><b>{@code RealWorld.optOut(AMNESTY)}</b> — the treaty's own door, the
 *     one the root opens at PEACE. The count is an argument; {@code
 *     Config.OPTOUT_COUNT} frees six, the fleet's two boards seat six, and a
 *     six-citizen amnesty leaves nobody ashore to petition at all. {@code
 *     AMNESTY} is the smallest round number that keeps a crowd on the dock
 *     after both hulls are manned.
 * <li><b>{@code Simulation.commandSink()}, twice</b> — the operator's own
 *     order, the one {@code --sink-at} and {@code --sink-every} file. A lost
 *     hull kills its crew, and those deaths ARE the petition account: {@code
 *     doorTick} spikes every living account by {@code PETITION_GRIEF_SPIKE}
 *     per grief and by nothing else. Two losses put 144 on every survivor,
 *     and 143 is the highest breaking point any name can carry ({@code
 *     PETITION_BASE + PETITION_JITTER - 1}), so two is the smallest number of
 *     losses that opens the door for everyone ashore instead of for one lucky
 *     name. That is why this probe reaches the door in seven ticks where the
 *     canonical film does not reach it in six thousand.
 * <li><b>{@code RealWorld.grow()}</b>, for {@code rack_full} — the farm's own
 *     crop, the first statement of {@code Simulation.jackIn}. It is also the
 *     only way this universe can be built: every petitioner is a mind that
 *     vacated a rack unit on the way out, so the rack cannot be full while
 *     anyone is at the door unless something refilled it. The Architect's
 *     reload promises exactly that — "fresh crop inbound" — and nothing in
 *     the daemon delivers it: after boot the farm grows once more, ever, for
 *     the One (#927). The probe grows the crop the reload only talks about.
 * <li><b>{@code avatar.alive = false}</b>, for {@code starved} — what an Agent
 *     does, in bulk. The daemon does everything after: {@code RealWorld.tick}
 *     observes each death, flatlines the mind, flushes the rack unit, and
 *     {@code MachineSystem} recounts the budget at the top of the next tick.
 *     The probe stops when the budget stops selling above its floor, so the
 *     floor's arithmetic is never restated here — {@code SubstrateBudget} is
 *     driven to a state, and what is ASSERTED is the door's answer to it.
 * </ol>
 *
 * <p>Assertions are written out longhand rather than taken from {@code
 * DoorPolicy} — a probe that asks the code under test what it should have
 * said proves nothing (the {@code PodOptional} rule). Only the load-bearing
 * half of each sentence is pinned: a refusal must name the slot, a grant must
 * count the quota. Wording around that may move without breaking the lock.
 *
 * <p>The clause state is pinned across the WHOLE window, not sampled at the
 * answering tick: {@code free_pods} and {@code hot_slots} are read after
 * every tick and their min and max both reported, so "the rack was full when
 * the door answered" is a claim about every tick the door could have answered
 * on, and a mid-window drift shows up as a spread instead of hiding.
 *
 * Usage: java -cp out:probes/out DoorPressure [ticks] [seed]
 */
public final class DoorPressure {

    /**
     * Freed minds per pressured universe. Two hulls at {@code RIG_CAPACITY}
     * berths take six; the rest stay ashore, which is the only lane that may
     * petition (D-046 open point (a)).
     */
    private static final int AMNESTY = 24;

    /**
     * Ticks between the amnesty and the first loss. The root drains
     * liberations into the census AFTER the zion slot has already run, so a
     * sink order filed the same tick as the amnesty finds no hull afloat and
     * is refused; the city needs a few ticks to absorb its freed, lay its
     * hulls and man them before there is a crew to lose.
     */
    private static final int SETTLE = 5;

    /** Sink orders filed, at these offsets into the window — see the class note on 144 vs 143. */
    private static final int[] SINKS = {0, 3};

    /** Window ticks after the losses begin. Long enough for both losses and every answer, short enough that the rack holds still. */
    private static final int WINDOW = 14;

    private static int scenarios = 0;
    private static int anomalies = 0;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        film(seed, ticks);

        Bench healthy = pressure(seed, Farm.UNTOUCHED);
        line("HEALTHY", healthy.state(),
                fact("petitions>=3", healthy.petitions >= 3),
                fact("every_petition_answered", healthy.answers() == healthy.petitions),
                fact("pod_half_free", healthy.freeMin > 0),
                fact("slot_half_above_floor", healthy.slotsMin > Config.SLOTS_FLOOR),
                fact("granted==quota", healthy.grants.size() == Config.REINSERTION_QUOTA),
                fact("quota_counted_to_" + Config.REINSERTION_QUOTA,
                        healthy.lastGrant().contains("quota " + Config.REINSERTION_QUOTA
                                + "/" + Config.REINSERTION_QUOTA)),
                fact("refused_quota_spent", !healthy.quotaRefusals.isEmpty()),
                fact("never_refused_no_slot", healthy.slotRefusals.isEmpty()));
        say(healthy.lastGrant());
        say(healthy.first(healthy.quotaRefusals));

        Bench rackFull = pressure(seed, Farm.REGROWN);
        line("RACK_FULL", rackFull.state(),
                fact("petitions>=3", rackFull.petitions >= 3),
                fact("every_petition_answered", rackFull.answers() == rackFull.petitions),
                fact("pod_half_shut_all_window", rackFull.freeMin == 0 && rackFull.freeMax == 0),
                fact("slot_half_healthy", rackFull.slotsMin > Config.SLOTS_FLOOR),
                fact("refused_no_slot", !rackFull.slotRefusals.isEmpty()),
                fact("granted_nothing", rackFull.grants.isEmpty()),
                fact("treaty_unspent", rackFull.quotaRefusals.isEmpty()));
        say(rackFull.first(rackFull.slotRefusals));

        Bench starved = pressure(seed, Farm.CUT_TO_FLOOR);
        line("STARVED", starved.state(),
                fact("petitions>=3", starved.petitions >= 3),
                fact("every_petition_answered", starved.answers() == starved.petitions),
                fact("pod_half_healthy", starved.freeMin > 0),
                fact("slot_half_at_floor_all_window",
                        starved.slotsMin == Config.SLOTS_FLOOR && starved.slotsMax == Config.SLOTS_FLOOR),
                fact("refused_no_slot", !starved.slotRefusals.isEmpty()),
                fact("granted_nothing", starved.grants.isEmpty()),
                fact("treaty_unspent", starved.quotaRefusals.isEmpty()));
        say(starved.first(starved.slotRefusals));

        // I-1 itself, over all three universes at once (#442's own sentence):
        // grants never outrun the treaty, and no grant is ever issued by a
        // door whose substrate could not seat it.
        long granted = healthy.door.granted() + rackFull.door.granted() + starved.door.granted();
        line("I-1",
                "universes=3 granted=" + granted + " quota=" + Config.REINSERTION_QUOTA,
                fact("grants<=quota", healthy.door.granted() <= Config.REINSERTION_QUOTA
                        && rackFull.door.granted() <= Config.REINSERTION_QUOTA
                        && starved.door.granted() <= Config.REINSERTION_QUOTA),
                fact("no_grant_without_a_pod", rackFull.door.granted() == 0),
                fact("no_grant_without_a_slot", starved.door.granted() == 0));

        System.out.println("DOORPRESSURE scenarios=" + scenarios + " anomalies=" + anomalies);
        System.out.println(anomalies == 0
                ? "VERDICT DOOR_PRESSURE_HELD" : "VERDICT DOOR_PRESSURE_BROKEN");
    }

    /**
     * The premise, measured rather than asserted: what the canonical film does
     * to this door. A REPORT, not a verdict — when a later unit gives the arc
     * a reason to fill the rack, nothing here fails, and the numbers move where
     * a reader can see them.
     *
     * <p>{@code free_pods_min} is the whole-run minimum and it is 0: the rack
     * is full at boot and full again the tick the One is grown.
     * {@code free_pods_min_ashore} is the same minimum taken only over ticks
     * with somebody in the census lane — the state the door can actually be
     * asked in — and it is the number that made #886 an issue.
     */
    private static void film(long seed, long ticks) throws Exception {
        Simulation sim = new Simulation(seed, null, null);
        RealWorld rw = Probes.realWorld(sim);
        Zion zion = Probes.zion(sim);
        SubstrateBudget budget = Probes.substrate(sim);
        DoorPolicy door = Probes.doorPolicy(sim);
        int freeMin = Integer.MAX_VALUE;
        int freeMinAshore = Integer.MAX_VALUE;
        int slotsMin = Integer.MAX_VALUE;
        long ashoreTicks = 0;
        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            int free = Config.PODS_REFERENCE - rw.farm().occupiedCount();
            freeMin = Math.min(freeMin, free);
            slotsMin = Math.min(slotsMin, budget.hotSlots());
            if (!zion.ashore().isEmpty()) {
                ashoreTicks++;
                freeMinAshore = Math.min(freeMinAshore, free);
            }
        }
        System.out.println("DOORCLAIM seed=" + seed + " ticks=" + ticks
                + " petitions=" + rw.petitionCount()
                + " granted=" + door.granted() + " denied=" + door.denied()
                + " free_pods_min=" + freeMin
                + " ashore_ticks=" + ashoreTicks
                + " free_pods_min_ashore=" + (ashoreTicks == 0 ? -1 : freeMinAshore)
                + " hot_slots_min=" + slotsMin + " floor=" + Config.SLOTS_FLOOR);
    }

    /** What the probe does to the farm before the petitions land. */
    private enum Farm { UNTOUCHED, REGROWN, CUT_TO_FLOOR }

    /**
     * One universe driven to the door under a stated farm condition. The
     * amnesty and the losses are identical across all three; only the farm
     * differs, so the outcomes below differ for exactly one reason.
     */
    private static Bench pressure(long seed, Farm farm) throws Exception {
        Bench b = new Bench(seed);
        b.sim.tickOnce();
        int freed = b.rw.optOut(AMNESTY);
        if (freed != AMNESTY) {
            anomalies++;
            System.out.println("SETUP the amnesty freed " + freed + " of " + AMNESTY);
        }
        switch (farm) {
            case UNTOUCHED -> { }
            case REGROWN -> {
                int grown = 0;
                while (b.rw.farm().occupiedCount() < Config.PODS_REFERENCE && grown < 4 * AMNESTY) {
                    b.rw.grow();
                    grown++;
                }
            }
            case CUT_TO_FLOOR -> {
                int killed = 0;
                for (NeuralLink link : List.copyOf(Probes.links(b.rw))) {
                    if (b.budget.hotSlots() <= Config.SLOTS_FLOOR) {
                        break;
                    }
                    if (!link.closed() && link.avatar.alive) {
                        link.avatar.alive = false;
                        killed++;
                        b.sim.tickOnce();
                    }
                }
                if (b.budget.hotSlots() > Config.SLOTS_FLOOR) {
                    anomalies++;
                    System.out.println("SETUP " + killed + " deaths did not reach the floor: hot_slots="
                            + b.budget.hotSlots());
                }
            }
        }
        for (int t = 0; t < SETTLE; t++) {
            b.sim.tickOnce();
        }
        for (int t = 0; t < WINDOW; t++) {
            for (int at : SINKS) {
                if (at == t) {
                    b.sim.commandSink();
                }
            }
            b.sim.tickOnce();
            b.sample();
        }
        b.read();
        return b;
    }

    /** One private universe under pressure, plus everything its log said about the door. */
    private static final class Bench {
        final Simulation sim;
        final RealWorld rw;
        final SubstrateBudget budget;
        final DoorPolicy door;
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

        int freeMin = Integer.MAX_VALUE;
        int freeMax = Integer.MIN_VALUE;
        int slotsMin = Integer.MAX_VALUE;
        int slotsMax = Integer.MIN_VALUE;
        long petitions;
        final List<String> grants = new ArrayList<>();
        final List<String> quotaRefusals = new ArrayList<>();
        final List<String> slotRefusals = new ArrayList<>();

        Bench(long seed) throws Exception {
            sim = new Simulation(seed, sink, null);
            rw = Probes.realWorld(sim);
            budget = Probes.substrate(sim);
            door = Probes.doorPolicy(sim);
        }

        void sample() {
            int free = Config.PODS_REFERENCE - rw.farm().occupiedCount();
            freeMin = Math.min(freeMin, free);
            freeMax = Math.max(freeMax, free);
            slotsMin = Math.min(slotsMin, budget.hotSlots());
            slotsMax = Math.max(slotsMax, budget.hotSlots());
        }

        /** The door's three sentences, sorted out of the daemon's own stream. */
        void read() {
            petitions = rw.petitionCount();
            for (String l : sink.toString(StandardCharsets.UTF_8).split("\n")) {
                if (!l.contains("DOOR policy: ")) {
                    continue;
                }
                if (l.contains("no slot; the door is substrate-bounded")) {
                    slotRefusals.add(l.trim());
                } else if (l.contains("quota spent")) {
                    quotaRefusals.add(l.trim());
                } else if (l.contains("DOOR policy: granted ")) {
                    grants.add(l.trim());
                }
            }
        }

        int answers() {
            return grants.size() + quotaRefusals.size() + slotRefusals.size();
        }

        String lastGrant() {
            return grants.isEmpty() ? "" : grants.get(grants.size() - 1);
        }

        String first(List<String> lines) {
            return lines.isEmpty() ? "" : lines.get(0);
        }

        String state() {
            return "petitions=" + petitions
                    + " free_pods=" + freeMin + ".." + freeMax
                    + " hot_slots=" + slotsMin + ".." + slotsMax
                    + " floor=" + Config.SLOTS_FLOOR
                    + " granted=" + grants.size()
                    + " denied_quota=" + quotaRefusals.size()
                    + " denied_slot=" + slotRefusals.size();
        }
    }

    /** The daemon's own sentence, quoted — evidence a reader can read without the source open. */
    private static void say(String logLine) {
        System.out.println("  SAID " + (logLine.isEmpty() ? "<nothing>" : logLine));
    }

    /** A fact holds or it counts: prints name=held, tallies the anomaly when it does not. */
    private static String fact(String name, boolean held) {
        if (!held) {
            anomalies++;
        }
        return name + "=" + held;
    }

    private static void line(String prefix, String state, String... facts) {
        scenarios++;
        System.out.println(prefix + " " + state + " " + String.join(" ", facts));
    }

    private DoorPressure() {}
}
