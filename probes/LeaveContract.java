import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: does every judged probe reach its exit through {@code Probes.leave}?
 *
 * <p>{@code probes/README.md} states the contract plainly — a judged probe owes
 * a greppable verdict line AND an honest exit code, and {@code Probes.leave} is
 * the one place that owes both. #1093 established it after #1091 found
 * {@code DistrictNeutral} printing {@code DISTRICTS_TOUCHED_THE_STREAM} and
 * exiting 0. Nothing has ever checked that a probe obeys it.
 *
 * <p>Measured the day this landed: <b>eight judged probes had no
 * {@code System.exit} anywhere</b> — {@code BondScenario}, {@code CapSentinel},
 * {@code CensusSampleSize}, {@code DocLint}, {@code DoorPressure},
 * {@code LedgerMirror}, {@code PodOptional}, {@code SealHygiene}. Each printed
 * its failing verdict and fell off the end of {@code main}, which is exit 0.
 * `SealHygiene` would have printed {@code SEAL_HYGIENE_BROKEN} and left with
 * the code that means "the seal is clean". The bench still went red on the
 * verdict grep — but every hand-run invocation, every {@code $?} in a script,
 * and every future caller reading the code was told the contract held. That is
 * #1091 exactly, eight times, four months after it was supposedly fixed.
 *
 * <h2>The join</h2>
 *
 * The question is NOT "does every probe call the helper". A <b>reporting</b>
 * probe must not: a {@code run} row fails on a nonzero exit, so adopting an
 * exit code there changes what the row means. The bench table already knows
 * which rows are judged, so the rule is:
 *
 * <pre>a probe with a `judge` or `known` row in bench.sh must call Probes.leave</pre>
 *
 * and the table is read rather than a list being kept here. A list would be a
 * second copy of the bench's own contract, which is the shape #1192 spent a
 * unit deleting.
 *
 * <h2>What it cannot see</h2>
 *
 * A probe that calls {@code Probes.leave} on one branch and returns on another
 * satisfies this check and still lies on the second branch. This finds probes
 * that never route through the helper at all — the population that produced
 * every one of the eight — and says so in its verdict rather than implying more.
 *
 * <h2>The census is not the verdict</h2>
 *
 * {@code LEAVE_CENSUS} carries {@code judged=}, {@code reporting=} and
 * {@code no_source=}; the verdict line carries only {@code no_code=} and
 * {@code by_hand=}. The first draft put the population in the verdict, the
 * bench row pinned it by exact-line grep, and the row went red TWICE in one
 * afternoon for reasons unrelated to this check — once when its own row was
 * added, once when {@code SheetDump --catalog} landed. A pinned census in a
 * lane is a number people learn to edit, which is #884's lesson about the seal.
 *
 * <p>{@code judged=} does include this probe: the table is read as the table
 * and the row for this probe is a row. A check that excluded itself would
 * report a number nobody could reproduce by counting the file.
 *
 * <p>Usage: {@code java -cp out:probes/out LeaveContract [repo-root]}
 */
public final class LeaveContract {

    /** A row's verb and class: `  judge LedgerMirror 'LEDGER_ANOMALIES=0' 6000`. */
    private static final Pattern ROW =
            Pattern.compile("^\\s+(judge|known|run)\\s+(\\w+)\\b");

    /** The bench's own exemption grammar, read rather than re-listed. */
    private static final Pattern VARY = Pattern.compile("^\\s+vary\\b");

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        Path bench = root.resolve("probes/bench.sh");
        if (!Files.isReadable(bench)) {
            Probes.leave("VERDICT LEAVE_CONTRACT_NO_BENCH " + bench, false);
        }

        Set<String> judged = new LinkedHashSet<>();
        Set<String> reporting = new LinkedHashSet<>();
        for (String line : Files.readAllLines(bench, StandardCharsets.UTF_8)) {
            // A `vary` prefix declares a determinism exemption and is followed
            // by the real verb on the same or the next line; the verb is what
            // this reads, so the modifier is simply skipped.
            if (VARY.matcher(line).find()) {
                continue;
            }
            Matcher m = ROW.matcher(line);
            if (!m.find()) {
                continue;
            }
            (m.group(1).equals("run") ? reporting : judged).add(m.group(2));
        }

