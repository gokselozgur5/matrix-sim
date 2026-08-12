import matrix.Simulation;
import matrix.realworld.NeuralLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Probe: how did one pilot's link state evolve, tick by tick?
 *
 * Prints a line whenever any matching link's observable state changes:
 * alive / present-in-world / closed / pill.
 *
 * <p><b>The case it solved, as of the {@code v3.0.0} tag.</b> This is the
 * instrument that cracked the Nadia Petrov double-dark mystery — same avatar,
 * worn by Smith at 1717, freed by The One at 1846, worn again at 2477, while
 * the event log stayed silent (hijack logging is sampled). Every tick in that
 * sentence belongs to the sealed Season One universe and reproduces only there:
 *
 * <pre>
 *   git archive v3.0.0 | tar -x -C "$(mktemp -d)"   # then build and run inside it
 *   java -cp out:probes/out LinkTrace "Nadia Petrov" 6000 42
 * </pre>
 *
 * <b>On current {@code main} it does not.</b> Season Two's mechanics moved the
 * film; her link never changes across the whole arc, and the event log holds
 * nothing under her name. That is the same pin the two documents carrying this
 * case already wear (`docs/ARCHITECTURE.md`'s field manual, `probes/README.md`'s
 * catalog row) — the instrument is era-free, the case study is not.
 *
 * <p><b>A still link is a verdict, not a fault.</b> The run closes with
 * {@code VERDICT STILL_LINK} or {@code VERDICT TRACE_MOVED} and, on a still
 * trace, with how many OTHER links moved in the same window — so a reader can
 * tell "this pilot had no story in this era" from "the probe is broken" without
 * knowing the era in advance. That distinction is the whole reason the closing
 * lines exist.
 *
 * <p>The tick default is the repo's standard full-arc budget (6,000 — what CI,
 * the census and the bench all use), deliberately NOT a window cut to fit one
 * era's flips: a default tuned to a case rots the day the case does.
 *
 * Usage: java -cp out:probes/out LinkTrace ["needle"] [ticks] [seed]
 */
public final class LinkTrace {

    public static void main(String[] args) throws Exception {
        String needle = args.length > 0 ? args[0] : "Nadia Petrov";
        // WHY 6_000: the old default was 2_600, chosen to bracket the v3.0.0 flip
        // window (1717/1846/2477). That window is dead on today's main, so the bare
        // run printed one line and read as a malfunction. 6,000 is era-free — it is
        // the full-arc budget every other instrument in this repo already runs at.
        long ticks = args.length > 1 ? Long.parseLong(args[1]) : 6_000;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        // Snapshot: the farm's book is live and grows as the world jacks people in.
        List<NeuralLink> all = new ArrayList<>(Probes.links(Probes.realWorld(sim)));
        List<NeuralLink> matches = new ArrayList<>();
        for (NeuralLink l : all) {
            if (l.human.name.contains(needle)) {
                matches.add(l);
            }
        }
        System.out.println("TRACE pilot=\"" + needle + "\" links=" + matches.size()
                + " seed=" + seed + " ticks=" + ticks);

        String[] last = new String[matches.size()];
        long changes = 0;
        // The control group: every link on the farm's book AT BOOT, tracked with the
        // same tuple but never printed. It costs one string per link per tick and it
        // buys the only sentence that separates a still pilot from a broken
        // instrument. Links opened later are outside the group by construction — the
        // denominator is stated on the WORLD line so nobody has to guess which.
        String[] lastAll = new String[all.size()];
        boolean[] movedAll = new boolean[all.size()];

        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            for (int i = 0; i < matches.size(); i++) {
                String s = state(world, matches.get(i));
                if (!s.equals(last[i])) {
                    System.out.println("t=" + t + " link#" + i + " " + s);
                    if (last[i] != null) {
                        changes++;      // t=0 is the baseline, not a change
                    }
                    last[i] = s;
                }
            }
            for (int i = 0; i < all.size(); i++) {
                String s = state(world, all.get(i));
                if (!s.equals(lastAll[i])) {
                    if (lastAll[i] != null) {
                        movedAll[i] = true;
                    }
                    lastAll[i] = s;
                }
            }
        }

        int moved = 0;
        for (boolean m : movedAll) {
            if (m) {
                moved++;
            }
        }
        System.out.println("CHANGES pilot=\"" + needle + "\" links=" + matches.size()
                + " after_baseline=" + changes + " window=0.." + ticks);
        System.out.println("WORLD links=" + all.size() + " links_that_moved=" + moved
                + " in the same window");
        if (matches.isEmpty()) {
            System.out.println("VERDICT NO_SUCH_PILOT - no link's human name contains \""
                    + needle + "\" in this universe; try a substring, or LinkAudit for the roster");
        } else if (changes == 0) {
            System.out.println("VERDICT STILL_LINK - " + moved + " of " + all.size()
                    + " links moved in this window and this pilot's did not:"
                    + " a trace with no state changes is a real answer about this era,"
                    + " not a broken probe (the v3.0.0 case in the javadoc is pinned"
                    + " for exactly this reason)");
        } else {
            System.out.println("VERDICT TRACE_MOVED changes=" + changes);
        }
    }

    private static String state(matrix.core.World world, NeuralLink l) {
        return "alive=" + l.avatar.alive
                + " present=" + world.isPresent(l.avatar)
                + " closed=" + l.closed()
                + " avatarId=" + l.avatar.id
                + " pill=" + l.avatar.pill;
    }

    private LinkTrace() {}
}
