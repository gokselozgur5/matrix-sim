import matrix.core.Position;
import matrix.entities.Avatar;
import matrix.entities.Pill;
import matrix.realworld.Brain;
import matrix.realworld.Human;
import matrix.realworld.LinkKind;
import matrix.realworld.NeuralLink;
import matrix.realworld.Pod;

/**
 * Probe: unit #110's DoD as a machine verdict — the wire's third ending.
 *
 * Own universe, smaller than a Simulation: three objects and a wire in the
 * bench vise, no shared state anywhere. Four scenarios: (1) severUnclean on
 * a PIRATE link flatlines the brain, closes the link, clears the jack, and
 * touches neither pod (there is none) nor avatar — the Remove is the
 * caller's job, and a dead wire must not execute the death rule a second
 * time; (2) observeDeath on a podless link runs the rule with nothing to
 * flush and no NPE; (3) observeDeath on a HARDLINE still flushes the pod —
 * the old rule, untouched; (4) severUnclean after closeClean is a no-op —
 * a mind that left clean cannot be killed by a wire it no longer wears.
 *
 * Usage: java -cp out:probes/out PirateSever
 */
public final class PirateSever {

    private static int anomalies = 0;

    public static void main(String[] args) {
        matrix.Streams.utf8();
        // S1 — "Not like this.": the wire dies first, and the mind follows.
        Human pirate = new Human("Switch", new Brain("Switch"), null);
        Avatar red1 = new Avatar(1, new Position(0, 0), "Switch", Pill.RED);
        NeuralLink wire1 = new NeuralLink(pirate, red1, LinkKind.PIRATE);
        wire1.severUnclean();
        boolean avatarUntouched = red1.alive;
        red1.alive = false;
        boolean reExecuted = wire1.observeDeath();
        line("SEVER pirate",
                fact("brain_dead", !pirate.alive()),
                fact("closed", wire1.closed()),
                fact("jack_cleared", pirate.link() == null),
                fact("avatar_untouched", avatarUntouched),
                fact("no_second_death", !reExecuted));

        // S2 — killed inside, no pod outside: the rule runs, nothing flushes.
        Human free = new Human("Mouse", new Brain("Mouse"), null);
        Avatar red2 = new Avatar(2, new Position(0, 0), "Mouse", Pill.RED);
        NeuralLink wire2 = new NeuralLink(free, red2, LinkKind.PIRATE);
        red2.alive = false;
        boolean observed;
        boolean npe = false;
        try {
            observed = wire2.observeDeath();
        } catch (NullPointerException e) {
            npe = true;
            observed = false;
        }
        line("DEATH pirate",
                fact("observed", observed),
                fact("brain_dead", !free.alive()),
                fact("jack_cleared", free.link() == null),
                fact("no_npe", !npe));

        // S3 — the farm's old rule, byte for byte: sleeper dies, pod flushes.
        Pod pod = new Pod("R01/U01");
        Human sleeper = new Human("Dana Frost", new Brain("Dana Frost"), pod);
        Avatar blue = new Avatar(3, new Position(0, 0), "Dana Frost", Pill.BLUE);
        NeuralLink wire3 = new NeuralLink(sleeper, blue, LinkKind.HARDLINE);
        blue.alive = false;
        line("DEATH hardline",
                fact("observed", wire3.observeDeath()),
                fact("brain_dead", !sleeper.alive()),
                fact("pod_flushed", !pod.occupied()));

        // S4 — walked out the door first: the sever finds nothing to kill.
        Human freed = new Human("Ezra Berg", new Brain("Ezra Berg"), null);
        Avatar red4 = new Avatar(4, new Position(0, 0), "Ezra Berg", Pill.RED);
        NeuralLink wire4 = new NeuralLink(freed, red4, LinkKind.PIRATE);
        wire4.closeClean();
        wire4.severUnclean();
        line("SEVER after_clean",
                fact("brain_alive", freed.alive()),
                fact("still_closed", wire4.closed()));

        System.out.println("PIRATESEVER scenarios=4 anomalies=" + anomalies);
        System.out.println(anomalies == 0 ? "VERDICT CONTRACT_HELD" : "VERDICT CONTRACT_BROKEN");
    }

    /** A fact holds or it counts: prints name=held, tallies the anomaly when it does not. */
    private static String fact(String name, boolean held) {
        if (!held) {
            anomalies++;
        }
        return name + "=" + held;
    }

    private static void line(String prefix, String... facts) {
        System.out.println(prefix + " " + String.join(" ", facts));
    }

    private PirateSever() {}
}
