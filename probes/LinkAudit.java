import matrix.Simulation;
import matrix.realworld.NeuralLink;

/**
 * Probe: the end-state of every NeuralLink after N ticks — ghost triage.
 *
 * Walks BOTH books since D-032 split them: RealWorld's registry (the farm's
 * hardlines) and Zion's — every fleet rig's channel board, LOST hulls
 * included, since a board freezes rather than vanishes when its ship goes
 * down. Buckets each link by (closed × fate × presence). Closed wires key on
 * the HUMAN's fate, not the avatar's: severUnclean leaves the avatar object
 * untouched (the caller removes it; the MIND is what died), so the avatar
 * key would file a severed pirate under closed_clean. The link itself keeps
 * one bit (closed), not which of its three endings fired, so fate is the
 * honest observable — with one consequence, stated out loud: a pilot who
 * left a wire clean and died on a LATER wire re-keys the old hardline to
 * closed_dead (seed 7's Milo Marek: treaty exit, then flatlined
 * mid-broadcast). On a pre-fleet universe the keys agree exactly; the audit
 * reads fate, not etiquette. The bucket that
 * must stay EMPTY is open+absent+unworn: a ghost, the class of bug behind
 * the v3 ghost-HARDLINE finding; a SmithCopy wearing the avatar is canon,
 * not a ghost. The AUDIT line carries the union; AUDIT_ZION repeats the
 * zion-book share (already included in the totals), so a sink scenario's
 * severed wires are attributable at a glance.
 *
 * An optional third argument replays the #119 loss: sink_at >= 0 files the
 * sink order right before that tick, exactly like the daemon's --sink-at.
 * The probe still drives only its OWN private universe.
 *
 * Usage: java -cp out:probes/out LinkAudit [ticks] [seed] [sink_at]
 */
public final class LinkAudit {

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 2_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
        long sinkAt = args.length > 2 ? Long.parseLong(args[2]) : -1;

        Simulation sim = new Simulation(seed, null, null);
        for (long t = 1; t <= ticks; t++) {
            if (t == sinkAt) {
                sim.commandSink();
            }
            sim.tickOnce();
        }
        var world = Probes.world(sim);

        java.util.List<NeuralLink> book = new java.util.ArrayList<>(Probes.links(Probes.realWorld(sim)));
        int zionFrom = book.size();
        for (var ship : Probes.fleet(Probes.zion(sim))) {
            book.addAll(Probes.rigLinks(ship.rig()));
        }

        int[] all = new int[5];
        int[] zion = new int[5];
        for (int i = 0; i < book.size(); i++) {
            NeuralLink l = book.get(i);
            int bucket = classify(world, l);
            all[bucket]++;
            if (i >= zionFrom) {
                zion[bucket]++;
            }
        }
        System.out.println("AUDIT seed=" + seed + " ticks=" + ticks
                + (sinkAt >= 0 ? " sink_at=" + sinkAt : "")
                + " open_streaming=" + all[0]
                + " worn_by_smith=" + all[1]
                + " closed_clean=" + all[2]
                + " closed_dead=" + all[3]
                + " ghosts=" + all[4]);
        System.out.println("AUDIT_ZION open_streaming=" + zion[0]
                + " worn_by_smith=" + zion[1]
                + " closed_clean=" + zion[2]
                + " closed_dead=" + zion[3]
                + " ghosts=" + zion[4]);
        Probes.leave(all[4] == 0 ? "VERDICT CLEAN" : "VERDICT GHOSTS_FOUND", all[4] == 0);
    }

    /** 0 open_streaming · 1 worn_by_smith · 2 closed_clean · 3 closed_dead · 4 ghost. */
    private static int classify(matrix.core.World world, NeuralLink l) {
        if (l.closed()) {
            return l.human.alive() ? 2 : 3;
        }
        if (world.isPresent(l.avatar) && l.avatar.alive) {
            return 0;
        }
        if (isWorn(world, l)) {
            return 1;
        }
        System.out.println("GHOST pilot=" + l.human.name + " avatarId=" + l.avatar.id
                + " alive=" + l.avatar.alive + " present=" + world.isPresent(l.avatar));
        return 4;
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
