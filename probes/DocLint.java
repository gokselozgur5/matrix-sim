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
 * <p>Six questions, asked of the tree this runs in:
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
 * </ol>
 *
 * <p>The gate comparison needs three sources and takes the third from
 * {@code informed:}, which two conventions share: fourteen records name a
 * milestone ({@code informed: milestone v6.5}) and the rest name the phase
 * tracker issue the template ships ({@code informed: phase tracker #24}). A
 * record on the tracker convention has no third source, so it is counted out
 * of {@code gates_compared} rather than judged — D-008, D-023, D-024, D-032
 * and D-033 carry roadmap rows and are outside this check for that reason
 * alone. Unifying the convention would widen the check; it is not this unit's
 * call, and three of those five are desynced index-to-roadmap already.
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
                         int gaps, int unannotatedGaps, int beatClaims, int beatDrift) {

        boolean docsTrue() {
            return statusDrift == 0 && gateDrift == 0 && twoStatuses == 0
                    && missingConfirmation == 0 && unannotatedGaps == 0 && beatDrift == 0;
        }
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
        Report report = lint(canon, ArcBeats.measure(ticks, seed), true);
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
                + " gaps=" + r.gaps()
                + " unannotated_gaps=" + r.unannotatedGaps()
                + " beat_claims=" + r.beatClaims()
                + " beat_drift=" + r.beatDrift());
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
    static Report lint(Canon canon, ArcBeats.Arc arc, boolean print) {
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

        return new Report(canon.records().size(), index.size(), roadmap.size(), compared,
                drift, gates[0], gates[1], two, noConfirmation, gaps, unannotated,
                beats[0], beats[1]);
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
    private static void selfcheck() {
        ArcBeats.Arc arc = new ArcBeats.Arc(List.of(
                new ArcBeats.Found("birth", 100),
                new ArcBeats.Found("refusal", 200),
                new ArcBeats.Found("peace", 300)), 42, 6_000, 3);

        int broken = 0;
        broken += expect("true-canon", "none", lint(sample(c -> { }), arc, false));
        broken += expect("roadmap-desync", "status_drift",
                lint(sample(c -> c.roadmap.set(2, "| Districts | D-002 | 🟡 | #223 |")), arc, false));
        broken += expect("index-desync", "status_drift",
                lint(sample(c -> c.index.set(2, "| [D-002](adr/D-002-b.md) | Two | 🟡 | v6.0 | #2 |")), arc, false));
        broken += expect("record-desync", "status_drift",
                lint(sample(c -> c.bodies.get(2).set(1, "status: accepted")), arc, false));
        broken += expect("gate-index-desync", "gate_drift",
                lint(sample(c -> c.index.set(2, "| [D-002](adr/D-002-b.md) | Two | 🟢 | v7.5 | #2 |")), arc, false));
        broken += expect("gate-roadmap-desync", "gate_drift",
                lint(sample(c -> c.roadmap.set(0, "## v6.5 — Program")), arc, false));
        broken += expect("gate-record-desync", "gate_drift",
                lint(sample(c -> c.bodies.get(1).set(3, "informed: milestone v7.5")), arc, false));
        broken += expect("gate-cell-annotated", "none",
                lint(sample(c -> c.index.set(2, "| [D-002](adr/D-002-b.md) | Two | 🟢 | v6.0 (interface) | #2 |")),
                        arc, false));
        broken += expect("record-without-row", "status_drift",
                lint(sample(c -> c.index.remove(2)), arc, false));
        broken += expect("two-statuses", "two_statuses",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("", "Awaiting the Architect's verdict in #1."))),
                        arc, false));
        broken += expect("kept-label-excused", "none",
                lint(sample(c -> c.bodies.get(0).addAll(List.of("",
                        "*Recorded before the verdict, kept unedited:* Awaiting the Architect's verdict in #1."))),
                        arc, false));
        broken += expect("no-confirmation", "missing_confirmation",
                lint(sample(c -> c.bodies.get(0).remove("### Confirmation")), arc, false));
        broken += expect("unannotated-gap", "unannotated_gaps",
                lint(sample(c -> c.index.remove(c.index.size() - 1)), arc, false));
        broken += expect("stale-beat", "beat_drift",
                lint(sample(c -> c.readme.set(0, pinLine("100", "999", "300"))), arc, false));
        broken += expect("dropped-beat", "beat_drift",
                lint(sample(c -> c.readme.set(0, pinLine("100", "300"))), arc, false));
        broken += expect("no-pin", "beat_drift",
                lint(sample(c -> c.readme.set(0, "the film plays 100, 200 and 300.")), arc, false));
        broken += expect("stale-attribution", "beat_drift",
                lint(sample(c -> c.architecture.add("the door is 999 at `abc1234`.")), arc, false));

        System.out.println("SELFCHECK cases=17 broken=" + broken);
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
