import matrix.Simulation;
import matrix.character.Family;
import matrix.character.Sheet;
import matrix.character.SheetDoor;
import matrix.core.World;

/**
 * Probe: does the Matrix's fatigue axis read the counter, or invent a
 * number? (#661)
 *
 * <p>Every other value on every sheet in this tree is a pure function of a
 * name. The SYSTEM row's {@code versionFatigue} is the single exception,
 * and before #661 it was not one: the mixer folded {@code "the Matrix"}
 * and produced 1, so the census reported a fresh install of a world on its
 * sixth version. The number was stable, reproducible, byte-defined and
 * wrong — which is the failure mode a derivation cannot see, because a
 * derived value is correct by construction about the string it was handed
 * and says nothing about the world.
 *
 * <p>Three claims, judged here rather than by a reader trusting a diff:
 *
 * <ul>
 *   <li><b>The boot value is the counter.</b> Six lived versions read 6, at
 *       a universe this probe builds itself.</li>
 *   <li><b>A reboot moves it on the same page.</b> Nothing is cached
 *       between the counter and the sheet, so bumping the world's version
 *       and asking again reads 7 — the sheets-cached-nowhere law (#656)
 *       having an observable consequence rather than only a grep.</li>
 *   <li><b>The band saturates.</b> A version counter is unbounded and a
 *       stat is 1..10, so v99 reads 10 and v0 reads 1. This is the claim
 *       most worth pinning: {@code Math.floorMod} — the banding every OTHER
 *       axis uses — would make v11 read 1, and a system on its eleventh
 *       reload reported as brand new is the one answer that is definitely
 *       wrong.</li>
 * </ul>
 *
 * <h2>The mutation, declared</h2>
 *
 * This probe calls {@code world.bumpVersion()}, which makes it the first
 * probe in this tree to mutate anything. It stands on clause 1's carve-out
 * and says so here, as that clause requires: the universe is one this probe
 * built itself (clause 2), nothing is queued — the counter is one the digest
 * already reads — and the mutation IS the measurement. The claim under test
 * is that the fatigue axis moves when the world reloads, and there is no way
 * to ask that of a world that never reloads.
 *
 * <p>The carve-out was written down in #1261 because this probe was inside
 * the LETTER of a clause composed when no probe mutated anything at all, and
 * a rule people follow that is not the rule on the page is how the next
 * probe arrives with a weaker excuse.
 */
public final class SystemFatigue {

    /** The name the census mints the SYSTEM row under; identity is bytes. */
    private static final String THE_MATRIX = "the Matrix";

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();

        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        Simulation sim = new Simulation(seed, null, null);
        World world = Probes.world(sim);

        int counter = world.version();
        int boot = fatigue(SheetDoor.system(THE_MATRIX, counter));
        System.out.println("FATIGUE boot counter=" + counter + " axis=" + boot);

        world.bumpVersion();
        int rebooted = world.version();
        int after = fatigue(SheetDoor.system(THE_MATRIX, rebooted));
        System.out.println("FATIGUE reboot counter=" + rebooted + " axis=" + after);

        int saturated = fatigue(SheetDoor.system(THE_MATRIX, 99));
        int floor = fatigue(SheetDoor.system(THE_MATRIX, 0));
        System.out.println("FATIGUE band v99=" + saturated + " v0=" + floor);

        // The other three axes must NOT have moved: this unit reads one axis
        // and derives the rest, and a reboot that shifted `authority` would
        // mean the override had reached past its own index.
        Sheet before = SheetDoor.system(THE_MATRIX, counter);
        Sheet plain = SheetDoor.at(THE_MATRIX, Family.SYSTEM);
        boolean derivedIntact = before.stat("stability") == plain.stat("stability")
                && before.stat("tolerance") == plain.stat("tolerance")
                && before.stat("authority") == plain.stat("authority");
        System.out.println("FATIGUE derived_axes intact=" + derivedIntact
                + " (stability=" + plain.stat("stability")
                + " tolerance=" + plain.stat("tolerance")
                + " authority=" + plain.stat("authority") + ")");

        boolean held = boot == counter
                && after == rebooted
                && after == boot + 1
                && saturated == 10
                && floor == 1
                && derivedIntact;

        Probes.leave(String.format(
                "VERDICT FATIGUE_READS_THE_COUNTER boot=%d reboot=%d v99=%d v0=%d derived_intact=%s",
                boot, after, saturated, floor, derivedIntact),
                held ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    private static int fatigue(Sheet sheet) {
        return sheet.stat(Family.FATIGUE_AXIS);
    }

    private SystemFatigue() {}
}
