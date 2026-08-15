import matrix.Simulation;
import matrix.core.Config;
import matrix.realworld.Bond;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Probe: what holds the bond book's ceiling, and whether the book ever
 * lets go.
 *
 * <p>The registry's own line reports {@code edges} and {@code woven} at
 * boot, and the daemon's TRACE lines announce every mint and every
 * forgetting — so a reader can already see THAT the book fills. What
 * neither can answer is what the ceiling is MADE OF once it is reached,
 * and that turned out to be the whole question (#852). The issue assumed
 * squatters: pairs who drifted apart early and would never weave. They
 * are not. The {@code HOLD} line counts the book at the end of the run,
 * and at seed 42 every slot but the stragglers is WOVEN — exempt from
 * forgetting by law (#378) — so eviction of any shape can only ever
 * reach {@code evictable}, which is the number to read before proposing
 * one.
 *
 * <p>The {@code RETURN} and {@code STRAND} lines are the measurement
 * that picked the constant, and they are the two sides of one band. A
 * run apart is not a proxy for a pair drifting: the commute posts minds
 * to districts for {@code COMMUTE_SWITCH_TICKS} at a time, so pairs go
 * apart for whole postings and come back. {@code RETURN} reports, per
 * edge that reached WOVEN, the longest run it spent apart on the way —
 * how long the clock MUST be. {@code STRAND} reports the same for every
 * candidate still sitting in the book at the end — how long it MAY be.
 * {@code margin} is how close the clock came to eating a real bond.
 * Taken with the clock effectively off (the same probe, a
 * {@code BOND_FORGET_WINDOWS} of one million) seed 42 answers 208 and
 * 479, and {@code BOND_FORGET_WINDOWS} is 300 because of that gap — not
 * the obvious {@code BOND_WEAVE_WINDOWS} of 12, which lands below both.
 *
 * <p>The verdict is the issue's own done-when and it is not vacuous: it
 * separates a book that filled once and never moved again from one that
 * turns over. The base commit cannot run this probe — {@link
 * Bond#apart()} did not exist — but the same two numbers taken from its
 * log are {@code fill=1419 lastMint=1419 forgotten=0}, which is what
 * {@code BOOK_FOSSILIZED} names.
 *
 * <p>Reads {@code Simulation.bonds} and the registry's public walk; ticks
 * its own quiet universe; prints. Usage:
 * {@code java -cp out:probes/out BondBook [ticks] [seed]}
 */
public final class BondBook {

    /** Per edge: the longest run apart seen so far, the last window it was in the book, its fate. */
    private static final int MAX_APART = 0;
    private static final int LAST_SEEN = 1;
    private static final int FATE = 2;

    private static final int FATE_CANDIDATE = 0;
    private static final int FATE_WOVEN = 1;
    private static final int FATE_FORGOTTEN = 2;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        Field f = Simulation.class.getDeclaredField("bonds");
        f.setAccessible(true);
        Bond.Registry book = (Bond.Registry) f.get(sim);

        // Mint order, deliberately: a LinkedHashMap walks in insertion
        // order, and insertion follows the registry's own ordered walk, so
        // this probe's output cannot move with an identity hash the way a
        // HashMap's would. The bench's --twice pass is what noticed.
        Map<Bond, int[]> seen = new LinkedHashMap<>();
        long fillTick = -1;
        long lastMintTick = -1;
        int window = 0;
        int minted = 0;

        for (long t = 1; t <= ticks; t++) {
            sim.tickOnce();
            if (t % Config.ACCRUE_EVERY_TICKS != 0) {
                continue;
            }
            // The registry runs on world.tick()+1 and the log stamps the
            // line with world.tick(), so the accrual window whose node tick
            // is t prints as t-1. Report the log's clock, or every number
            // here is one off from the line a reader greps for.
            long stamp = sim.tick() - 1;
            window++;
            for (Bond edge : book.edges()) {
                int[] row = seen.get(edge);
                if (row == null) {
                    row = new int[] {0, window, FATE_CANDIDATE};
                    seen.put(edge, row);
                    minted++;
                    lastMintTick = stamp;
                }
                row[LAST_SEEN] = window;
                if (edge.state() == Bond.State.CANDIDATE) {
                    row[MAX_APART] = Math.max(row[MAX_APART], edge.apart());
                } else {
                    row[FATE] = FATE_WOVEN;
                }
            }
            for (int[] row : seen.values()) {
                if (row[FATE] == FATE_CANDIDATE && row[LAST_SEEN] < window) {
                    row[FATE] = FATE_FORGOTTEN;
                }
            }
            if (fillTick < 0 && book.size() >= Config.BOND_MAX_EDGES) {
                fillTick = stamp;
            }
        }

        int forgotten = 0;
        int wove = 0;
        int maxReturn = -1;
        int[] returns = new int[Config.BOND_FORGET_WINDOWS + 2];
        for (int[] row : seen.values()) {
            if (row[FATE] == FATE_FORGOTTEN) {
                forgotten++;
            } else if (row[FATE] == FATE_WOVEN) {
                wove++;
                int run = Math.min(row[MAX_APART], returns.length - 1);
                returns[run]++;
                maxReturn = Math.max(maxReturn, row[MAX_APART]);
            }
        }

        System.out.println("BOOK seed=" + seed + " ticks=" + ticks + " ceiling="
                + Config.BOND_MAX_EDGES + " fill=" + fillTick + " minted=" + minted
                + " forgotten=" + forgotten + " lastMint=" + lastMintTick);

        int candidate = 0;
        int woven = 0;
        int scar = 0;
        for (Bond edge : book.edges()) {
            switch (edge.state()) {
                case CANDIDATE -> candidate++;
                case WOVEN -> woven++;
                case SCAR -> scar++;
            }
        }
        System.out.println("HOLD edges=" + book.size() + " candidate=" + candidate
                + " woven=" + woven + " scar=" + scar
                + " evictable=" + candidate + "/" + book.size());

        for (int run = 0; run < returns.length; run++) {
            if (returns[run] > 0) {
                System.out.println("RETURN apartRun=" + run + " edges=" + returns[run]);
            }
        }
        System.out.println("RETURN wove=" + wove + " longest=" + maxReturn
                + " forgetAt=" + Config.BOND_FORGET_WINDOWS
                + " margin=" + (Config.BOND_FORGET_WINDOWS - maxReturn));

        // The band's other edge. A returner's longest run says how long the
        // clock must be; a straggler's says how long it may be, and the two
        // together are the only reason 300 is a number rather than a taste.
        // Printed in mint order — the book's own walk.
        for (Bond edge : book.edges()) {
            if (edge.state() == Bond.State.CANDIDATE) {
                System.out.println("STRAND apartRun=" + seen.get(edge)[MAX_APART]
                        + " windows=" + edge.windows() + " " + edge.pair());
            }
        }

        // The done-when, as a word. A book that filled and never moved
        // again is the symptom; a book that let an edge go AND minted
        // afterwards is the mechanism working.
        boolean turnsOver = forgotten > 0 && fillTick >= 0 && lastMintTick > fillTick;
        Probes.leave("VERDICT " + (turnsOver ? "BOOK_TURNS_OVER" : "BOOK_FOSSILIZED"), turnsOver);
    }
}
