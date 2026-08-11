import matrix.Simulation;
import matrix.realworld.NeuralLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Probe: how did one pilot's link state evolve, tick by tick?
 *
 * Prints a line whenever any matching link's observable state changes:
 * alive / present-in-world / closed / pill. This is the instrument that
 * solved the Nadia Petrov double-dark mystery — same avatar, worn by Smith
 * at 1717, freed by The One at 1846, worn again at 2477, while the event
 * log stayed silent (hijack logging is sampled).
 *
 * Usage: java -cp out:probes/out LinkTrace "Nadia Petrov" [ticks] [seed]
 */
public final class LinkTrace {

    public static void main(String[] args) throws Exception {
        String needle = args.length > 0 ? args[0] : "Nadia Petrov";
        long ticks = args.length > 1 ? Long.parseLong(args[1]) : 2_600;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        var world = Probes.world(sim);
        List<NeuralLink> matches = new ArrayList<>();
        for (NeuralLink l : Probes.links(Probes.realWorld(sim))) {
            if (l.human.name.contains(needle)) {
                matches.add(l);
            }
        }
        System.out.println("TRACE pilot=\"" + needle + "\" links=" + matches.size()
                + " seed=" + seed + " ticks=" + ticks);
        String[] last = new String[matches.size()];
        for (long t = 0; t < ticks; t++) {
            sim.tickOnce();
            for (int i = 0; i < matches.size(); i++) {
                NeuralLink l = matches.get(i);
                String s = "alive=" + l.avatar.alive
                        + " present=" + world.isPresent(l.avatar)
                        + " closed=" + l.closed()
                        + " avatarId=" + l.avatar.id
                        + " pill=" + l.avatar.pill;
                if (!s.equals(last[i])) {
                    System.out.println("t=" + t + " link#" + i + " " + s);
                    last[i] = s;
                }
            }
        }
    }

    private LinkTrace() {}
}
