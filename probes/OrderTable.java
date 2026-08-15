import matrix.Simulation;
import matrix.SystemNode;
import matrix.core.Digest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <p>ONE-OFF: run by hand, not in the bench. It answers a question once rather than
 * guarding a property on every push, so a row would cost the lane wall clock and buy
 * nothing (#1162). The rule is that the absence is DECLARED, not that it is unusual.
 *
 * Probe: the keeper the root door's draw-order table never had (#1013).
 *
 * <p>#830 split one welded sentence into two facts and gave one of them a
 * keeper. {@link SameTick} holds the same-tick half — a liberation queued in
 * tick T is absorbed inside T, and it is the drain's position in
 * {@code tickOnce} that buys it, never zion's slot. The other half is a TABLE:
 * six orders of the three {@code SystemNode}s, four distinct seals, and one
 * non-canonical order that reproduces the canonical seal byte for byte.
 * Nothing measured it. It was written by hand, from a run nobody repeats, into
 * a javadoc that had already carried two dead shas once (#830's own record).
 *
 * <p>The silent row is the whole reason this probe exists. Realworld and zion
 * commute: what the rng stream sees is each node's position relative to
 * MACHINE, never relative to each other. So {@code machine, zion, realworld}
 * is byte-identical to canonical — the free city can be taken off LAST, the
 * one move a reader of "zion LAST" is most likely to make, and no lock in this
 * repository noticed. The four orders that DO move are lawful and declared.
 * Both facts are lawful; the defect was that neither was measured.
 *
 * <p>What it judges, in three counters and nothing else:
 *
 * <ul>
 *   <li>{@code orders} — every permutation actually run. Derived from the node
 *       list found in the root by reflection, never from a hardcoded three, so
 *       a fourth {@code SystemNode} moves this number instead of being
 *       skipped by an instrument that never heard of it.</li>
 *   <li>{@code classes} — distinct final seals among those orders. A node that
 *       starts drawing splits a class; one that stops merges two.</li>
 *   <li>{@code silent} — non-canonical orders whose seal IS the canonical
 *       seal. This is the door's claim, stated as a number: exactly one at the
 *       judged row's seed and budget, and the day it is zero or two there the
 *       door is wrong and this row is red on the counter that moved.</li>
 * </ul>
 *
 * <p>How an order is run: one {@code Simulation}, built normally, with its
 * {@code nodes} list replaced by a permutation of itself before the first
 * tick. That write is the one non-read in the bench's reflection (the shared
 * {@link Probes} openers stay read-only, which is why it lives here) and it is
 * safe for exactly one reason — no node's CONSTRUCTOR draws, so building them
 * in canonical order and ticking them in another is the same universe as
 * building them in that order. The harness is neutral by construction and
 * says so out loud: the canonical row prints the sha the seal is pinned to,
 * so a reader can check the instrument against {@code .github/canonical-digest}
 * without trusting this sentence.
 *
 * <p>{@code first_diff} rides beside each row: the first DIGEST LINK that
 * differs from canonical, named by its tick, or {@code -} when the two chains
 * are identical link for link. Links land every {@code DIGEST_EVERY_TICKS}
 * ticks, so it is the link the worlds had parted BY and never the tick they
 * parted ON — the door's old paragraph quoted 4330 for a move this probe
 * reports as 4400 on the same tree, and both numbers are right about different
 * questions. It is context, not the verdict, and it is what makes a silent row
 * falsifiable at a glance: an order that diverged and reconverged by the final
 * tick prints {@code role=silent} with a tick number beside it, not a dash.
 *
 * <p>The three counters are a reading at ONE seed and ONE budget, and both
 * ride on the {@code ORDERS} line rather than being implied. Neither is
 * cosmetic: at 2,000 ticks the reading is {@code classes=2 silent=2}, because
 * moving the free city to the front has not diverged yet — its first differing
 * link at seed 42 is 4100 — and at seed 1, the QUIET universe where the ledger
 * never overflows, it is {@code classes=3 silent=2}, because a city that never
 * launches a sortie draws nothing and its slot costs nothing. Both are true
 * tables of smaller worlds. The judged row therefore writes its budget out and
 * takes the default seed, and the door's table says "at seed 42, 6,000 ticks"
 * for the same reason.
 *
 * <p>One stated blind spot, because the counters are the contract and they
 * measure the SHAPE of the table rather than which corner of it the root
 * stands in: committing the silent swap — {@code machine, zion, realworld} as
 * the canonical list — leaves all three counters where they are, moves no
 * seal, and passes. It is lawful, it is the door's own point, and this probe
 * reports it rather than judging it: the {@code ORDERS} header names the order
 * it FOUND, so a reader who cares which one is canonical reads it there.
 *
 * <p>A vacuous pass is a failure here, the same way it is in {@link SameTick}.
 * At #187's merge {@code Zion.tick} was an empty method and all six orders
 * were identical; a table measured in that world says nothing about draw order
 * and would have licensed the sentence that turned out to be a generalisation
 * from one permutation. {@code classes=1} over more than one order is
 * {@code ORDER_TABLE_VACUOUS}, not a pass.
 *
 * <p>Judged in {@code probes/bench.sh} — one row, exact-line grep on the
 * verdict with its three counters, which is what makes this a lock rather
 * than a table somebody remembers to re-measure.
 *
 * <p>One command:
 *   {@code java -cp out:probes/out OrderTable 6000}
 *
 * <p>Usage: {@code java -cp out:probes/out OrderTable [ticks] [seed]}
 */
public final class OrderTable {

    /**
     * Above this the sweep stops being a sweep: orders grow as n!, and each
     * order is a whole run. Five nodes is 120 runs — about two minutes at
     * this budget on the box that wrote this line; six nodes is 720.
     * The probe refuses rather than sampling, and the refusal is loud (exit 2,
     * red row) — a silent subset is the exact failure this probe exists
     * against, one level up.
     */
    private static final int MAX_NODES = 4;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        // The roster, read off a booted root rather than assumed: the node
        // list is the one thing this probe must not carry as a constant.
        List<String> roster = tags(nodes(new Simulation(seed, null, null)));
        int n = roster.size();
        if (n > MAX_NODES) {
            System.out.println("FATAL ORDER_TABLE_TOO_WIDE nodes=" + n
                    + " orders=" + factorial(n) + " max_nodes=" + MAX_NODES);
            System.exit(2);
        }

        System.out.println("ORDERS seed=" + seed + " ticks=" + ticks
                + " nodes=" + n + " canonical=" + String.join(",", roster));

        List<Digest> canonChain = null;
        String canonSha = null;
        // Seal -> class number, in first-seen order: the canonical run is
        // first, so class 1 is always canonical's.
        Map<String, Integer> classes = new LinkedHashMap<>();
        int orders = 0;
        int silent = 0;

        for (int[] order : permutations(n)) {
            List<Digest> chain = run(seed, ticks, order);
            String sha = chain.get(chain.size() - 1).sha256();
            orders++;
            boolean canonical = orders == 1;
            if (canonical) {
                canonChain = chain;
                canonSha = sha;
            }
            classes.putIfAbsent(sha, classes.size() + 1);
            int cls = classes.get(sha);
            boolean quiet = !canonical && sha.equals(canonSha);
            if (quiet) {
                silent++;
            }
            System.out.println("ORDER perm=" + permName(roster, order)
                    + " class=" + cls
                    + " role=" + (canonical ? "canonical" : quiet ? "silent" : "break")
                    + " first_diff=" + (canonical ? "-" : firstDiff(canonChain, chain))
                    + " sha=" + sha);
        }

        System.out.println(orders > 1 && classes.size() == 1
                ? "VERDICT ORDER_TABLE_VACUOUS orders=" + orders
                        + " classes=" + classes.size() + " silent=" + silent
                : "VERDICT ORDER_TABLE_HELD orders=" + orders
                        + " classes=" + classes.size() + " silent=" + silent);
    }

    /** One universe, ticked with its node list permuted before the first tick. */
    private static List<Digest> run(long seed, long ticks, int[] order)
            throws ReflectiveOperationException {
        Simulation sim = new Simulation(seed, null, null);
        List<SystemNode> canonical = nodes(sim);
        List<SystemNode> permuted = new ArrayList<>(order.length);
        for (int i : order) {
            permuted.add(canonical.get(i));
        }
        // The one write in the bench's reflection, and it lands before the
        // first tick: same objects, same universe, different tick order.
        nodeList().set(sim, List.copyOf(permuted));
        return sim.run(ticks);
    }

    @SuppressWarnings("unchecked")
    private static List<SystemNode> nodes(Simulation sim) throws ReflectiveOperationException {
        return (List<SystemNode>) nodeList().get(sim);
    }

    private static Field nodeList() throws NoSuchFieldException {
        Field f = Simulation.class.getDeclaredField("nodes");
        f.setAccessible(true);
        return f;
    }

    /**
     * The word the root door uses for a node, taken off its class: the machine
     * node's own {@code name()} is "matrix", and the table this probe keeps is
     * written in machine/realworld/zion. Locale-pinned, because the bench's
     * second run stands in a hostile one.
     */
    private static List<String> tags(List<SystemNode> nodes) {
        List<String> out = new ArrayList<>(nodes.size());
        for (SystemNode node : nodes) {
            String cls = node.getClass().getSimpleName();
            if (cls.endsWith("System")) {
                cls = cls.substring(0, cls.length() - "System".length());
            }
            out.add(cls.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String permName(List<String> roster, int[] order) {
        StringBuilder sb = new StringBuilder();
        for (int i : order) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(roster.get(i));
        }
        return sb.toString();
    }

    /** The first differing link, named by its tick, or "-" when the chains agree link for link. */
    private static String firstDiff(List<Digest> canonical, List<Digest> other) {
        int links = Math.min(canonical.size(), other.size());
        for (int i = 0; i < links; i++) {
            if (!canonical.get(i).equals(other.get(i))) {
                return String.valueOf(canonical.get(i).tick());
            }
        }
        return canonical.size() == other.size() ? "-" : "length";
    }

    /** Every permutation of [0,n), lexicographic — canonical (the identity) first. */
    private static List<int[]> permutations(int n) {
        List<int[]> out = new ArrayList<>();
        int[] pick = new int[n];
        boolean[] used = new boolean[n];
        walk(0, pick, used, out);
        return out;
    }

    private static void walk(int depth, int[] pick, boolean[] used, List<int[]> out) {
        if (depth == pick.length) {
            out.add(pick.clone());
            return;
        }
        for (int i = 0; i < pick.length; i++) {
            if (!used[i]) {
                used[i] = true;
                pick[depth] = i;
                walk(depth + 1, pick, used, out);
                used[i] = false;
            }
        }
    }

    private static long factorial(int n) {
        long f = 1;
        for (int i = 2; i <= n; i++) {
            f *= i;
        }
        return f;
    }

    private OrderTable() {}
}
