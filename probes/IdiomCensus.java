import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reading: how many probes print an {@code anomalies=} counter, through the
 * same comment-stripped reader {@code CatalogFlags} already trusts
 * ({@code Probes.uncommented}, #1583/#1586) rather than a grep anchored to
 * one concatenation spelling.
 *
 * <p>#1641 measured this population with
 * {@code grep -l 'anomalies=" + anomalies' probes/*.java} and found five.
 * That grep matches only the string concatenation spelling of the idiom; a
 * probe building the identical printed shape with {@code String.format}, a
 * {@code StringBuilder}, or a text block is invisible to it. This reading
 * looks for the literal printed token instead of one way of constructing it,
 * so it is not blind to those forms (#1642).
 *
 * <p>Not a fix and not a sweep of what it finds — a census, so #1641's "five"
 * can be checked against a reading rather than trusted as one.
 */
public final class IdiomCensus {

    private static final Pattern ANOMALIES = Pattern.compile("anomalies=");

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        Path root = Path.of(args.length > 0 ? args[0] : "probes");
        List<String> carriers = new ArrayList<>();
        int scanned = 0;
        try (var files = Files.list(root)) {
            for (Path f : files.sorted().toList()) {
                String name = f.getFileName().toString();
                if (!name.endsWith(".java") || name.equals("IdiomCensus.java")) {
                    continue;
                }
                scanned++;
                if (printsAnomalies(f)) {
                    carriers.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        }
        int grepFloor = 5;
        // A census of nothing and a census that found nothing print the same
        // "carriers=0" — this line's own subject, so it needs the guard it
        // would otherwise be reporting on (#900, #970, #1207).
        int scannedNone = scanned == 0 ? 1 : 0;
        System.out.println("IDIOM_CENSUS carriers=" + carriers.size()
                + " grep_floor=" + grepFloor + " scanned_none=" + scannedNone
                + " at " + String.join(",", carriers));
        Probes.leave("IDIOM_CENSUS carriers=" + carriers.size() + " grep_floor=" + grepFloor
                + " scanned_none=" + scannedNone,
                scanned > 0 && carriers.size() >= grepFloor);
    }

    /** Does this source print {@code anomalies=} anywhere, comments stripped (#1586)? */
    private static boolean printsAnomalies(Path src) throws IOException {
        for (String line : Probes.uncommented(src).split("\n")) {
            int quote = line.indexOf('"');
            if (quote >= 0 && ANOMALIES.matcher(line.substring(quote)).find()) {
                return true;
            }
        }
        return false;
    }
}
