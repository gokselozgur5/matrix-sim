import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Probe: the documents, judged the way the instrument lines are judged.
 *
 * <p>{@link LineLint} exists because D-020 made the instrument lines a
 * contract and enforced it with nobody. The documents are in the same
 * position and lose the same way: a claim bound to a command holds, and a
 * claim bound to prose goes stale the next time the thing it describes
 * moves. #907 (five decisions carrying two statuses) and #903 (four
 * published beats stale by ~420 ticks) are the same failure with different
 * subjects, and both were repaired by hand because nothing could fail.
 *
 * <p>Seven questions, asked of the tree this runs in:
 *
 * <ol>
 *   <li><b>One decision, one status.</b> The ADR front matter, the
 *       {@code docs/DECISIONS.md} index emoji and the {@code ROADMAP.md}
 *       gate cell must agree for every D-number that appears in more than
 *       one of them. Under Dev1/D-000 the emoji IS the permission to write
 *       code, so a contradiction leaves the gate undefined.</li>
 *   <li><b>One decision, one gate.</b> The same three sources must also name
 *       the same PHASE — the index Gate cell, the {@code ROADMAP.md} section
 *       the decision's row sits under, and the milestone the record's
 *       {@code informed:} line names. D-039 hangs scheduling on this column
 *       ("gates before units"), so a decision gated v6.0 in the index and
 *       v7.5 in the roadmap tells a reader both "cuttable now" and "wait two
 *       phases".</li>
 *   <li><b>One status per record.</b> An accepted record may not also
 *       claim, unlabelled, to be awaiting a verdict — nor a proposed one
 *       claim to have been accepted. D-029 forbids rewriting a record, so
 *       the pre-verdict sentence is kept and labelled; the label is what
 *       turns a contradiction into a chronology, and this check reads it.</li>
 *   <li><b>Every record says how it would be checked.</b> A
 *       {@code ### Confirmation} section, which the template ships and
 *       fifty-eight records currently carry.</li>
 *   <li><b>A missing D-number is explained.</b> D-055, D-056 and D-057 have
 *       no records; a gap with no note is indistinguishable from a lost
 *       file.</li>
 *   <li><b>The published beats are the beats.</b> README's pinned {@code main}
 *       column is compared against a live run of the film, via
 *       {@link ArcBeats#measure} rather than a second copy of the needles.</li>
 *   <li><b>A cited SHA resolves.</b> D-030 pins a measurement to a commit so a
 *       reader can go and re-run it, and an amend or a rebase kills that
 *       provenance silently: the figures beside a dead pin stay correct, so
 *       nothing in the prose looks wrong (#1131). Only the DECLARED citation
 *       forms are judged — {@code at `<sha>`}, {@code git archive <sha>}, and
 *       the same pin bare, because a mermaid {@code Note} cannot carry a
 *       backtick — since a bare hex scan returns twelve candidates in this
 *       tree and most are digest fragments or JSON, and a lock that cries wolf
 *       gets switched off.</li>
 * </ol>
 *
 * <p>The gate comparison needs three sources and takes the third from
 * {@code informed:}, which two conventions share: fourteen records name a
 * milestone ({@code informed: milestone v6.5}) and the rest name the phase
 * tracker issue the template ships ({@code informed: phase tracker #24}). A
 * record on the tracker convention has no third source, so it is counted out
 * of {@code gates_compared} rather than judged — D-008, D-023, D-024, D-032
 * and D-033 carry roadmap rows and are outside this check for that reason
 * alone. #1041 repaired the three of those five that were desynced
 * index-to-roadmap (D-008, D-023, D-024) by hand, for the same reason #957
 * repaired Season Three by hand: the check cannot see them. It still cannot.
 * Unifying the convention — or giving the gate its own front-matter key and
 * letting {@code informed:} mean what MADR means by it — would widen the
 * check to all nineteen roadmap rows, and that is the Architect's call.
 *
 * <pre>
 * java -cp out:probes/out DocLint [ticks] [seed]   lint the tree it stands in
 * java -cp out:probes/out DocLint --root DIR ...   lint another checkout
 * java -cp out:probes/out DocLint --selfcheck      falsify the lint, no universe
 * </pre>
 *
 * The {@code --selfcheck} form is the answer to "would this actually go red".
 * It builds a small canon in memory, breaks one thing at a time, and demands
 * that each break move exactly the counter it should — so the claim that a
 * desynced row fails is a lock and not a sentence in a PR body.
 */
public final class DocLint {

    // ---------------------------------------------------------------- model

    /** One ADR record: its number, the file it came from, its lines. */
    public record Rec(String id, String file, List<String> lines) {}

    /** The four documents this lint reads, already split into lines. */
    public record Canon(List<String> index, List<String> roadmap, List<String> readme,
                        List<String> architecture, List<Rec> records) {}

    /** What the run found, once. Every field is a count of things that are wrong. */
    public record Report(int records, int indexRows, int roadmapRows, int compared,
                         int statusDrift, int gatesCompared, int gateDrift,
                         int twoStatuses, int missingConfirmation,
                         int gaps, int unannotatedGaps, int beatClaims, int beatDrift,
                         int pinsScanned, int pinsPlaceholder, int deadPins,
                         int staleConfirmations) {

        boolean docsTrue() {
            return statusDrift == 0 && gateDrift == 0 && twoStatuses == 0
                    && missingConfirmation == 0 && unannotatedGaps == 0 && beatDrift == 0
                    && deadPins == 0 && staleConfirmations == 0;
        }
    }

    /**
     * Does this SHA name a commit in the checkout under test?
     *
     * <p>An interface rather than a call to {@code git} because the selfcheck
     * must be able to state a dead pin without owning a repository to make one
     * in: the lint is then the same code in both, and only the answer differs.
     */
    public interface Resolver {

        /** True when the sha names a commit that can be checked out. */
        boolean resolves(String sha);
    }

    // The four status words and the four glyphs the index spends them as.
    private static final Map<String, String> GLYPHS = new LinkedHashMap<>();
    static {
        GLYPHS.put("🟢", "accepted");   // green circle
        GLYPHS.put("🟡", "proposed");   // yellow circle
        GLYPHS.put("❌", "rejected");         // cross mark
        GLYPHS.put("🔵", "parked");     // blue circle
    }

    private static final Pattern INDEX_ROW =
            Pattern.compile("^\\|\\s*\\[(D-\\d{3})\\]\\([^)]*\\)\\s*\\|");
    private static final Pattern RECORD_FILE = Pattern.compile("^(D-\\d{3})-.*\\.md$");
    private static final Pattern FRONT_STATUS = Pattern.compile("^status:\\s*(\\S+)\\s*$");
    private static final Pattern D_NUMBER = Pattern.compile("D-(\\d{3})");

    // The gate column's three sources: a roadmap phase heading, a milestone in
    // front matter, and whatever phases the index cell names (`v1.0 (interface)`
    // is one phase with a note, not a second value).
    private static final Pattern ROADMAP_PHASE = Pattern.compile("^##\\s+(v\\d+(?:\\.\\d+)?)(?![\\d.])");
    private static final Pattern FRONT_MILESTONE =
            Pattern.compile("^informed:.*\\bmilestone\\s+(v\\d+(?:\\.\\d+)?)(?![\\d.])");
    private static final Pattern PHASE = Pattern.compile("v\\d+(?:\\.\\d+)?(?![\\d.])");
    private static final Pattern D_RANGE =
            Pattern.compile("D-(\\d{3})\\s*(?:[–—-]|through|to)\\s*(?:D-)?(\\d{3})");

    // A record awaiting a verdict says so; a record that was accepted says that.
    private static final Pattern AWAITING = Pattern.compile("(?i)\\bawaiting\\b[^.]{0,60}\\bverdict\\b");
    private static final Pattern ACCEPTED_CLAIM = Pattern.compile("(?i)\\baccepted by the owner\\b");
    private static final String KEPT_LABEL = "Recorded before the verdict";

    // README pins main's beats to the tree they were measured on. This is the anchor.
    private static final Pattern PIN = Pattern.compile("On `main` at `([0-9a-f]{7,40})`");
    private static final Pattern ATTRIBUTED =
            Pattern.compile("(?<![\\w-])(\\d[\\d,]*)\\s+at\\s+`([0-9a-f]{7,40})`");
    private static final Pattern BACKTICKED = Pattern.compile("`[^`]*`");
    // A published number: not glued to a word, not half of a date or a `20,000-tick` compound.
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w-])(\\d[\\d,]*)(?![\\w-])");

    // The two forms in which this canon CITES a tree, and nothing else. `at `sha`` is D-030's
    // pin; `git archive sha` is the command the reader is told to run. Prose that merely holds
    // hex — a digest fragment, a JSON example — makes no claim and is not judged.
    private static final Pattern CITE_AT = Pattern.compile("\\bat\\s+`([0-9a-f]{7,40})`");
    private static final Pattern CITE_ARCHIVE = Pattern.compile("\\bgit archive\\s+([0-9a-f]{7,40})\\b");

    // The same pin without its backticks. Two of the four `ea2c141` citations #1131 counted are
    // inside a mermaid Note, where a backtick would render as one — so the form that misses them
    // would have called the tree clean after repairing half of it. Bare hex needs a guard the
    // backticked form gets for free: `at 1299000` is a tick count and `at 6000000` is a budget,
    // both perfectly good short hex.
    private static final Pattern CITE_AT_BARE = Pattern.compile("\\bat\\s+([0-9a-f]{7,40})\\b");

    // Two guards, either of which is enough, because one of them alone had a computable hole.
    //
    // #1131 shipped only the first: a bare citation carries a LETTER. A seven-character sha is
    // all digits with probability (10/16)^7, about one pin in twenty-seven, and such a pin cited
    // in a Note would die to a rebase with this lint saying nothing (#1133) — the exact silence
    // it was written to end, restored for a measurable slice. The repair #1131 chose was to
    // lengthen the pin until its eighth character happened to be a letter, which is editing the
    // document to satisfy the lint.
    //
    // The second guard is CONTEXT, and it is the honest one: a bare pin is only ever written in
    // a sentence that says what it is — `t=1299 at <sha>, seed 42`, `measured at <sha>`. A line
    // carrying one of those words is making a provenance claim whatever its hex looks like. The
    // word list is printed with the count, so a phrasing nobody thought of is visible as a
    // number rather than invisible as a pass.
    private static final Pattern HAS_LETTER = Pattern.compile("[a-f]");
    private static final Pattern MEASUREMENT_CONTEXT =
            Pattern.compile("(?i)\\b(seed|measured|pinned|re-?run|reproduces?|as of|tick)\\b");

    // `abc1234` is this repository's stand-in sha: D-061 quotes it inside a sentence ABOUT
    // dead pins, and DocLint's own selfcheck canon is built on it. It is hex, it is in the
    // citation form, and it names nothing on purpose — so it is counted and named rather than
    // matched away, because an exemption nobody can see is how the next one gets added quietly.
    private static final Set<String> PLACEHOLDERS = Set.of("abc1234");

    // ----------------------------------------------------------------- main

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        List<String> rest = new ArrayList<>(List.of(args));
        if (!rest.isEmpty() && rest.get(0).equals("--selfcheck")) {
            selfcheck();
            return;
        }
        Path root = Path.of(".");
        if (rest.size() >= 2 && rest.get(0).equals("--root")) {
            root = Path.of(rest.get(1));
            rest = rest.subList(2, rest.size());
        }
        long ticks = !rest.isEmpty() ? Long.parseLong(rest.get(0)) : 6_000;
        long seed = rest.size() > 1 ? Long.parseLong(rest.get(1)) : 42;

        Canon canon = read(root);
        if (canon == null) {
            System.out.println("VERDICT DOCS_DRIFT");
            return;
        }
        Report report = lint(canon, ArcBeats.measure(ticks, seed), resolver(root), true);
        print(report);
        System.out.println(report.docsTrue() ? "VERDICT DOCS_TRUE" : "VERDICT DOCS_DRIFT");
    }

    private static void print(Report r) {
        System.out.println("DOCS records=" + r.records()
                + " index_rows=" + r.indexRows()
                + " roadmap_rows=" + r.roadmapRows()
                + " compared=" + r.compared()
                + " status_drift=" + r.statusDrift()
                + " gates_compared=" + r.gatesCompared()
                + " gate_drift=" + r.gateDrift()
                + " two_statuses=" + r.twoStatuses()
                + " missing_confirmation=" + r.missingConfirmation()
                + " confirmation_stale=" + r.staleConfirmations()
                + " gaps=" + r.gaps()
                + " unannotated_gaps=" + r.unannotatedGaps()
                + " beat_claims=" + r.beatClaims()
                + " beat_drift=" + r.beatDrift()
                + " pins=" + r.pinsScanned()
                + " placeholders=" + r.pinsPlaceholder()
                + " dead_pins=" + r.deadPins());
    }

    /**
     * The checkout's own answer to "is this a commit", or a resolver that admits
     * it cannot ask.
     *
     * <p>A tree with no {@code .git} — a tarball, an unpacked release — can still
     * be linted for everything else, and calling every honest pin dead there would
     * blame the reader's download for the author's provenance. A SHALLOW clone is
     * the same ignorance wearing git's clothes: {@code --depth 1} leaves every pin
     * older than the tip unreachable, and locks.yml's {@code fetch-depth: 0} is a
     * comment in another file governing one of the places this probe runs. So both
     * ways of not knowing end in the same sentence: name the reason, judge nothing,
     * stay green. Silence is the one thing this will not do.
     */
    private static Resolver resolver(Path root) {
        if (!ran(root, "rev-parse", "--verify", "HEAD")) {
            System.out.println("PIN_SCAN skipped=no-git root=" + root
                    + " (nothing here can say whether a sha resolves)");
            return sha -> true;
        }
        if ("true".equals(read(root, "rev-parse", "--is-shallow-repository"))) {
            System.out.println("PIN_SCAN skipped=shallow root=" + root
                    + " (a depth-limited clone cannot resolve a pin older than its tip)");
            return sha -> true;
        }
        return sha -> ran(root, "cat-file", "-e", sha + "^{commit}");
    }

    /** Runs one git plumbing command in the checkout; true when it exits 0. */
    private static boolean ran(Path root, String... args) {
        return git(root, args) != null;
    }

    /** One git command's stdout, trimmed, or empty when it could not be run. */
    private static String read(Path root, String... args) {
        String out = git(root, args);
        return out == null ? "" : out;
    }

    /** The command's trimmed stdout when it exits 0, else null. */
    private static String git(Path root, String... args) {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return p.waitFor() == 0 ? out.trim() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ----------------------------------------------------------------- read

    /** Reads the canon out of a checkout. Returns null, loudly, if a document is missing. */
    private static Canon read(Path root) throws IOException {
        Path index = root.resolve("docs/DECISIONS.md");
        Path roadmap = root.resolve("ROADMAP.md");
        Path readme = root.resolve("README.md");
        Path architecture = root.resolve("docs/ARCHITECTURE.md");
        Path adr = root.resolve("docs/adr");
        boolean ok = true;
        for (Path p : List.of(index, roadmap, readme, architecture)) {
            if (!Files.isRegularFile(p)) {
                System.out.println("MISSING_CANON path=" + p);
                ok = false;
            }
        }
        if (!Files.isDirectory(adr)) {
            System.out.println("MISSING_CANON path=" + adr);
            ok = false;
        }
        if (!ok) {
            return null;
        }
        List<Rec> records = new ArrayList<>();
        try (Stream<Path> files = Files.list(adr)) {
            List<Path> sorted = files.sorted().toList();
            for (Path p : sorted) {
                Matcher m = RECORD_FILE.matcher(p.getFileName().toString());
                if (m.matches()) {
                    records.add(new Rec(m.group(1), p.getFileName().toString(), lines(p)));
                }
            }
        }
        return new Canon(lines(index), lines(roadmap), lines(readme), lines(architecture), records);
    }

    private static List<String> lines(Path p) {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ----------------------------------------------------------------- lint

    /** The whole judgement, on a canon and one run's beats. Printing is optional so selfcheck is quiet. */
    static Report lint(Canon canon, ArcBeats.Arc arc, Resolver resolver, boolean print) {
        Map<String, String> index = indexStatuses(canon.index());
        Map<String, String> roadmap = roadmapStatuses(canon.roadmap());
        Map<String, String> front = new LinkedHashMap<>();
        for (Rec rec : canon.records()) {
            front.put(rec.id(), frontStatus(rec));
        }

        Set<String> ids = new TreeSet<>();
        ids.addAll(front.keySet());
        ids.addAll(index.keySet());
        ids.addAll(roadmap.keySet());

        int drift = 0;
        int compared = 0;
        for (String id : ids) {
            String a = front.getOrDefault(id, "absent");
            String i = index.getOrDefault(id, "absent");
            String r = roadmap.getOrDefault(id, "-");
            compared++;
            // "One row per decision" is the index's own first sentence, so a record
            // with no row and a row with no record are drift too, not a skip: the
            // pair that is missing is exactly the pair nothing else can catch.
            boolean bad = !a.equals(i) || (!r.equals("-") && !r.equals(a));
            if (bad) {
                drift++;
                if (print) {
                    System.out.println("STATUS " + id + " adr=" + a + " index=" + i + " roadmap=" + r + " DRIFT");
                }
            }
        }

        int[] gates = gateCheck(canon, ids, print);

        int two = 0;
        int noConfirmation = 0;
        int staleConfirmation = 0;
        for (Rec rec : canon.records()) {
            String status = frontStatus(rec);
            String contradiction = contradiction(rec, status);
            if (contradiction != null) {
                two++;
                if (print) {
                    System.out.println("TWO_STATUSES " + rec.id() + " status=" + status
                            + " claim=\"" + contradiction + "\"");
                }
            }
            if (!hasHeading(rec, "### Confirmation")) {
                noConfirmation++;
                if (print) {
                    System.out.println("NO_CONFIRMATION " + rec.id() + " file=" + rec.file());
                }
            }
            if (staleConfirmation(rec, status)) {
                staleConfirmation++;
                if (print) {
                    System.out.println("STALE_CONFIRMATION " + rec.id() + " file=" + rec.file()
                            + " (accepted, Confirmation not applicable, no errata)");
                }
            }
        }

        int gaps = 0;
        int unannotated = 0;
        if (!front.isEmpty()) {
            Set<Integer> have = new TreeSet<>();
            for (String id : front.keySet()) {
                have.add(Integer.parseInt(id.substring(2)));
            }
            Set<Integer> annotated = annotations(canon.index());
            int lo = Collections.min(have);
            int hi = Collections.max(have);
            for (int n = lo; n <= hi; n++) {
                if (have.contains(n)) {
                    continue;
                }
                gaps++;
                String id = String.format("D-%03d", n);
                if (annotated.contains(n)) {
                    if (print) {
                        System.out.println("GAP " + id + " annotated=index");
                    }
                } else {
                    unannotated++;
                    if (print) {
                        System.out.println("GAP " + id + " unannotated");
                    }
                }
            }
        }

        int[] beats = beatCheck(canon, arc, print);
        int[] pins = pinCheck(canon, resolver, print);

        return new Report(canon.records().size(), index.size(), roadmap.size(), compared,
                drift, gates[0], gates[1], two, noConfirmation, gaps, unannotated,
                beats[0], beats[1], pins[0], pins[1], pins[2], staleConfirmation);
    }

    // -------------------------------------------------------------- the pins

    /**
     * Every cited tree, asked whether it is still a tree.
     *
     * <p>The check is one line of git per pin; the design is entirely in what
     * gets fed to it. Judging a bare hex grep returns twelve candidates in this
     * canon and most assert nothing — a digest fragment, a JSON example, the
     * placeholder D-061 quotes in a sentence about this exact failure. So the
     * scan reads the forms in which a document CITES a tree, and reports the
     * number it examined: a regex that silently matched nothing would otherwise
     * pass as a clean sweep, which is the same silence #1131 is about.
     *
     * @return {@code {scanned, placeholders, dead}}
     */
    private static int[] pinCheck(Canon canon, Resolver resolver, boolean print) {
        List<Object[]> docs = new ArrayList<>();
        docs.add(new Object[] {"README.md", canon.readme()});
        docs.add(new Object[] {"docs/ARCHITECTURE.md", canon.architecture()});
        docs.add(new Object[] {"docs/DECISIONS.md", canon.index()});
        docs.add(new Object[] {"ROADMAP.md", canon.roadmap()});
        for (Rec rec : canon.records()) {
            docs.add(new Object[] {"docs/adr/" + rec.file(), rec.lines()});
        }

        int scanned = 0;
        int placeholder = 0;
        int dead = 0;
        int bareCites = 0;
        for (Object[] doc : docs) {
            String name = (String) doc[0];
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) doc[1];
            for (int n = 0; n < lines.size(); n++) {
                Set<String> seen = new LinkedHashSet<>();
                for (Pattern form : List.of(CITE_AT, CITE_ARCHIVE, CITE_AT_BARE)) {
                    Matcher m = form.matcher(lines.get(n));
                    while (m.find()) {
                        String sha = m.group(1);
                        boolean bare = form == CITE_AT_BARE;
                        if (bare && !HAS_LETTER.matcher(sha).find()
                                && !MEASUREMENT_CONTEXT.matcher(lines.get(n)).find()) {
                            continue; // digits-only AND no claim on the line: a count, not a tree
                        }
                        if (!seen.add(sha)) {
                            continue; // the backticked form already counted this one on this line
                        }
                        scanned++;
                        if (bare) {
                            bareCites++;
                        }
                        if (PLACEHOLDERS.contains(sha)) {
                            placeholder++;
                            if (print) {
                                System.out.println("PIN file=" + name + " line=" + (n + 1)
                                        + " sha=" + sha + " placeholder");
                            }
                        } else if (!resolver.resolves(sha)) {
                            dead++;
                            if (print) {
                                System.out.println("PIN file=" + name + " line=" + (n + 1)
                                        + " sha=" + sha + " DEAD (does not resolve to a commit)");
                            }
                        }
                    }
                }
            }
        }
        if (print) {
            System.out.println("PIN_SCAN scanned=" + scanned
                    + " bare=" + bareCites
                    + " placeholders=" + placeholder + " dead=" + dead);
        }
        return new int[] {scanned, placeholder, dead};
    }

    // -------------------------------------------------------------- the gate

    /**
     * The gate column, across the same three documents the status uses.
     *
     * <p>The roadmap is the authority and the other two are its transcripts:
     * the four-beat split of Season Three was the Architect's ruling in
     * session (2026-08-11 17:32, carried by {@code 3f122ee} — "milestones cut
     * and gates reassigned to their beats"), and it was the roadmap that the
     * ruling was written into. So a disagreement is read as the index or the
     * record lagging, never as a second plan.
     *
     * <p>A decision is only judged when it has all three: a roadmap row (its
     * section heading is the phase), a Gate cell, and an {@code informed:}
     * line naming a milestone. The index cell may annotate its phase —
     * {@code v1.0 (interface)} is one phase with a note — so it is read as
     * the set of phases it names and must contain the roadmap's.
     *
     * @return {@code {compared, drift}}
     */
    private static int[] gateCheck(Canon canon, Set<String> ids, boolean print) {
        Map<String, String> index = indexGates(canon.index());
        Map<String, String> roadmap = roadmapGates(canon.roadmap());
        Map<String, String> front = new LinkedHashMap<>();
        for (Rec rec : canon.records()) {
            String milestone = frontMilestone(rec);
            if (milestone != null) {
                front.put(rec.id(), milestone);
            }
        }

        int compared = 0;
        int drift = 0;
        for (String id : ids) {
            String r = roadmap.get(id);
            String a = front.get(id);
            String i = index.get(id);
            if (r == null || a == null || i == null) {
                // A two-source comparison cannot say which one lagged, and a
                // record with no index row at all is the status check's fault
                // to report, not a disagreement about which phase it belongs to.
                continue;
            }
            compared++;
            boolean bad = !r.equals(a) || !phases(i).contains(r);
            if (bad) {
                drift++;
            }
            if (bad && print) {
                System.out.println("GATE " + id + " index=" + i + " adr=" + a + " roadmap=" + r + " DRIFT");
            }
        }
        return new int[] {compared, drift};
    }

    /** The Gate cell of each index row, verbatim — the annotation is part of the claim. */
    private static Map<String, String> indexGates(List<String> index) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : index) {
            Matcher m = INDEX_ROW.matcher(line);
            if (!m.find()) {
                continue;
            }
            String[] cells = line.split("\\|", -1);
            out.put(m.group(1), cells.length > 4 ? cells[4].trim() : "absent");
        }
        return out;
    }

    /** The phase whose section holds each decision's roadmap gate row. */
    private static Map<String, String> roadmapGates(List<String> roadmap) {
        Map<String, String> out = new LinkedHashMap<>();
        String phase = null;
        for (String line : roadmap) {
            Matcher heading = ROADMAP_PHASE.matcher(line);
            if (heading.find()) {
                phase = heading.group(1);
                continue;
            }
            String[] cells = line.split("\\|", -1);
            if (phase == null || cells.length < 5) {
                continue;
            }
            String id = cells[2].trim();
            if (id.matches("D-\\d{3}")) {
                out.putIfAbsent(id, phase);
            }
        }
        return out;
    }

    /** The milestone a record's {@code informed:} line names, or null on the tracker convention. */
    private static String frontMilestone(Rec rec) {
        for (String line : rec.lines()) {
            Matcher m = FRONT_MILESTONE.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** Every phase a cell names, so an annotated cell is read by its phases and not its prose. */
    private static Set<String> phases(String cell) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = PHASE.matcher(cell);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    // ------------------------------------------------------------- the beats

    /**
     * The published beats against the film.
     *
     * <p>README pins main's column to the SHA it was measured on. That pin is
     * what #903 shipped, and it is honest — but a claim about an ancestor can
     * never be falsified by the tree it sits in, which is exactly how the
     * column went 420 ticks stale the first time. So the rule here is that
     * the pinned main column must describe THIS tree: when the film moves,
     * re-measure and re-pin in the same PR that moved it.
     *
     * <p>Numbers inside backticks are command arguments, not published claims,
     * and are cut before the line is read — {@code ArcBeats 6000} at the end of
     * the sentence is the instrument's invocation, not a beat.
     *
     * @return {@code {claims, drift}}
     */
    private static int[] beatCheck(Canon canon, ArcBeats.Arc arc, boolean print) {
        Set<Long> ran = new LinkedHashSet<>();
        for (ArcBeats.Found f : arc.beats()) {
            if (f.tick() >= 0) {
                ran.add(f.tick());
            }
        }

        List<String> anchors = new ArrayList<>();
        String sha = null;
        for (String line : canon.readme()) {
            Matcher m = PIN.matcher(line);
            if (m.find()) {
                anchors.add(line);
                sha = m.group(1);
            }
        }
        if (anchors.size() != 1) {
            if (print) {
                System.out.println("BEAT_PIN anchors=" + anchors.size()
                        + " want=1 MISSING (README must publish exactly one `On `main` at `<sha>`` column)");
            }
            return new int[] {0, 1};
        }

        int claims = 0;
        int drift = 0;
        List<Long> published = numbers(anchors.get(0), arc.ticks());
        claims += published.size();
        boolean ordered = true;
        for (int n = 1; n < published.size(); n++) {
            if (published.get(n) < published.get(n - 1)) {
                ordered = false;
            }
        }
        Set<Long> publishedSet = new LinkedHashSet<>(published);
        boolean same = publishedSet.equals(ran);
        if (!same || !ordered) {
            drift++;
        }
        if (print) {
            System.out.println("BEAT_PIN sha=" + sha
                    + " doc=" + ticksOf(publishedSet)
                    + " run=" + ticksOf(ran)
                    + " ordered=" + ordered
                    + (same && ordered ? " ok" : " STALE"));
        }

        // A tick attributed to the pinned tree anywhere in canon must be a beat of it.
        for (String[] where : new String[][] {
                {"README.md", "readme"}, {"docs/ARCHITECTURE.md", "architecture"}}) {
            List<String> doc = where[1].equals("readme") ? canon.readme() : canon.architecture();
            for (int n = 0; n < doc.size(); n++) {
                Matcher m = ATTRIBUTED.matcher(doc.get(n));
                while (m.find()) {
                    if (!m.group(2).equals(sha)) {
                        continue; // attributed to another tree — this lint cannot run that one
                    }
                    long tick = Long.parseLong(m.group(1).replace(",", ""));
                    claims++;
                    boolean ok = ran.contains(tick);
                    if (!ok) {
                        drift++;
                    }
                    if (print) {
                        System.out.println("BEAT_ATTRIB file=" + where[0] + " line=" + (n + 1)
                                + " tick=" + tick + " sha=" + sha + (ok ? " ok" : " STALE"));
                    }
                }
            }
        }
        return new int[] {claims, drift};
    }

    /** A tick list as one greppable field: no spaces, so a verdict line stays one fact wide. */
    private static String ticksOf(Set<Long> ticks) {
        StringBuilder sb = new StringBuilder();
        for (long t : ticks) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** Every number a line publishes, in order: backticked code cut, out-of-range dropped. */
    private static List<Long> numbers(String line, long ticks) {
        String prose = BACKTICKED.matcher(line).replaceAll(" ");
        List<Long> found = new ArrayList<>();
        Matcher m = NUMBER.matcher(prose);
        while (m.find()) {
            long value = Long.parseLong(m.group(1).replace(",", ""));
            if (value >= 1 && value <= ticks) {
                found.add(value);
            }
        }
        return found;
    }

    // ------------------------------------------------------------- readers

    private static Map<String, String> indexStatuses(List<String> index) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : index) {
            Matcher m = INDEX_ROW.matcher(line);
            if (!m.find()) {
                continue;
            }
            String[] cells = line.split("\\|", -1);
            out.put(m.group(1), cells.length > 3 ? glyph(cells[3]) : "unknown");
        }
        return out;
    }

    private static Map<String, String> roadmapStatuses(List<String> roadmap) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : roadmap) {
            String[] cells = line.split("\\|", -1);
            if (cells.length < 5) {
                continue;
            }
            String id = cells[2].trim();
            if (!id.matches("D-\\d{3}")) {
                continue;
            }
            out.put(id, glyph(cells[3]));
        }
        return out;
    }

    private static String glyph(String cell) {
        for (Map.Entry<String, String> e : GLYPHS.entrySet()) {
            if (cell.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return "unknown";
    }

    private static String frontStatus(Rec rec) {
        for (String line : rec.lines()) {
            Matcher m = FRONT_STATUS.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
        }
        return "unknown";
    }

    /**
     * An accepted record whose Confirmation says it does not apply, and which
     * says nothing about having been overtaken.
     *
     * <p>The narrowest honest reading of "stale", and the one #1128 asked for.
     * D-008, D-023 and D-024 each parked a mechanism, wrote "Not applicable
     * while parked", and then the mechanism SHIPPED — the processor coupling is
     * `COMPUTE_MODEL`, the chronos log is three command-line flags, the LOD park
     * is a TRACE line in every long run — while the record kept saying it had
     * not been built. Nothing compared a Confirmation against `src/`, and the
     * three sat wrong for days.
     *
     * <p>What this check does NOT do is read `src/`: it cannot tell whether the
     * parked option is reachable, and a lint that tried would be guessing at
     * which identifier corresponds to which decision. It asks the cheaper
     * question that catches the same class — a record cannot both be ACCEPTED
     * and permanently excused from proving itself. Records are immutable
     * (D-029), so the answer is never an edit to the outcome: it is an errata,
     * which is exactly what D-033 already carries and why D-033 is not flagged.
     */
    private static boolean staleConfirmation(Rec rec, String status) {
        if (!status.equals("accepted")) {
            return false;
        }
        boolean excused = false;
        boolean errata = false;
        for (String line : rec.lines()) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("not applicable")) {
                excused = true;
            }
            if (lower.contains("errata")) {
                errata = true;
            }
        }
        return excused && !errata;
    }

    private static boolean hasHeading(Rec rec, String heading) {
        for (String line : rec.lines()) {
            if (line.trim().equals(heading)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The second status a record states, or null.
     *
     * <p>Read by paragraph, because D-029 keeps the pre-verdict sentence and
     * labels it rather than deleting it: an "awaiting" claim inside a
     * paragraph that opens with the kept-text label is a quotation of the
     * past and asserts nothing about now. An unlabelled one is a live claim,
     * and a record cannot hold two.
     */
    private static String contradiction(Rec rec, String status) {
        for (List<String> para : paragraphs(rec.lines())) {
            String text = String.join(" ", para);
            if (text.contains(KEPT_LABEL)) {
                continue;
            }
            if (status.equals("accepted")) {
                Matcher m = AWAITING.matcher(text);
                if (m.find()) {
                    return m.group();
                }
            } else if (status.equals("proposed")) {
                Matcher m = ACCEPTED_CLAIM.matcher(text);
                if (m.find()) {
                    return m.group();
                }
            }
        }
        return null;
    }

    private static List<List<String>> paragraphs(List<String> lines) {
        List<List<String>> out = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                if (!current.isEmpty()) {
                    out.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(line);
            }
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
    }

    /** Every D-number the index mentions or covers with a range — a gap's explanation. */
    private static Set<Integer> annotations(List<String> index) {
        Set<Integer> out = new TreeSet<>();
        for (String line : index) {
            if (INDEX_ROW.matcher(line).find()) {
                continue; // a row is a record, never an explanation of a missing one
            }
            Matcher range = D_RANGE.matcher(line);
            while (range.find()) {
                int lo = Integer.parseInt(range.group(1));
                int hi = Integer.parseInt(range.group(2));
                for (int n = Math.min(lo, hi); n <= Math.max(lo, hi); n++) {
                    out.add(n);
                }
            }
            Matcher one = D_NUMBER.matcher(line);
            while (one.find()) {
                out.add(Integer.parseInt(one.group(1)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ selfcheck

    /**
     * The lint, falsified. Each case breaks exactly one thing in a canon that
     * starts true and demands that exactly one counter moves. No universe is
     * built: the beats are handed in, so this runs in milliseconds and stays
     * a lock rather than a second full film.
     */
    /**
     * The selfcheck's git: one sha exists, everything else does not.
     *
     * <p>{@code fa1da4d} is borrowed from the real canon on purpose — it is the
     * v3.0.0 tag this repository cites, so the case that must stay green is
     * green for the same reason the tree is.
     */
    private static final Resolver RESOLVING = sha -> sha.equals("fa1da4d");

    private static void selfcheck() {
        ArcBeats.Arc arc = new ArcBeats.Arc(List.of(
                new ArcBeats.Found("birth", 100),
                new ArcBeats.Found("refusal", 200),
                new ArcBeats.Found("peace", 300)), 42, 6_000, 3);

        int broken = 0;
        broken += expect("true-canon", "none", lint(sample(c -> { }), arc, RESOLVING, false));
        broken += expect("roadmap-desync", "status_drift",
                lint(sample(c -> c.roadmap.set(2, "| Districts | D-002 | 🟡 | #223 |")), arc, RESOLVING, false));
        broken += expect("index-desync", "status_drift",
                lint(sample(c -> c.index.set(2, "| [D-002](adr/D-002-b.md) | Two | 🟡 | v6.0 | #2 |")), arc, RESOLVING, false));
        broken += expect("record-desync", "status_drift",
                lint(sample(c -> c.bodies.get(2).set(1, "status: accepted")), arc, RESOLVING, false));
        broken += expect("gate-index-desync", "gate_drift",
                lint(sample(c -> c.index.set(2, "| [D-002](adr/D-002-b.md) | Two | 🟢 | v7.5 | #2 |")), arc, RESOLVING, false));
        broken += expect("gate-roadmap-desync", "gate_drift",
                lint(sample(c -> c.roadmap.set(0, "## v6.5 — Program")), arc, RESOLVING, false));
        broken += expect("gate-record-desync", "gate_drift",
                lint(sample(c -> c.bodies.get(1).set(3, "informed: milestone v7.5")), arc, RESOLVING, false));
        broken += expect("gate-cell-annotated", "none",
                lint(sample(c -> c.index.set(2, "| [D-002](adr/D-002-b.md) | Two | 🟢 | v6.0 (interface) | #2 |")),
                        arc, RESOLVING, false));
        broken += expect("record-without-row", "status_drift",
                lint(sample(c -> c.index.remove(2)), arc, RESOLVING, false));
        broken += expect("two-statuses", "two_statuses",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("", "Awaiting the Architect's verdict in #1."))),
                        arc, RESOLVING, false));
        broken += expect("kept-label-excused", "none",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("",
                        "*Recorded before the verdict, kept unedited:* Awaiting the Architect's verdict in #1."))),
                        arc, RESOLVING, false));
        broken += expect("no-confirmation", "missing_confirmation",
                lint(sample(c -> c.bodies.get(0).remove("### Confirmation")), arc, RESOLVING, false));
        broken += expect("unannotated-gap", "unannotated_gaps",
                lint(sample(c -> c.index.remove(c.index.size() - 1)), arc, RESOLVING, false));
        broken += expect("stale-beat", "beat_drift",
                lint(sample(c -> c.readme.set(0, pinLine("100", "999", "300"))), arc, RESOLVING, false));
        broken += expect("dropped-beat", "beat_drift",
                lint(sample(c -> c.readme.set(0, pinLine("100", "300"))), arc, RESOLVING, false));
        broken += expect("no-pin", "beat_drift",
                lint(sample(c -> c.readme.set(0, "the film plays 100, 200 and 300.")), arc, RESOLVING, false));
        broken += expect("stale-attribution", "beat_drift",
                lint(sample(c -> c.architecture.add("the door is 999 at `abc1234`.")), arc, RESOLVING, false));
        broken += expect("dead-pin", "dead_pins",
                lint(sample(c -> c.architecture.add("measured at `0000000a`, seeds 1-500.")),
                        arc, RESOLVING, false));
        broken += expect("dead-pin-in-a-record", "dead_pins",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("", "Measured at `0000000a`."))),
                        arc, RESOLVING, false));
        broken += expect("dead-archive-command", "dead_pins",
                lint(sample(c -> c.architecture.add("re-run it: `git archive 0000000a`.")),
                        arc, RESOLVING, false));
        broken += expect("live-archive-command", "none",
                lint(sample(c -> c.architecture.add("re-run it: `git archive fa1da4d`.")),
                        arc, RESOLVING, false));
        // The mermaid case: a Note cannot carry backticks, so the pin arrives bare.
        broken += expect("bare-pin-in-a-note", "dead_pins",
                lint(sample(c -> c.architecture.add("    Note over RW,S: t=1299 at 0000000a, seed 42.")),
                        arc, RESOLVING, false));
        broken += expect("bare-live-pin-in-a-note", "none",
                lint(sample(c -> c.architecture.add("    Note over RW,S: t=1299 at fa1da4d, seed 42.")),
                        arc, RESOLVING, false));
        // The three ways a bare hex scan goes red on prose that claims nothing.
        broken += expect("bare-digits-are-not-a-pin", "none",
                lint(sample(c -> c.architecture.add("the corridor was held at 1299000 ticks.")),
                        arc, RESOLVING, false));
        // #1133: the letter guard alone has a computable hole — a seven-character sha is
        // all digits about one time in twenty-seven, and such a pin in a Note used to be
        // invisible. Context closes it: the line says what the number IS.
        broken += expect("bare-digits-in-a-measurement", "dead_pins",
                lint(sample(c -> c.architecture.add("    Note over RW,S: t=1299 at 0123456, seed 42.")),
                        arc, RESOLVING, false));
        broken += expect("bare-digits-with-no-claim", "none",
                lint(sample(c -> c.architecture.add("the corridor held at 1234567 for a while.")),
                        arc, RESOLVING, false));
        broken += expect("digest-fragment-is-not-a-pin", "none",
                lint(sample(c -> c.architecture.add("DIGEST tick=6000 sha=0000000adeadc0de.")),
                        arc, RESOLVING, false));
        broken += expect("placeholder-is-named-not-judged", "none",
                lint(sample(c -> c.architecture.add("a body saying \"measured at `abc1234`\" pins nothing.")),
                        arc, RESOLVING, false));
        broken += expect("stale-confirmation", "confirmation_stale",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("",
                        "Not applicable until unparked; the trigger is D-002 entering design."))),
                        arc, RESOLVING, false));
        broken += expect("errata-excuses-the-confirmation", "none",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("",
                        "Not applicable until unparked; the trigger is D-002 entering design.",
                        "", "**Errata (2026-08-15):** unparked, and here is the command that proves it."))),
                        arc, RESOLVING, false));
        broken += expect("a-proposed-record-may-be-excused", "none",
                lint(sample(c -> c.bodies.get(2).addAll(List.of("",
                        "Not applicable until the verdict lands."))),
                        arc, RESOLVING, false));

        System.out.println("SELFCHECK cases=31 broken=" + broken);
        System.out.println(broken == 0
                ? "SELFCHECK VERDICT DOCLINT_FALSIFIABLE"
                : "SELFCHECK VERDICT DOCLINT_BLIND");
    }

    private static int expect(String name, String want, Report got) {
        String moved = moved(got);
        boolean ok = moved.equals(want);
        System.out.println("SELFCHECK case=" + name + " expect=" + want + " got=" + moved
                + " verdict=" + (got.docsTrue() ? "DOCS_TRUE" : "DOCS_DRIFT")
                + (ok ? " ok" : " BROKEN"));
        return ok ? 0 : 1;
    }

    /** Which counter this report moved — "none", one name, or "many" if the case was not surgical. */
    private static String moved(Report r) {
        List<String> names = new ArrayList<>();
        if (r.statusDrift() > 0) {
            names.add("status_drift");
        }
        if (r.gateDrift() > 0) {
            names.add("gate_drift");
        }
        if (r.twoStatuses() > 0) {
            names.add("two_statuses");
        }
        if (r.missingConfirmation() > 0) {
            names.add("missing_confirmation");
        }
        if (r.unannotatedGaps() > 0) {
            names.add("unannotated_gaps");
        }
        if (r.beatDrift() > 0) {
            names.add("beat_drift");
        }
        if (r.deadPins() > 0) {
            names.add("dead_pins");
        }
        if (r.staleConfirmations() > 0) {
            names.add("confirmation_stale");
        }
        return names.isEmpty() ? "none" : names.size() == 1 ? names.get(0) : String.join("+", names);
    }

    /** A mutable canon under construction, so a selfcheck case is one lambda. */
    private static final class Draft {
        final List<String> index = new ArrayList<>();
        final List<String> roadmap = new ArrayList<>();
        final List<String> readme = new ArrayList<>();
        final List<String> architecture = new ArrayList<>();
        final List<List<String>> bodies = new ArrayList<>();
    }

    private static String pinLine(String... ticks) {
        return "- **On `main` at `abc1234`**: the film plays **" + String.join("** then **", ticks) + "**.";
    }

    /**
     * A canon of three records that is true in every respect the lint checks:
     * two records agreeing across all three sources, a third with a gap
     * (D-003) annotated in the index, and a README pin that matches the run.
     * D-001 sits on the phase-tracker convention, so its gate is out of the
     * comparison the way the tree's five older records are.
     */
    private static Canon sample(java.util.function.Consumer<Draft> mutate) {
        Draft d = new Draft();
        d.index.add("| ID | Decision | Status | Gate | Thread |");
        d.index.add("| [D-001](adr/D-001-a.md) | One | 🟢 | v6.0 | #1 |");
        d.index.add("| [D-002](adr/D-002-b.md) | Two | 🟢 | v6.0 | #2 |");
        d.index.add("| [D-004](adr/D-004-d.md) | Four | 🟡 | v7.0 | #4 |");
        d.index.add("D-003 was never issued: the number was claimed and the record never written.");

        d.roadmap.add("## v6.0 — The Heart of the City");
        d.roadmap.add("| Gate | Decision | Status | Thread |");
        d.roadmap.add("| Districts | D-002 | 🟢 accepted 2026-08-12 | #223 |");
        d.roadmap.add("## v7.0 — A Detective Story");
        d.roadmap.add("| Gate | Decision | Status | Thread |");
        d.roadmap.add("| Truce | D-004 | 🟡 | #224 |");

        d.readme.add(pinLine("100", "200", "300"));
        d.architecture.add("the door is 300 at `abc1234`, and 250 at the tag.");

        d.bodies.add(new ArrayList<>(List.of("---", "status: accepted",
                "consulted: thread #1", "informed: phase tracker #20", "---", "",
                "Accepted by the owner's verdict, 2026-08-12.", "", "### Confirmation", "",
                "The command that proves it.")));
        d.bodies.add(new ArrayList<>(List.of("---", "status: accepted",
                "consulted: thread #2", "informed: milestone v6.0", "---", "",
                "Accepted by the owner's verdict, 2026-08-12.", "", "### Confirmation", "",
                "The command that proves it.")));
        d.bodies.add(new ArrayList<>(List.of("---", "status: proposed",
                "consulted: thread #4", "informed: milestone v7.0", "---", "",
                "Final call in thread #4.", "", "### Confirmation", "",
                "The command that proves it.")));
        mutate.accept(d);

        List<Rec> records = List.of(
                new Rec("D-001", "D-001-a.md", d.bodies.get(0)),
                new Rec("D-002", "D-002-b.md", d.bodies.get(1)),
                new Rec("D-004", "D-004-d.md", d.bodies.get(2)));
        return new Canon(d.index, d.roadmap, d.readme, d.architecture, records);
    }

    private DocLint() {}
}
