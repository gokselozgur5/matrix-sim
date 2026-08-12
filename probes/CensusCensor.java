import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
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
        if (args.length < 2) {
            System.out.println("usage: CensusCensor <atlas-table> <window> [<window>...]");
            return;
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
        System.out.println(refused ? "VERDICT WINDOW_REFUSED"
                : sums ? "VERDICT PARTITION_SUMS" : "VERDICT PARTITION_BROKEN");
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
            boolean bornInWindow = r.birth() >= 0 && r.birth() <= window;
            boolean overflowed = r.overflow() >= 0 && r.overflow() <= window;
            boolean atPeace = r.peace() >= 0 && r.peace() <= window;
            if (!bornInWindow) {
                other++;
            } else if (atPeace) {
                amended++;
            } else if (overflowed) {
                terminal++;
            } else {
                censored++;
                if (r.overflow() > window) {
                    laterOverflow.add(r.seed());
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
        boolean ok = terminal + amended + censored + other == n;
        if (!ok) {
            System.out.println("BROKEN window=" + window + " columns do not partition the sample");
        }
        return ok;
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