        List<String> noCode = new ArrayList<>();
        List<String> byHand = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String probe : judged) {
            Path src = root.resolve("probes/" + probe + ".java");
            if (!Files.isReadable(src)) {
                // A judged row for a class with no source is `roster_check`'s
                // question, not this one — but a check that walked past it
                // would report a clean join over a probe it never opened.
                missing.add(probe);
                continue;
            }
            String body = Files.readString(src, StandardCharsets.UTF_8);
            if (body.contains("Probes.leave")) {
                continue;
            }
            // Two different facts, and only one of them is a defect.
            //
            // A probe with NO exit call anywhere falls off the end of `main`,
            // which is exit 0 — so its failing verdict leaves with the code
            // that means "held". That is #1091's defect and it is red.
            //
            // A probe that calls `System.exit` itself is HONEST; it just does
            // not route through the shared helper. That is a shape worth
            // counting — one contract, one place to change it — but calling it
            // a defect would be calling a style a lie, and this probe's whole
            // subject is the difference.
            //
            // A REFUSAL EXIT IS NOT A VERDICT CODE (#1502), and reading it as one blinded
            // this check to the defect it exists for. `Outcome.code()`'s javadoc REQUIRES
            // a refusal to sit outside `leave` — *a refused invocation has nothing to print
            // in the bench's grammar, so it reaches System.exit directly* — so every probe
            // with an argument door carries one, and `contains("System.exit")` was satisfied
            // by it. Five probes printed a FAILING verdict and fell off the end of main at
            // exit 0 while being counted as a style preference: HullRoster's ROSTER_BROKEN,
            // FleetLines' FLEET_LINES_LIE, OrderTable's VACUOUS, AllocMeter's OVER_BUDGET,
            // UnparkStorm's UNBOUNDED.
            //
            // So the exits are counted with the refusal removed. An exit that is ONLY a
            // refusal leaves the verdict path with nothing, which is `no_code` — the red
            // case, and the one #1091 was opened for.
            (spendsBeyondRefusal(body) ? byHand : noCode).add(probe);
        }

        for (String probe : noCode) {
            System.out.println("NO_EXIT_CODE " + probe
                    + " judged=yes leave=no exit=no (a failing verdict leaves with 0)");
        }
        for (String probe : byHand) {
            System.out.println("EXITS_BY_HAND " + probe + " judged=yes leave=no exit=yes");
        }
        for (String probe : missing) {
            System.out.println("NO_SOURCE " + probe + " judged=yes");
        }

        // The population is its own line, and that is not cosmetic. The first
        // draft put `judged=` in the verdict, the bench row pinned it by
        // exact-line grep, and the row went red TWICE in one afternoon for
        // reasons that had nothing to do with this check: once when the row for
        // this probe was added, once when `SheetDump --catalog` landed. A pinned
        // census in a lane is a number people learn to edit, which is #884's
        // lesson about the seal. What the row must pin is the CONTRACT —
        // `no_code=0`, and `by_hand=` because a probe leaving the helper is a
        // decision — and the counts belong beside it, greppable and unpinned.
        System.out.println("LEAVE_CENSUS judged=" + judged.size()
                + " reporting=" + reporting.size()
                + " no_source=" + missing.size());

        boolean held = noCode.isEmpty() && missing.isEmpty();
        Probes.leave("VERDICT " + (held ? "EVERY_JUDGED_PROBE_HAS_A_CODE" : "A_JUDGED_PROBE_HAS_NO_CODE")
                + " no_code=" + noCode.size()
                + " by_hand=" + byHand.size(), held);
    }

    /**
     * Does this source leave with a code its VERDICT path can reach? (#1502)
     *
     * <p>Every `System.exit` in the file except the argument-refusal door. The refusal is
     * excluded because the grammar puts it there for an unrelated reason and it says
     * nothing about what a failing verdict leaves with — reading it as a verdict code is
     * what let five probes print `ROSTER_BROKEN` and friends at exit 0 while this check
     * called them stylistically different.
     *
     * <p>Textual, and it is honest about that: a probe that reached its refusal code
     * through a variable, or spelling the refusal as a bare digit, would be read as a
     * verdict exit. Neither exists here — {@code ExitGrammar} pins the codes and every
     * refusal in {@code probes/} is written `Probes.Outcome.REFUSED.code()` — and the
     * direction of the mistake is the safe one: it would count a refusal as a verdict code
     * and leave a probe in `by_hand` rather than reporting a defect that is not there.
     */
    private static boolean spendsBeyondRefusal(String body) {
        return body.replace("System.exit(Probes.Outcome.REFUSED.code())", "")
                .contains("System.exit");
    }

    private LeaveContract() {}
}
