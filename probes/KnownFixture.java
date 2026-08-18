/**
 * Probe: a break that will never be fixed, so the verb for breaks stays proven.
 *
 * <p>{@code probes/bench.sh} has three verbs. {@code judge} and {@code run} are
 * exercised on every sweep by forty-odd rows. {@code known} — the verb for a
 * defect the tree has ACCEPTED and is watching — had zero rows and zero tests
 * (#1231). It worked once, for {@code LedgerMirror}'s broken seeds, and retired
 * when that row became a judge again.
 *
 * <p>That is the wrong verb to leave unproven. {@code known} is what the tree
 * reaches for at its worst moment: the defect is real, the fix is not ready,
 * and the choice is between deleting the row (losing the watch) and dropping
 * the exit code (losing the honesty). Its value is being correct on the day it
 * is needed, and that day is by definition a day nobody has time to debug the
 * bench.
 *
 * <h2>The inversion this fixture exists to prove</h2>
 *
 * A {@code known} row fails when its probe <b>passes</b>:
 *
 * <pre>if [ "$ROW_RC" -eq 0 ]; then
 *   FAIL … "the break $issue records is gone; make this a judge row"</pre>
 *
 * That is the difference between a lock and a mute button — a defect that
 * quietly heals must be noticed, not enjoyed — and it is the branch most likely
 * to break under a refactor of {@code execute}/{@code ROW_RC}, which happened
 * twice this week (#1093's honest exit codes, #1138's third code).
 *
 * <h2>What it does</h2>
 *
 * Nothing about the world. It prints one line and leaves with 1. There is no
 * seed, no tick, no {@code Simulation} — a fixture that touched the daemon
 * could break for a second reason and stop being a fixture.
 *
 * <p>{@code --heal} makes it exit 0 instead, which is how the inversion is
 * falsified: point the bench at a healed fixture and the sweep must go red with
 * "make this a judge row". The flag exists for that falsification and is not
 * used by any row.
 *
 * <p><b>Its issue is itself.</b> The row cites #1231, which closes when this
 * lands — so the row will cite a closed issue forever, and that is stated here
 * rather than hidden: this break is not waiting on a fix, it exists to be
 * broken. Every other {@code known} row must cite an issue that is genuinely
 * open, and a future check that reads issue states should exempt this one by
 * name and say why.
 *
 * <p>Usage: {@code java -cp out:probes/out KnownFixture [--heal]}
 */
public final class KnownFixture {

    public static void main(String[] args) {
        matrix.Streams.utf8();
        // The fifth door that swallowed an unknown flag (#1479), and it is the one that
        // would have hidden longest: this probe's PASS condition is a nonzero exit, so
        // `--heel` printed the by-design break and left 1 — indistinguishable from the
        // flag working, in the one probe whose whole job is to be broken on purpose.
        boolean healed = false;
        for (String arg : args) {
            if ("--heal".equals(arg)) {
                healed = true;
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }
        // LEAVE_BY_HAND (#1218): the reason below, in the word the checker reads.
        // Not Probes.leave: this probe's verdict word does not change with its
        // Not Probes.leave: this probe's verdict word does not change with its
        // exit code, and `leave` pairs the two. Here they are deliberately
        // separable, because the falsification is exactly "same line, other
        // code" — which is the state a real break reaching its fix arrives in.
        System.out.println("VERDICT KNOWN_FIXTURE_BROKEN by_design=yes issue=1231");
        System.exit(healed ? 0 : 1);
    }

    private KnownFixture() {}
}
