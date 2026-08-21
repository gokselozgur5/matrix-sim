import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>THE TABLE RUN IS ONE-OFF: invoked by hand against a census table, it answers a
 * question once rather than guarding a property on every push, so a row that grew the
 * universes would cost the lane wall clock and buy nothing (#1162). That declared
 * absence still stands and is about the RUN. Since #816 the half that costs no
 * universe IS swept: see the selfcheck below.
 *
 * Probe: held-at-window-end is CENSORED, never a hold.
 *
 * The film's census gets away with a simple design because the film ENDS: every
 * universe either completes the arc inside its budget or does not. A standing
 * era has no terminal event — peace does not finish. A 20,000-tick corridor run
 * that ends with the treaty intact has not shown the corridor holds; it has
 * shown the corridor outlived the window. Counting those runs as successes is
 * the oldest way in statistics to overstate stability, and it will be committed
 * the first time somebody writes "corridors hold in 17 of 20 universes".
 *
 * This probe is the convention with a number behind it. It re-reads a census
 * table at a SHORTER hypothetical window and partitions the sample the honest
 * way — terminal events counted, survivors reported in their own column with
 * the window that bounds them — beside the naive count that folds survivors
 * into holds. The gap between the two columns is the overstatement, in points,
 * measured rather than argued.
 *
 * The film's own table is the worked example available today: at 6,000 ticks
 * `QUIET` contains universes whose cascade merely had not overflowed yet, and
 * `TREATY` is a censored observation by construction (peace reached, second
 * birth pending). Shrink the window and the "hold" fraction inflates on data
 * that never changed. That is the era census's whole exposure, demonstrated on
 * a multiverse that already exists.
 *
 * Usage:
 *   java -cp out:probes/out CensusCensor <atlas-table> <window> [<window>...]
 *
 * where <atlas-table> is `SeedAtlas` stdout and each <window> is a tick count
 * at or below the table's budget. Every reported hold carries its window; the
 * four columns partition the sample and are asserted to sum.
 *
 * Verdicts: PARTITION_SUMS (every window's columns account for every seed)
 * · PARTITION_BROKEN · WINDOW_REFUSED (a window longer than the run that fed
 * it, which is the same error one level up).
 */
public final class CensusCensor {

    /** One universe as the table recorded it. */
    private record Row(long seed, long birth, long overflow, long peace, long rebirth) {}

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            selfcheck();
            return;
        }
        if (args.length < 2) {
            System.out.println("usage: CensusCensor <atlas-table> <window> [<window>...]");
            // A refused invocation is not a held contract (#816). Returning 0
            // here let a typo in the lane's arguments read as a pass, which is
            // the same silence the verdict below used to leave.
            Probes.leave("VERDICT CENSOR_REFUSED reason=usage", Probes.Outcome.REFUSED);
        }
        List<Row> rows = new ArrayList<>();
        long budget = -1, from = -1, to = -1;
        for (String line : Files.readString(Path.of(args[0]), StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("SEED ")) {
                String[] parts = line.trim().split("\\s+");
                Map<String, String> kv = kv(parts);
                rows.add(new Row(Long.parseLong(parts[1]),
                        num(kv, "birth"), num(kv, "overflow"), num(kv, "peace"), num(kv, "rebirth")));
            } else if (line.startsWith("ATLAS ")) {
                Map<String, String> kv = kv(line.trim().split("\\s+"));
                budget = num(kv, "ticks");
                String seeds = kv.getOrDefault("seeds", "-1..-1");
                from = Long.parseLong(seeds.substring(0, seeds.indexOf("..")));
                to = Long.parseLong(seeds.substring(seeds.indexOf("..") + 2));
            }
        }
        System.out.println("SOURCE table=" + args[0] + " seeds=" + from + ".." + to
                + " budget=" + budget + " rows=" + rows.size());

        boolean sums = true;
        boolean refused = false;
        for (int i = 1; i < args.length; i++) {
            long window = Long.parseLong(args[i]);
            if (window > budget) {
                System.out.println("REFUSED window=" + window + " exceeds table budget=" + budget
                        + " - a window cannot claim past the run that fed it");
                refused = true;
                continue;
            }
            sums &= report(rows, window, budget);
        }
        // THE VERDICT LEAVES THROUGH THE EXIT CODE NOW (#816). It was a bare
        // println: the architecture note's laws 1 and 2 — every hold claim
        // carries its window, and the columns partition and sum — were enforced
        // by a program that printed PARTITION_BROKEN and exited 0, and which no
        // sweep ran anyway. Either half alone made the other pointless.
        //
        // `windows=` is the denominator. A run handed no window that survives
        // the budget check partitions nothing, and `sums` is vacuously true
        // there — the same zero a clean partition prints (#900, #1429).
        int windows = args.length - 1;
        if (refused) {
            Probes.leave("VERDICT WINDOW_REFUSED windows=" + windows,
                    Probes.Outcome.REFUSED);
        }
        Probes.leave((sums ? "VERDICT PARTITION_SUMS" : "VERDICT PARTITION_BROKEN")
                        + " windows=" + windows + " rows=" + rows.size(),
                sums && windows > 0 && !rows.isEmpty()
                        ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /**
     * Partition the sample at one window. The film's classes read as survival
     * data: overflow is the terminal event ("collapsed"), peace-without-rebirth
     * is the amended-but-unfinished state, and a universe still quiet at the
     * window end is CENSORED — it has outlived the window, which is all we know.
     */
    private static boolean report(List<Row> rows, long window, long budget) {
        int terminal = 0;          // overflow observed inside the window
        int amended = 0;           // peace inside the window (the arc turned over)
        int censored = 0;          // no overflow by the window end — survivor, not success
        int other = 0;             // no One born inside the window at all
        List<Long> laterOverflow = new ArrayList<>();

        for (Row r : rows) {
            // ONE COPY OF THE DECISION (#1512). The suite drives `classify` and
            // this loop counts what it returns, so a case cannot pass against a
            // second spelling of the rule that has drifted from this one.
            switch (classify(r, window)) {
                case "other" -> other++;
                case "amended" -> amended++;
                case "collapsed" -> terminal++;
                default -> {
                    censored++;
                    if (r.overflow() > window) {
                        laterOverflow.add(r.seed());
                    }
                }
            }
        }
        int n = rows.size();
        System.out.println("WINDOW ticks=" + window
                + " collapsed=" + terminal + " amended=" + amended
                + " intact_censored=" + censored + " other=" + other
                + " n=" + n + " window=" + window);

        // The lie, sized. Naive practice folds survivors into the hold count.
        double naive = (censored + amended) / (double) n;
        double honestLow = amended / (double) n;                       // survivors excluded
        System.out.println("HOLD naive=" + pct(naive)
                + " (censored folded in) honest_lower_bound=" + pct(honestLow)
                + " overstatement=" + pct(naive - honestLow) + " points"
                + " censored=" + censored + "/" + n + " window=" + window);

        if (!laterOverflow.isEmpty()) {
            List<Long> shown = laterOverflow.subList(0, Math.min(12, laterOverflow.size()));
            System.out.println("PROOF window=" + window + " seeds_that_collapse_after_the_window="
                    + laterOverflow.size() + " " + shown
                    + (laterOverflow.size() > shown.size() ? " ..." : "")
                    + " - each one a 'hold' at " + window + " and a collapse by " + budget);
        }
        // PARTITION_BROKEN IS UNREACHABLE AND THAT IS WORTH SAYING HERE (#816).
        // The four counters are incremented by a total if/else chain over the
        // same list `n` is the size of, so the sum equals `n` for every input
        // that parses. No table can make this false. It is kept because it is
        // the assertion the loop above is written against — delete the chain's
        // final `else` and this fires — but it must not be mistaken for a guard
        // that a census table can trip, which is the `ORDER_TABLE_TOO_WIDE`
        // shape this catalog already records: a guard nobody can trigger is a
        // guard nobody can falsify. What CAN be wrong is the CLASSIFICATION,
        // and `--selfcheck` is where that is judged.
        boolean ok = terminal + amended + censored + other == n;
        if (!ok) {
            System.out.println("BROKEN window=" + window + " columns do not partition the sample");
        }
        return ok;
    }

    /** One classification case: a row, a window, and the column it must land in. */
    private record Case(String name, Row row, long window, String want) {}

    /**
     * The reading's own cases (#816). No universe and no table on disk: every
     * case is a row this method constructs, so the suite judges the censoring
     * rule rather than the multiverse that happens to be pinned today.
     *
     * <p>This is the half of the probe a sweep can afford. #1162 declared the
     * by-hand table run one-off because a row would cost the lane wall clock
     * for a question answered once — that argument is about the RUN, and it
     * still holds; this costs no universe, so it does not inherit the
     * exemption. The distinction is the point: the classifier guards a property
     * on every push, the table run answers a question when somebody asks it.
     */
    private static void selfcheck() {
        // birth, overflow, peace, rebirth — -1 is "never, within the table's budget".
        List<Case> cases = List.of(
                new Case("collapsed-inside", new Row(1, 100, 500, -1, -1), 1000, "collapsed"),
                new Case("collapse-after-window", new Row(2, 100, 2000, -1, -1), 1000, "censored"),
                new Case("amended-inside", new Row(3, 100, 500, 600, -1), 1000, "amended"),
                new Case("peace-after-window", new Row(4, 100, -1, 2000, -1), 1000, "censored"),
                new Case("never-born", new Row(5, -1, -1, -1, -1), 1000, "other"),
                new Case("born-after-window", new Row(6, 2000, -1, -1, -1), 1000, "other"),
                new Case("still-quiet-at-window", new Row(7, 100, -1, -1, -1), 1000, "censored"),
                // The boundary is INCLUSIVE on both edges, and it is asserted
                // rather than left to a reader: `<= window` in three places.
                new Case("overflow-on-the-edge", new Row(8, 100, 1000, -1, -1), 1000, "collapsed"),
                new Case("birth-on-the-edge", new Row(9, 1000, -1, -1, -1), 1000, "censored"),
                // Peace outranks overflow when both land inside: the arc turned
                // over, so the universe is amended and not terminal.
                new Case("peace-outranks-overflow", new Row(10, 100, 400, 500, -1), 1000, "amended"));

        int failed = 0;
        for (Case c : cases) {
            String got = classify(c.row(), c.window());
            boolean ok = got.equals(c.want());
            if (!ok) {
                failed++;
            }
            System.out.println("CENSOR case=" + c.name()
                    + " want=" + c.want() + " got=" + got + (ok ? " OK" : " FAIL"));
        }

        // The overstatement is the number this probe exists to produce, so one
        // case drives it end to end rather than trusting the columns alone: two
        // survivors and one amended universe in a sample of four is a naive
        // hold of 75% against an honest lower bound of 25%.
        List<Row> sample = List.of(
                new Row(1, 100, 500, 600, -1),    // amended
                new Row(2, 100, 2000, -1, -1),    // censored, collapses later
                new Row(3, 100, -1, -1, -1),      // censored, still quiet
                new Row(4, 100, 500, -1, -1));    // collapsed
        boolean sums = report(sample, 1000, 6000);
        if (!sums) {
            failed++;
        }
        System.out.println("CENSOR case=overstatement-worked-example want=sums got="
                + (sums ? "sums" : "broken") + (sums ? " OK" : " FAIL"));

        int total = cases.size() + 1;
        Probes.leave("CENSOR SELFCHECK VERDICT CLASSIFIER_HOLDS cases=" + total
                        + " cases_none=" + (total == 0 ? 1 : 0)
                        + " failed=" + failed,
                failed == 0 && total > 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /**
     * The column one row lands in at one window. This is the ONLY copy of the
     * four-way decision: {@link #report} switches on what this returns rather
     * than re-deriving it, so a case here judges the rule the table run uses
     * and not a second spelling of it (#1512).
     */
    private static String classify(Row r, long window) {
        boolean bornInWindow = r.birth() >= 0 && r.birth() <= window;
        boolean overflowed = r.overflow() >= 0 && r.overflow() <= window;
        boolean atPeace = r.peace() >= 0 && r.peace() <= window;
        if (!bornInWindow) {
            return "other";
        } else if (atPeace) {
            return "amended";
        } else if (overflowed) {
            return "collapsed";
        }
        return "censored";
    }

    private static Map<String, String> kv(String[] tokens) {
        Map<String, String> kv = new LinkedHashMap<>();
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                kv.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }
        return kv;
    }

    private static long num(Map<String, String> kv, String key) {
        try {
            return Long.parseLong(kv.getOrDefault(key, "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String pct(double d) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", 100 * d);
    }

    private CensusCensor() {}
}
