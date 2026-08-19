import matrix.Simulation;
import matrix.core.World;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.realworld.Bond;
import matrix.realworld.Human;
import matrix.realworld.NeuralLink;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Probe: what happens to a mind AFTER the Room 303 clause stands it back
 * up (#1018).
 *
 * <p>#377 unwrites the death and stops there. It does not move the body:
 * {@code NeuralLink.observeDeath} sets {@code avatar.alive = true} and
 * returns, so the saved mind stands up on the coordinates it fell on —
 * inside the {@code CONTACT_RADIUS_CM} of the daemon that just killed it,
 * still wearing {@code Pill.RED} — and the same {@code Agent.tick} finds
 * the same prey next tick and rolls D-002's 90/10 again. That reading was
 * reconstructed from two adjacent log lines and from nothing else in the
 * repository. This probe is the number instead.
 *
 * <p><b>Identity, not names.</b> 196 minds wear 154 names at seed 42, so a
 * name match across 40,000 ticks proves nothing about who stood up. Every
 * firing is taken off the registry's own walk — the edge object whose
 * {@code firedAt()} just left -1 — and the mind it names is kept as the
 * {@link Human} REFERENCE and followed as one. The {@code saved=} field on
 * the line is decoration; {@code id=} beside it is the growth ordinal, the
 * only handle in this repository that names one mind and no other.
 *
 * <p><b>The four fates, and why the residual has a positive definition.</b>
 * A firing ends when the hunt resolves the mind it gave back:
 * <ul>
 * <li>{@code recaptured} — D-002's 90 branch landed: the avatar's pill is
 *     BLUE, the daemon plugged them back in;</li>
 * <li>{@code rekilled} — the 10 branch landed and this time D-013 wrote it:
 *     the brain is dark;</li>
 * <li>{@code resaved} — the 10 branch landed and a SECOND woven edge paid,
 *     so the death was unwritten again. Neither of the two above: nobody
 *     died and nobody was plugged back in, and reporting it as either would
 *     hide the most expensive tick the clause has. It is detected the only
 *     way it can be — a new firing naming the same mind object — because the
 *     kill, the second firing and the second unwriting all happen inside one
 *     {@code tickOnce} and leave the avatar exactly as they found it;</li>
 * <li>{@code uncaught} — the budget ran out first, with the mind STILL PREY:
 *     brain alive, the same wire open, the same body alive, red, and held by
 *     the world.</li>
 * </ul>
 * That last clause is the whole falsifiability of the verdict. A residual
 * defined as "everything else" makes {@code saved == sum} an identity and
 * the verdict a tautology. Defined as "still standing in the hunt", a firing
 * that leaves the hunt by some other door — walked out through D-033's, worn
 * by a Smith copy, re-jacked onto a new body — matches no fate this probe
 * knows, counts as {@code unaccounted}, and turns the row red until somebody
 * gives that door a name. The bench judges the accounting; it does not judge
 * that the accounting is happy news.
 *
 * <p><b>What it reads.</b> Seed 42 over 40,000 ticks: 18 firings, 16
 * recaptured, 2 resaved, and every single delay is ONE TICK. The two
 * {@code resaved} rows — Hugo Iglesias at 14916, Ivan Kaya at 29125 — are
 * the argument stated as arithmetic: the miracle bought one tick, and the
 * next tick spent a second edge. Seed 7 reaches the other branch with no
 * scripted pressure at all: 17 firings, 10 recaptured, 7 {@code rekilled},
 * median delay one tick again — 35 firings across two universes and not one
 * of them survives past the tick after the one it was given. The issue's own
 * body quotes a 13-firing run with a {@code rekilled} row at 18088; that
 * reading is from an older tree, and this one reproduces its two canonical
 * firings at 1850 and 5950 exactly and nothing after them.
 *
 * <p>Reads {@code Simulation.bonds} and the registry's public walk; ticks
 * its own quiet universe; prints. Usage:
 * {@code java -cp out:probes/out ClauseAftermath [ticks] [seed]}
 */
public final class ClauseAftermath {

    private static final String RECAPTURED = "recaptured";
    private static final String REKILLED = "rekilled";
    private static final String RESAVED = "resaved";
    private static final String UNCAUGHT = "uncaught";
    private static final String UNACCOUNTED = "unaccounted";

    /** One firing, and the fate of the mind it gave back. */
    private static final class Firing {
        final long tick;
        final Human mind;
        final NeuralLink wire;
        final Avatar body;
        String outcome;
        long delay = -1;

        Firing(long tick, Human mind, NeuralLink wire) {
            this.tick = tick;
            this.mind = mind;
            this.wire = wire;
            this.body = wire == null ? null : wire.avatar;
        }

        void settle(String outcome, long now) {
            this.outcome = outcome;
            this.delay = now - tick;
        }
    }

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 40_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        Field f = Simulation.class.getDeclaredField("bonds");
        f.setAccessible(true);
        Bond.Registry book = (Bond.Registry) f.get(sim);
        World world = Probes.world(sim);

        // Bond overrides neither equals nor hashCode, so this set is keyed by
        // identity, which is the point — and it is insertion-ordered even
        // though nothing walks it, because a probe whose output can move with
        // an identity hash between two runs of one seed is the defect
        // `bench.sh --twice` exists to catch (BondBook's #852 note). Firing
        // order, likewise, is the world's: every list below is appended to.
        Set<Bond> spent = new LinkedHashSet<>();
        List<Firing> firings = new ArrayList<>();
        List<Firing> open = new ArrayList<>();

        for (long t = 1; t <= ticks; t++) {
            sim.tickOnce();
            // The firing tick is the world's clock, not bond.firedAt(): the
            // registry is handed world.tick()+1 by the root's node loop, so
            // the field stands one ahead of the [tick] the FATE line a reader
            // greps was stamped with. Detection is same-tick — the walk below
            // runs inside the tickOnce that fired the edge — so sim.tick() IS
            // that stamp.
            long now = sim.tick();
            for (Bond edge : book.edges()) {
                if (edge.firedAt() < 0 || !spent.add(edge)) {
                    continue;
                }
                Human mind = edge.resurrected();
                // A second edge paying for the same MIND OBJECT is the only
                // visible trace of a re-death the clause unwrote again.
                settleOpen(open, mind, RESAVED, now);
                Firing firing = new Firing(now, mind, mind.link());
                firings.add(firing);
                open.add(firing);
            }
            for (int i = open.size() - 1; i >= 0; i--) {
                Firing firing = open.get(i);
                if (!firing.mind.alive()) {
                    firing.settle(REKILLED, now);
                } else if (firing.body != null && firing.body.pill == Pill.BLUE) {
                    firing.settle(RECAPTURED, now);
                } else {
                    continue;
                }
                open.remove(i);
            }
        }

        // The residual, with a positive definition: still prey when the
        // lights went out. Anything else left open is a door this probe
        // cannot name, and it says so rather than absorbing it.
        for (Firing firing : open) {
            firing.outcome = stillPrey(firing, world) ? UNCAUGHT : UNACCOUNTED;
        }

        int recaptured = 0;
        int rekilled = 0;
        int resaved = 0;
        int uncaught = 0;
        int unaccounted = 0;
        List<Long> delays = new ArrayList<>();
        for (Firing firing : firings) {
            System.out.println("AFTERMATH tick=" + firing.tick
                    + " saved=\"" + firing.mind.name + "\" id=" + firing.mind.id
                    + " outcome=" + firing.outcome + " delay=" + firing.delay);
            switch (firing.outcome) {
                case RECAPTURED -> recaptured++;
                case REKILLED -> rekilled++;
                case RESAVED -> resaved++;
                case UNCAUGHT -> uncaught++;
                default -> unaccounted++;
            }
            if (firing.delay >= 0) {
                delays.add(firing.delay);
            }
        }

        System.out.println("CLAUSE303 seed=" + seed + " ticks=" + ticks
                + " saved=" + firings.size()
                + " recaptured=" + recaptured
                + " rekilled=" + rekilled
                + " resaved=" + resaved
                + " uncaught=" + uncaught
                + " unaccounted=" + unaccounted
                + " median_delay=" + median(delays));

        if (firings.isEmpty()) {
            // Not a pass. A budget the clause never fires in has nothing to
            // account for, and saying ACCOUNTED there would let a row that
            // reaches no miracle stand in for one that does.
            Probes.leave("VERDICT NO_FIRING", Probes.Outcome.NEVER_AROSE);
        } else {
            // `unaccounted=` RIDES THE VERDICT SINCE #1655. It is the field the word
            // is chosen by and it sat on a census line; the outcome tally beside it —
            // `recaptured=`, `rekilled=`, `resaved=`, `uncaught=` — stays there,
            // because those are what the world DID with the saved and they move with
            // the seal (#1221). `median_delay=` stays for a different reason: it is a
            // STATISTIC, which is a category #1584's five clauses do not have.
            //
            // NO SECOND GUARD, and that is measured rather than assumed. #1655 worried
            // that the `firings.isEmpty()` refusal above guards the FIRINGS while the
            // accounting is over the SAVED, so a run that fired and saved nobody would
            // be empty in the way that matters and pass. It cannot: `saved=` IS
            // `firings.size()` (line 195), so the two populations are one and the
            // existing refusal covers both. The worry was right in shape and wrong
            // about this probe — the same way #1636's was about `SameTick`.
            Probes.leave("VERDICT "
                    + (unaccounted == 0 ? "AFTERMATH_ACCOUNTED" : "AFTERMATH_UNACCOUNTED")
                    + " unaccounted=" + unaccounted,
                    unaccounted == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);        }
    }

    /**
     * The open firing for this mind, settled. Identity, never the name: two
     * of the 196 could answer to the same string and only one of them stood
     * up. At most one firing per mind is ever open — a mind with an open
     * firing that dies again either resolves here or flatlines.
     */
    private static void settleOpen(List<Firing> open, Human mind, String outcome, long now) {
        for (int i = 0; i < open.size(); i++) {
            if (open.get(i).mind == mind) {
                open.get(i).settle(outcome, now);
                open.remove(i);
                return;
            }
        }
    }

    /**
     * Still what the clause left them: the same brain, the same wire, the
     * same body, alive, red, and held by the world. Every clause is an
     * identity comparison because every one of them can be true of somebody
     * else — a re-jacked mind wears a new avatar under the same name.
     */
    private static boolean stillPrey(Firing firing, World world) {
        NeuralLink wire = firing.mind.link();
        return firing.mind.alive()
                && wire != null && wire == firing.wire && !wire.closed()
                && firing.body != null && wire.avatar == firing.body
                && firing.body.alive && firing.body.pill == Pill.RED
                && world.isPresent(firing.body);
    }

    /** Lower median of the settled delays, or -1 when nothing settled. */
    private static long median(List<Long> delays) {
        if (delays.isEmpty()) {
            return -1;
        }
        List<Long> sorted = new ArrayList<>(delays);
        Collections.sort(sorted);
        return sorted.get((sorted.size() - 1) / 2);
    }

    private ClauseAftermath() {}
}
