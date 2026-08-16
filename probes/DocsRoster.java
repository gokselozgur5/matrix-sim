import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Probe: does every document in {@code docs/} get named by another one?
 * (#1190)
 *
 * <p>Three catalogs got a reader in one day, each after the same gap was
 * found by hand: the bench table (#1162 — {@code BoundsCensus}, a probe on
 * nobody's push), {@code probes/README.md} (#1177 — five probes with a bench
 * row and no catalog row), {@code tools/README.md} (#1189 — two tools in the
 * lane and in no document). {@code docs/} had no such reader. {@code DocLint}
 * asks seven questions of the canon and not one of them is *is this file
 * discoverable at all*.
 *
 * <p>The answer today is that all sixty-four are named, which is the same
 * answer {@code SheetFence} got on its first run and means the same thing: a
 * directory is tidy the way an empty room is tidy. D-058 made
 * {@code docs/spec/} a sanctioned document kind, and a sanctioned kind that
 * nothing links to is precisely the shape that grows an orphan — the shelf's
 * own README says the first spec lands with the probe that reads it, and
 * nothing yet says the spec itself will be findable.
 *
 * <h2>Named, not linked</h2>
 *
 * The question is whether some OTHER document mentions the file's name, not
 * whether an index links to it. This tree has no single index for
 * {@code docs/} and inventing one is a decision, not a probe's business
 * (#1190 says so). Naming is falsifiable, cheap, and catches the orphan —
 * the file nobody can reach except by {@code ls}.
 *
 * <p>A file naming ITSELF does not count, which is not a detail: every ADR
 * record carries its own D-number in its front matter, so a self-match would
 * make every record its own reference and the count would be 0 forever, by
 * construction rather than by fact. That is the same self-matching shape
 * {@code advice.sh} has been bitten by four times and {@code SheetFence}
 * strips comments for.
 *
 * <h2>Why an orphan is red rather than merely counted</h2>
 *
 * {@code bare=} (#1149) and {@code unfalsifiable=} (#1160) are tolerance
 * counters, and both exist because a real debt was already on the books when
 * the check landed. There is no debt here — the first run found zero — so
 * there is nothing to tolerate, and a counter that tolerates nothing is a
 * verdict wearing a softer word.
 *
 * <pre>
 *   probes/DocsRoster                     judge docs/ against the tree
 *   probes/DocsRoster --tree &lt;dir&gt;         judge another tree — the falsifier
 * </pre>
 */
public final class DocsRoster {

    /** The directory whose files must be discoverable. */
    private static final String DEFAULT_DOCS = "docs";

    /**
     * Where a name may be found: the docs themselves, plus the four documents
     * at the root that route a reader into them. A file named only by a
     * source comment is not discoverable by a reader of the documentation.
     */
    private static final String[] ROOT_READERS = {
            "README.md", "PRINCIPLES.md", "CLAUDE.md", "AGENTS.md",
    };

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();

        String docs = DEFAULT_DOCS;
        for (int i = 0; i < args.length; i++) {
            if ("--tree".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                docs = args[i];
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        List<Path> files = markdown(Path.of(docs));
        List<Path> readers = new ArrayList<>(files);
        for (String root : ROOT_READERS) {
            Path p = Path.of(root);
            if (Files.isRegularFile(p)) {
                readers.add(p);
            }
        }

        int orphans = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            boolean named = false;
            for (Path reader : readers) {
                if (reader.equals(file)) {
                    continue;   // a document naming itself proves nothing
                }
                if (Files.readString(reader, StandardCharsets.UTF_8).contains(name)) {
                    named = true;
                    break;
                }
            }
            if (!named) {
                orphans++;
                System.out.println("ORPHAN " + file + " — no other document names it");
            }
        }

        // The population reports; the verdict judges (#1221). A count of files
        // in a lane's exact-line row goes red the day somebody writes a
        // document, which is the wrong thing for a lane to punish.
        System.out.println("ROSTER_DOCS files=" + files.size() + " readers=" + readers.size());

        boolean held = orphans == 0 && !files.isEmpty();
        Probes.leave("VERDICT EVERY_DOCUMENT_IS_NAMED orphans=" + orphans
                        + " scanned_none=" + (files.isEmpty() ? 1 : 0),
                held ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
    }

    /** Every .md under a root, sorted, so two runs read the same bytes in the same order. */
    private static List<Path> markdown(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList();
        }
    }

    private DocsRoster() {}
}
