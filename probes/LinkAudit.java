import matrix.Simulation;
import matrix.realworld.NeuralLink;

/**
 * Probe: the end-state of every NeuralLink after N ticks — ghost triage.
 *
 * Buckets each link by (closed × avatar-alive × avatar-present). The bucket
 * that must stay EMPTY in a healthy universe is open+absent: an open link
 * whose avatar has left the world is either a wrapped mind (worn by Smith —
 * canon, it still strains the ledger) or a ghost (a removal path that forgot
 * to close the jack — the class of bug behind the v3 ghost-HARDLINE finding).
 * The probe separates the two by checking for a SmithCopy wearing the avatar.
 *
 * Usage: java -cp out:probes/out LinkAudit [ticks] [seed]
 */
public final class LinkAudit {

    public static void main(String[] args) throws Exception {
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 2_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Simulation sim = new Simulation(seed, null, null);
        sim.run(ticks);
        var world = Probes.world(sim);

        int openStreaming = 0, closedClean = 0, closedDead = 0, worn = 0, ghosts = 0;
        for (NeuralLink l : Probes.links(Probes.realWorld(sim))) {
            boolean present = world.isPresent(l.avatar);
            if (l.closed()) {
                if (l.avatar.alive) closedClean++; else closedDead++;
            } else if (present && l.avatar.alive) {
                openStreaming++;
            } else if (isWorn(world, l)) {
                worn++;
            } else {
                ghosts++;
                System.out.println("GHOST pilot=" + l.human.name + " avatarId=" + l.avatar.id
                        + " alive=" + l.avatar.alive + " present=" + present);
            }
        }
        System.out.println("AUDIT seed=" + seed + " ticks=" + ticks
                + " open_streaming=" + openStreaming
                + " worn_by_smith=" + worn
                + " closed_clean=" + closedClean
                + " closed_dead=" + closedDead
                + " ghosts=" + ghosts);
        System.out.println(ghosts == 0 ? "VERDICT CLEAN" : "VERDICT GHOSTS_FOUND");
    }

    private static boolean isWorn(matrix.core.World world, NeuralLink l) {
        for (var e : world.entities()) {
            if (e instanceof matrix.entities.SmithCopy c && c.original == l.avatar) {
                return true;
            }
        }
        return false;
    }

    private LinkAudit() {}
}
