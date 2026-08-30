import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Probe: how many verdict words can this tree print, and how many of them has
 * anything ever asked for? (#1741)
 *
 * <p>Two verdict branches are known to be unreachable from any input, and both
 * were found the same way — by a person reading the code. {@code
 * ORDER_TABLE_TOO_WIDE} was found only when its exit code turned out to have
 * been two units wrong for an unknown length of time, and nothing had noticed
 * because nothing could reach the line to notice. That is the cost: an
 * unreachable branch is the one place where a wrong exit code, a wrong verdict
 * word or a stale message survives indefinitely, because no falsifier can be
 * written for it and no sweep will ever print it.
 *
 * <p><b>This is a census and not a decider, and the difference is the whole
 * design.</b> Whether an input exists that makes a given arm run is undecidable
 * in general, and the issue that asked for this said plainly what a careless
 * reader would do with the answer: delete a branch for being unobserved.
 * Unobserved and unreachable are different claims. {@code DrawMeter}'s {@code
 * FREEZE_ABSENT} fires only on a QUIET seed and that case is real, so a reader
 * that conflates the two removes working guards. Nothing here is called dead.
 * What is printed is the candidate list, which is currently unknown even in
 * size.
 *
 * <p><b>Asked for</b> means named by a judged row in {@code probes/bench.sh} —
 * a {@code judge} or {@code known} row greps for a verdict line, and a word no
 * row greps for is a word the sweep has never demanded. Selfcheck rows count
 * the same way and for the same reason: they are rows, and they pin a word.
 *
 * <p><b>The reading is over stripped source, and the first one was not.</b> A
 * raw grep for {@code VERDICT [A-Z_]+} across {@code probes/*.java} answers
 * 127, and among its answers is {@code ALSO} — a word from a sentence in a
 * comment. Comments in this tree quote verdict lines constantly, because
 * explaining a verdict is what its javadoc is for, so the strip is not a
 * refinement of the count: it is the difference between a population and a
 * concordance.
 *
 * <p><b>The unpinned count is pinned.</b> It is a census, not a verdict on
 * anyone's work — {@code CatalogFlags} carries {@code undocumented=} the same
 * way. A number rather than a gate, because most of these words are correct to
 * keep: a break-verdict a compliant tree never prints is the assertion its loop
 * is written against. What the pin buys is that the next word to join them
 * arrives as a decision instead of as a default, which is the one thing the two
 * known cases never got.
 */
public final class VerdictCensus {

    /** A verdict word as it is printed: the line's first token after VERDICT. */
    private static final Pattern WORD = Pattern.compile("VERDICT ([A-Z][A-Z0-9_]*)");

    /**
     * A {@code "VERDICT …} literal, and whatever the source wrote after it.
     *
     * <p>THE WORD IS OFTEN NOT IN ONE LITERAL, and the first reading of this
     * probe did not know that. It matched only the completed shape and then
     * announced twenty-two words as pinned-but-unprintable, including
     * {@code CATALOG_FLAGS_COUNTED} — which {@code CatalogFlags} prints on
     * every green run, as {@code "VERDICT " + (held ? "CATALOG_FLAGS_COUNTED"
     * : "CATALOG_FLAGS_UNREAD")}. Twenty-two confident findings, all of them
     * the reader's own shape and none of them the tree's.
     *
     * <p>Three shapes are resolvable and are resolved: the completed literal,
     * a {@code "VERDICT "} joined to quoted alternatives, and a prefix like
     * {@code "VERDICT TRUTH_SNAPSHOT_"} joined to quoted suffixes. The fourth,
     * {@code String.format("VERDICT %s …", word)}, hands the word in from a
     * variable and is not decidable by reading; those sites are counted and
     * named rather than guessed at.
     */
    private static final Pattern LITERAL = Pattern.compile("\"VERDICT ([^\"]*)\"");

    /** A quoted all-caps token — a candidate completion for an unfinished word. */
    private static final Pattern QUOTED_WORD = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"");

    private static final String DEFAULT_ROOT = "probes";

    private static final String DEFAULT_BENCH = "probes/bench.sh";

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        String root = DEFAULT_ROOT;
        String bench = DEFAULT_BENCH;
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> root = value(args, ++i, "--root");
                case "--bench" -> bench = value(args, ++i, "--bench");
                case "--list" -> list = true;
                default -> {
                    System.err.println("unknown flag: " + args[i]);
                    System.exit(Probes.Outcome.REFUSED.code());
                }
            }
        }

        Set<String> pinned = new TreeSet<>();
        for (Probes.BenchRow row : Probes.benchRows(Path.of(bench))) {
            if (!row.judged()) {
                continue;
            }
            Matcher word = WORD.matcher(row.verdict());
            while (word.find()) {
                pinned.add(word.group(1));
            }
        }

        // Sorted by word, so the candidate list is a stable artefact between two
        // readings of one tree and a diff between two trees says what changed.
        Map<String, Set<String>> byWord = new TreeMap<>();
        Map<String, Integer> byProbe = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        List<Path> sources = sources(Path.of(root));
        for (Path source : sources) {
            String stripped = Probes.uncommented(source);
            String probe = source.getFileName().toString().replaceFirst("\\.java$", "");
            Set<String> here = new LinkedHashSet<>();
            Matcher literal = LITERAL.matcher(stripped);
            while (literal.find()) {
                String written = literal.group(1);
                // A LITERAL IS A PREFIX ONLY WHEN IT CANNOT HAVE ENDED, which is
                // two shapes and no others: it stops at `"VERDICT "`, or it stops
                // on an underscore. Any other literal carries its whole word, and
                // the second reading of this probe got that backwards — it treated
                // `"VERDICT SAME_TICK_ABSORB"` as a prefix, went looking for a
                // completion, and stitched the next quoted token onto it. Thirty
                // phantoms instead of twenty-two: a reader that widens after a
                // wrong answer without narrowing the rule widens the error too.
                if (!written.isEmpty() && !written.endsWith("_")) {
                    Matcher complete = WORD.matcher("VERDICT " + written);
                    if (complete.lookingAt()) {
                        here.add(complete.group(1));
                    } else {
                        // `"VERDICT %s breaks=%d"` — the word is handed in from a
                        // variable, so the literal names the shape and not the word.
                        unresolved.add(probe);
                    }
                    continue;
                }
                // The literal stops where the word does not: either at
                // `"VERDICT "` or at a prefix like `"VERDICT TRUTH_SNAPSHOT_"`.
                // The completions are the quoted all-caps tokens in the rest of
                // the statement.
                int stop = stripped.indexOf(';', literal.end());
                String rest = stripped.substring(literal.end(), stop < 0 ? stripped.length() : stop);
                Matcher suffix = QUOTED_WORD.matcher(rest);
                boolean joined = false;
                while (suffix.find()) {
                    here.add(written + suffix.group(1));
                    joined = true;
                }
                if (!joined) {
                    // `String.format("VERDICT %s …", word)` and its kin. NAMED,
                    // NOT GUESSED: a census that silently drops what it cannot
                    // read reports a population smaller than the tree's, and
                    // every number downstream of it inherits the error.
                    unresolved.add(probe);
                }
            }
            for (String found : here) {
                byWord.computeIfAbsent(found, key -> new TreeSet<>()).add(probe);
            }
            byProbe.put(probe, here.size());
        }

        List<String> unpinned = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : byWord.entrySet()) {
            if (!pinned.contains(entry.getKey())) {
                unpinned.add(entry.getKey());
                if (list) {
                    System.out.println("UNASKED " + entry.getKey() + " printed by "
                            + Probes.joined(new ArrayList<>(entry.getValue()))
                            + " — no judged row greps for it");
                }
            }
        }

        // A word a row greps for that no literal in the tree spells. It reads
        // like a finding and it is NOT ONE while `unresolved` is above zero: the
        // sites that hand their word in from a variable print words this reading
        // cannot name, and `SEAL_HYGIENE_HELD` — pinned, printed on every green
        // run, and unspellable here because `SealHygiene` formats it — is the
        // proof. Reported so the list exists, and judged by nothing, because a
        // number that would be a defect under one condition and noise under
        // another is not a gate. When the unresolved sites reach zero this
        // becomes decidable, and that is a later unit's argument to make.
        List<String> unmatched = new ArrayList<>();
        for (String word : pinned) {
            if (!byWord.containsKey(word)) {
                unmatched.add(word);
                if (list) {
                    System.out.println("UNMATCHED " + word
                            + " is pinned by a row and spelled by no literal this reading can resolve");
                }
            }
        }

        System.out.println("VERDICT_CENSUS sources=" + sources.size()
                + " words=" + byWord.size() + " pinned=" + pinned.size()
                + " root=" + root);

        // WHAT IS JUDGED IS THAT SOMETHING WAS READ, and nothing else. #1741
        // asked for the population to be measured and stated, and warned in the
        // same breath what a careless reader does with the answer: an unobserved
        // arm is not an unreachable one, and a gate here would invite exactly
        // that deletion. So the three numbers ride the verdict line and are
        // pinned by the bench row — `CatalogFlags` carries `undocumented=` the
        // same way — which makes the next word to join them arrive as a decision
        // instead of as a default. `read_none=` is the one real failure: a green
        // census over an empty tree is the shape #1207 and #970 both refuse.
        boolean held = !byWord.isEmpty();
        Probes.leave(String.format(
                "VERDICT VERDICT_CENSUS_STATED unasked=%d unresolved=%d unmatched=%d read_none=%d",
                unpinned.size(), unresolved.size(), unmatched.size(),
                byWord.isEmpty() ? 1 : 0), held);
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            System.err.println(flag + " takes a value and nothing followed it");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        return args[index];
    }

    /** Every probe source under the root, in one stable order. */
    private static List<Path> sources(Path root) throws IOException {
        try (Stream<Path> walk = Files.list(root)) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private VerdictCensus() {
    }
}
