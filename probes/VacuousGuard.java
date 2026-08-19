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
 * Probe: can a judged row pass over an EMPTY population?
 *
 * <p>A verdict with no denominator cannot tell <em>the contract held over
 * everything</em> from <em>the contract held over nothing</em>. This tree has
 * invented a counter for that five separate times — {@code checked_none=},
 * {@code swept_none=}, {@code scanned_none=}, {@code door_missing=},
 * {@code stale_none=} — each after a probe printed a passing line over an empty
 * set (#970's {@code INSTRUMENTS_UNPROVEN}, #1207's silent skip). #1373 counted
 * twenty-five judged rows with no such guard.
 *
 * <h2>Twenty-five was fourteen, and that is why this class exists</h2>
 *
 * #1373's count was true when it was taken. It missed a SECOND guard the tree
 * uses just as often: a probe that leaves with {@code Outcome.NEVER_AROSE} on an
 * empty population prints a DIFFERENT verdict word, so the pinned row goes red
 * without any number on the line at all.
 *
 * <pre>
 * SameTick        census == 0  ->  VERDICT NO_LIBERATIONS   (NEVER_AROSE)
 * BondScenario                 ->  VERDICT NOT_DEMONSTRATED (NEVER_AROSE)
 * ClauseAftermath              ->  VERDICT NO_FIRING        (NEVER_AROSE)
 * </pre>
 *
 * All three are on #1373's list of the dangerous group, and all three are
 * guarded. Five more already carry a {@code _none=} field. The population is
 * fourteen — and nothing in the tree could have said so, because the figure had
 * no producer to re-run (#1082). That is the finding this probe answers: not the
 * fourteen, but the fact that twenty-five rotted invisibly.
 *
 * <h2>The population is the table</h2>
 *
 * The bench's own {@code judge}/{@code known} rows are read as text, never
 * re-listed here — {@code LeaveContract}'s rule, for {@code LeaveContract}'s
 * reason: a list kept beside the table is a second copy of the bench's contract,
 * and the second copy is the one that goes stale (#1192).
 *
 * <h2>A ratchet against the branch base, not an exact pin</h2>
 *
 * {@code unguarded=} rides the census line. The verdict compares that population
 * with the same reader run over the branch base: an equal or smaller backlog is
 * green, and any growth is red. A repair therefore needs no unrelated number
 * edit, while the next blind row cannot spend slack left by earlier repairs
 * (#1649). If the base cannot be read, the comparison refuses to call itself a
 * pass.
 *
 * <p>A Git checkout supplies that base from local history. A pinned archive has
 * no history by design, so archive evidence pins BOTH inputs: extract the head
 * archive as usual, extract the base archive separately, and set
 * {@code VACUOUS_BASELINE_TREE} to the latter directory and
 * {@code VACUOUS_BASELINE_REF} to its commit SHA. The ref is a caller-attested
 * evidence label; the tree supplies the bytes. Supplying only one is unreadable,
 * never permission to compare the head with itself. Both roots must be gitless
 * and distinct (including through symlinks), and Git subprocesses discard inherited
 * {@code GIT_*} selectors. The canonical fresh extraction attests provenance,
 * immutability during the run and label-to-bytes identity; policy cannot prove those
 * properties from an arbitrary gitless directory (Ag9, probe-contract clause 6).
 * Checkout mode never fetches either: a local run inherits whatever
 * {@code origin/main} the operator last fetched, and a topic must contain that exact
 * tip. The ritual's hardline {@code git fetch origin main} followed by required
 * {@code git rebase origin/main} is the prerequisite to evidence. Topic-merge
 * fixtures exercise resolver topology; they do not authorize merge-based branch prep.
 *
 * <h2>What it cannot see</h2>
 *
 * The {@code NEVER_AROSE} test is textual and therefore generous: it asks
 * whether the constant is reachable in the file, not whether it is reachable on
 * the EMPTY path. A probe spending {@code NEVER_AROSE} for an unrelated reason
 * reads as guarded. That error UNDERSTATES the problem and can never invent one.
 * The ratchet therefore guards zero growth in the population this reader can
 * measure; it does not turn a textual approximation into semantic proof.
 *
 * <p>Comments are stripped before the constant is looked for (#1531), because a
 * {@code NEVER_AROSE} discussed in a javadoc is not a guard — the exact mistake
 * that let {@code CensusBeatDrift} print a failing verdict at exit 0 while
 * {@code LeaveContract} called it a style preference.
 *
 * <p>Usage: {@code java -cp out:probes/out VacuousGuard [repo-root]}
 *
 * <p>Pinned archive usage: {@code VACUOUS_BASELINE_TREE=/path/to/base
 * VACUOUS_BASELINE_REF=<base-sha> java -cp out:probes/out VacuousGuard}
 */
public final class VacuousGuard {


    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            System.exit(selfcheck(Files.createTempDirectory("vacuousguard")));
        }
        if (args.length > 0 && args[0].startsWith("--")) {
            System.err.println("FATAL unknown flag: " + args[0] + " (this probe takes [repo-root] or --selfcheck)");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        Path bench = root.resolve("probes/bench.sh");
        if (!Files.isReadable(bench)) {
            Probes.leave("VERDICT VACUOUS_GUARD_NO_BENCH " + bench, false);
        }

        Census current = census(root);
        Set<String> rows = current.rows();
        List<String> byField = current.byField();
        List<String> byWord = current.byWord();
        List<String> unguarded = current.unguarded();
        List<String> missing = current.missing();
        for (String probe : unguarded) {
            System.out.println("VACUOUS " + probe
                    + " judged=yes none_field=no never_arose=no"
                    + " (its row cannot tell a full population from an empty one)");
        }
        for (String probe : missing) {
            System.out.println("NO_SOURCE " + probe + " judged=yes");
        }

        // The population rides its own line, unpinned. LeaveContract learned this
        // twice in one afternoon: a census inside an exact-line row goes red for
        // reasons that have nothing to do with the check, and then it is a number
        // people edit until the lane is quiet (#1221, #884).
        // THE MEMBERS, NOT ONLY THE COUNT (#1550). `unguarded=` is pinned, so a probe
        // gaining a guard while another arrives needing one is a green row over a
        // different population. `LeaveContract`'s `by_hand=5` did exactly that across
        // #1531 — one probe out, one in, the number unmoved. The set rides its own line,
        // sorted and joined, UNPINNED: a member list in an exact-line grep is a list every
        // unit edits (#1192, #884).
        System.out.println("VACUOUS_MEMBERS unguarded=" + Probes.joined(unguarded));
        System.out.println("VACUOUS_CENSUS judged=" + rows.size()
                + " unguarded=" + unguarded.size()
                + " by_field=" + byField.size()
                + " by_word=" + byWord.size()
                + " no_source=" + missing.size());
        // `no_source` IS judged — a judged row naming a class with no file means
        // this read was over a population it could not see, which is the one
        // condition under which the count means nothing.
        // AND THE PROBE THAT MEASURES VACUOUS GUARDS SHOULD NOT BE ONE (#1607). Breaking
        // the self-match put this probe into its own `unguarded=` population, correctly:
        // its pinned verdict carried `unguarded=` and nothing else, and `unguarded=0` is
        // not the same statement as "the read opened a table". `judged_none=` is that
        // statement — the guard five siblings carry, and the one #970's
        // INSTRUMENTS_UNPROVEN is about.
        // THE VERDICT COMPARES TWO READS SINCE #1649. The old exact pin charged every
        // repair an unrelated edit; the first replacement fixed it to 25 and therefore
        // let a +1 regression pass after the live population fell to 24. A historical
        // cap accumulates slack. The only baseline that can make EVERY growth cost a
        // unit is the tree the unit is changing.
        //
        // Every checkout requires origin/main. A named main is that tip or one local
        // commit on it; named topics and detached commits must CONTAIN that exact tip,
        // not merely share a stale ancestor. This topology rule also handles GitHub's
        // synthetic PR merge without trusting forgeable event variables. A pinned
        // archive instead supplies an explicit base tree and ref. No network is used.
        // Any ambiguity is UNCOMPARED and red.
        Baseline baseline;
        try {
            baseline = baseline(root, System.getenv("VACUOUS_BASELINE_TREE"),
                    System.getenv("VACUOUS_BASELINE_REF"));
        } catch (IOException | InterruptedException e) {
            baseline = new Baseline("unread", null);
        }

        int baselineCount = baseline.census() == null ? -1 : baseline.census().unguarded().size();
        int delta = baselineCount < 0 ? 0 : unguarded.size() - baselineCount;
        System.out.println("VACUOUS_BASELINE ref=" + baseline.ref()
                + " unguarded=" + (baselineCount < 0 ? "unread" : baselineCount)
                + " delta=" + (baselineCount < 0 ? "unread" : delta));

        Policy policy = policy(unguarded.size(), baselineCount,
                missing.isEmpty() && !rows.isEmpty(),
                baseline.census() != null
                        && baseline.census().missing().isEmpty()
                        && !baseline.census().rows().isEmpty());
        Probes.leave(policy.line(), policy.outcome());
    }


    /** One run of the reader, kept whole so current and baseline use one implementation. */
    private record Census(Set<String> rows, List<String> byField, List<String> byWord,
                          List<String> unguarded, List<String> missing) {}

    /** The Git tree a unit is measured against and the census read from it. */
    private record Baseline(String ref, Census census) {}

    /** Commit graph used by the resolver's retained topology cases. */
    private record HistoryFixture(Path repo, String mainTip, String topic,
                                  String mainAdvanced, String freshTopic,
                                  String topicMerge, String prMerge) {}

    /** The stable verdict surface: counts stay on census lines, policy stays pinned. */
    private record Policy(String word, int growth, int judgedNone, int baselineNone,
                          Probes.Outcome outcome) {
        String line() {
            return "VERDICT " + word
                    + " growth=" + growth
                    + " judged_none=" + judgedNone
                    + " baseline_none=" + baselineNone;
        }
    }

    /**
     * Read the judged population in one tree.
     *
     * <p>This is deliberately the only copy of the classifier: comparing a current
     * reader with an older reader would make a code change look like population growth.
     */
    private static Census census(Path root) throws IOException {
        Set<String> rows = new LinkedHashSet<>();
        List<String> byField = new ArrayList<>();
        List<String> byWord = new ArrayList<>();
        List<String> unguarded = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // ONE ENTRY PER PROBE, AND EVERY ROW READ. A probe with two judged rows —
        // `SheetDump` has, and `LeaveContract` has since #1531 — is one subject,
        // and a first-row-wins rule reads whichever mode happens to sit higher in
        // the table: `NeutralDiff`'s selfcheck row is above its real one, so the
        // first draft of this loop called a guarded probe unguarded. A probe is
        // guarded if ANY of its rows carries the field.
        // ONE READER (#1590), and this probe is where the divergence was found: it read
        // the table in Java, disagreed with `counters.sh` about how many rows there are,
        // and the shell one turned out to be missing three (#1588).
        java.util.Map<String, Boolean> fielded = new java.util.LinkedHashMap<>();
        for (Probes.BenchRow row : Probes.benchRows(root.resolve("probes/bench.sh"))) {
            if (!row.judged()) {
                continue;
            }
            String probe = row.probe();
            // The first guard, and the one #1373 counted: a field on the pinned line
            // whose value is the size of what was read.
            boolean has = row.verdict().matches(".*\\b\\w+_none=\\d+.*");
            fielded.merge(probe, has, (a, b) -> a || b);
        }

        for (java.util.Map.Entry<String, Boolean> e : fielded.entrySet()) {
            String probe = e.getKey();
            rows.add(probe);
            if (e.getValue()) {
                byField.add(probe);
                continue;
            }
            Path src = root.resolve("probes/" + probe + ".java");
            if (!Files.isReadable(src)) {
                // `roster_check`'s question, not this one — but a check that
                // walked past it would report a clean count over a probe it
                // never opened.
                missing.add(probe);
                continue;
            }
            // The second guard, which #1373 did not count: an empty population
            // that leaves with NEVER_AROSE prints a different word, so the
            // pinned row goes red with no number on the line at all.
            // ASSEMBLED, BECAUSE A SEARCH FOR X CANNOT BE WRITTEN AS X (#1607). The search
            // was `contains("Outcome.NEVER_AROSE")` — which is the string on this very line,
            // so this probe counted ITSELF as guarded, by the string it uses to count
            // guards. A checker exempting itself is the sixth instance of one shape here:
            // `advice.sh` five times (#1033, #1157, #1222, #1265, #1276), `LeaveContract`
            // reading `System.exit` inside a comment (#1531), and `LEAVE_BY_HAND` satisfied
            // by prose about `LEAVE_BY_HAND` (#1605).
            //
            // Qualifying the name does NOT fix it — the qualified literal is still the
            // literal. Neither does the strip: the string is CODE. Only assembling it does,
            // which is the idiom `advice.sh` reaches for when it builds `--pr` from `$dash`
            // for exactly this reason. It hides what is searched for, which is the price,
            // and the alternative is a checker that cannot see itself.
            //
            // The probe is NOT excluded from its own population instead. `LeaveContract`
            // states why one directory over: a check that excluded itself would report a
            // number nobody could reproduce by counting the file.
            if (guardsTheEmptyPath(src)) {
                byWord.add(probe);
            } else {
                unguarded.add(probe);
            }
        }
        return new Census(rows, byField, byWord, unguarded, missing);
    }

    /** Read the same census from an explicit pinned tree or from unambiguous local history. */
    private static Baseline baseline(Path root, String explicitTree, String explicitRef)
            throws IOException, InterruptedException {
        boolean hasTree = explicitTree != null && !explicitTree.isBlank();
        boolean hasRef = explicitRef != null && !explicitRef.isBlank();
        if (hasTree != hasRef) {
            throw new IOException("archive baseline requires both tree and ref");
        }
        if (hasTree) {
            Path current = root.toAbsolutePath().normalize();
            if (!git(current, "rev-parse", "--git-dir").isEmpty()) {
                throw new IOException("archive baseline is refused inside a Git checkout");
            }
            if (!explicitRef.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
                throw new IOException("archive baseline ref is not a full commit SHA");
            }
            Path tree = Path.of(explicitTree).toAbsolutePath().normalize();
            if (!Files.isDirectory(tree)) {
                throw new IOException("archive baseline tree is not a readable directory");
            }
            if (Files.isSameFile(current, tree)) {
                throw new IOException("archive baseline tree aliases the current tree");
            }
            if (!git(tree, "rev-parse", "--git-dir").isEmpty()) {
                throw new IOException("archive baseline tree is a live Git checkout");
            }
            if (!Files.isReadable(tree.resolve("probes/bench.sh"))) {
                throw new IOException("archive baseline has no probes/bench.sh");
            }
            return new Baseline(explicitRef, census(tree));
        }

        Path repo = root.toAbsolutePath().normalize();
        String ref = baselineRef(repo);
        Path tree = Files.createTempDirectory("vacuousguard-baseline");
        try {
            materializeCensusTree(repo, ref, tree);
            return new Baseline(ref, census(tree));
        } finally {
            deleteTree(tree);
        }
    }

    /**
     * Pick the comparison tree from local history without guessing checkout identity.
     *
     * <p>Every checkout requires the already-present {@code origin/main}. Named main
     * may be that ref itself or one local commit whose first parent is that ref; named
     * topics use their merge-base. Detached checkouts need no forgeable event metadata:
     * origin/main itself uses its parent, while every other accepted head uses a
     * non-self merge-base. An ancestor, unrelated graph or divergent named main refuses
     * comparison. No remote is contacted.
     */
    private static String baselineRef(Path root)
            throws IOException, InterruptedException {
        String ancestry = git(root, "rev-list", "--parents", "-n", "1", "HEAD");
        String[] commits = ancestry.split("\\s+");
        if (commits.length < 2) {
            throw new IOException("HEAD has no readable parent");
        }
        String head = commits[0];
        String firstParent = commits[1];
        String remoteBase = git(root, "rev-parse", "refs/remotes/origin/main");
        if (remoteBase.isEmpty()) {
            throw new IOException("checkout has no readable origin/main");
        }
        String branch = git(root, "branch", "--show-current");
        if (!branch.isEmpty()) {
            return baselineForBranch(root, branch, head, firstParent, remoteBase);
        }

        if (head.equals(remoteBase)) {
            return firstParent;
        }
        String mergeBase = git(root, "merge-base", head, remoteBase);
        if (!mergeBase.equals(remoteBase)) {
            throw new IOException("detached checkout does not contain local origin/main");
        }
        return mergeBase;
    }

    /** Main changes its known tip; every topic changes its distinct merge-base with main. */
    private static String baselineForBranch(Path root, String branch, String head,
                                            String firstParent, String remoteBase)
            throws IOException, InterruptedException {
        if (branch.equals("main")) {
            if (head.equals(remoteBase)) {
                return firstParent;
            }
            if (firstParent.equals(remoteBase)) {
                return remoteBase;
            }
            throw new IOException("named main diverges from local origin/main");
        }
        String mergeBase = git(root, "merge-base", head, remoteBase);
        if (!mergeBase.equals(remoteBase)) {
            throw new IOException("topic checkout does not contain local origin/main");
        }
        return mergeBase;
    }

    /** Materialize exactly the files the census reads, without checking out the ref. */
    private static void materializeCensusTree(Path repo, String ref, Path tree)
            throws IOException, InterruptedException {
        Path bench = tree.resolve("probes/bench.sh");
        if (!writeGitFile(repo, ref, "probes/bench.sh", bench)) {
            throw new IOException("baseline has no probes/bench.sh");
        }
        Set<String> probes = new LinkedHashSet<>();
        for (Probes.BenchRow row : Probes.benchRows(bench)) {
            if (row.judged()) {
                probes.add(row.probe());
            }
        }
        for (String probe : probes) {
            String relative = "probes/" + probe + ".java";
            writeGitFile(repo, ref, relative, tree.resolve(relative));
        }
    }

    /** One Git process, insulated from caller selectors that can redirect its repository. */
    private static ProcessBuilder gitProcess(Path root, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        return builder;
    }

    /** One local Git query; a missing optional ref is the empty answer. */
    private static String git(Path root, String... args) throws IOException, InterruptedException {
        Process process = gitProcess(root, args)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] stdout = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            return "";
        }
        return new String(stdout, StandardCharsets.UTF_8).trim();
    }

    /** A Git mutation used only inside the selfcheck's private fixture repository. */
    private static void fixtureGit(Path root, String... args)
            throws IOException, InterruptedException {
        Process process = gitProcess(root, args)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (process.waitFor() != 0) {
            throw new IOException("fixture git command failed: " + String.join(" ", args));
        }
    }

    /** Build the graph that distinguishes main, topics, local merges and PR merges. */
    private static HistoryFixture historyFixture(Path tmp)
            throws IOException, InterruptedException {
        Path repo = tmp.resolve("history");
        Files.createDirectories(repo.resolve("probes"));
        fixtureGit(repo, "-c", "init.defaultBranch=main", "init", "-q");
        Files.createDirectories(repo.resolve(".git/no-hooks"));
        fixtureGit(repo, "config", "user.name", "VacuousGuard selfcheck");
        fixtureGit(repo, "config", "user.email", "vacuousguard@invalid");
        fixtureGit(repo, "config", "commit.gpgsign", "false");
        fixtureGit(repo, "config", "merge.gpgsign", "false");
        fixtureGit(repo, "config", "core.hooksPath", ".git/no-hooks");

        Files.writeString(repo.resolve("probes/bench.sh"),
                "  judge BaseBlind 'VERDICT BASE_BLIND'\n", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("probes/BaseBlind.java"),
                "public final class BaseBlind {}\n", StandardCharsets.UTF_8);
        fixtureGit(repo, "add", "probes");
        fixtureGit(repo, "commit", "-q", "-m", "fixture: base census");

        Files.writeString(repo.resolve("main-note"), "tip\n", StandardCharsets.UTF_8);
        fixtureGit(repo, "add", "main-note");
        fixtureGit(repo, "commit", "-q", "-m", "fixture: main tip");
        String mainTip = git(repo, "rev-parse", "HEAD");

        fixtureGit(repo, "checkout", "-q", "-b", "topic");
        Files.writeString(repo.resolve("probes/bench.sh"),
                "  judge BaseBlind 'VERDICT BASE_BLIND'\n"
                        + "  judge NewBlind 'VERDICT NEW_BLIND'\n",
                StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("probes/NewBlind.java"),
                "public final class NewBlind {}\n", StandardCharsets.UTF_8);
        fixtureGit(repo, "add", "probes");
        fixtureGit(repo, "commit", "-q", "-m", "fixture: blind row grows");
        Files.writeString(repo.resolve("topic-note"), "tail\n", StandardCharsets.UTF_8);
        fixtureGit(repo, "add", "topic-note");
        fixtureGit(repo, "commit", "-q", "-m", "fixture: topic tail");
        String topic = git(repo, "rev-parse", "HEAD");

        fixtureGit(repo, "checkout", "-q", "main");
        Files.writeString(repo.resolve("main-later"), "advanced\n", StandardCharsets.UTF_8);
        fixtureGit(repo, "add", "main-later");
        fixtureGit(repo, "commit", "-q", "-m", "fixture: main advances");
        String mainAdvanced = git(repo, "rev-parse", "HEAD");
        fixtureGit(repo, "update-ref", "refs/remotes/origin/main", mainAdvanced);

        // A topic rebased onto the current local main: unlike `topic` above, its
        // merge-base is exactly origin/main and the +1 comparison is admissible.
        fixtureGit(repo, "checkout", "-q", "-b", "fresh-topic", mainAdvanced);
        Files.writeString(repo.resolve("probes/bench.sh"),
                "  judge BaseBlind 'VERDICT BASE_BLIND'\n"
                        + "  judge NewBlind 'VERDICT NEW_BLIND'\n",
                StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("probes/NewBlind.java"),
                "public final class NewBlind {}\n", StandardCharsets.UTF_8);
        fixtureGit(repo, "add", "probes");
        fixtureGit(repo, "commit", "-q", "-m", "fixture: fresh topic grows");
        String freshTopic = git(repo, "rev-parse", "HEAD");

        fixtureGit(repo, "checkout", "-q", "-b", "topic-merge", topic);
        fixtureGit(repo, "merge", "-q", "--no-ff", "main", "-m", "fixture: main into topic");
        String topicMerge = git(repo, "rev-parse", "HEAD");

        fixtureGit(repo, "checkout", "-q", "-b", "pr-checkout", mainAdvanced);
        fixtureGit(repo, "merge", "-q", "--no-ff", topic, "-m", "fixture: synthetic pr merge");
        String prMerge = git(repo, "rev-parse", "HEAD");
        return new HistoryFixture(repo, mainTip, topic, mainAdvanced, freshTopic,
                topicMerge, prMerge);
    }

    /** Exercise the real resolver and materializer, then the policy they feed. */
    private static boolean baselineCase(String name, Path root,
                                        String explicitTree, String explicitRef,
                                        String wantRef, int wantCurrent, int wantBase,
                                        String wantWord, int wantExit)
            throws IOException, InterruptedException {
        Census current = census(root);
        Baseline got = null;
        try {
            got = baseline(root, explicitTree, explicitRef);
        } catch (IOException ignored) {
            // Unreadable is an asserted result in the fail-closed cases below.
        }
        int baseCount = got == null ? -1 : got.census().unguarded().size();
        Policy policy = policy(current.unguarded().size(), baseCount,
                current.missing().isEmpty() && !current.rows().isEmpty(),
                got != null && got.census().missing().isEmpty() && !got.census().rows().isEmpty());
        boolean refOk = wantRef == null ? got == null : got != null && got.ref().equals(wantRef);
        String wantLine = "VERDICT " + wantWord
                + " growth=" + (wantWord.equals("VACUOUS_GUARD_GREW") ? 1 : 0)
                + " judged_none=0 baseline_none=" + (wantBase < 0 ? 1 : 0);
        boolean lineOk = policy.line().equals(wantLine);
        boolean ok = current.unguarded().size() == wantCurrent
                && baseCount == wantBase
                && policy.word().equals(wantWord)
                && policy.outcome().code() == wantExit
                && refOk && lineOk;
        System.out.printf("VACUOUS_BASECASE case=%-25s want=%-26s/%d got=%-26s/%d"
                        + " current=%d base=%s ref=%s line=%s %s%n",
                name, wantWord, wantExit, policy.word(), policy.outcome().code(),
                current.unguarded().size(), baseCount < 0 ? "unread" : String.valueOf(baseCount),
                refOk ? "OK" : "WRONG", lineOk ? "OK" : "WRONG", ok ? "OK" : "BROKEN");
        return ok;
    }

    /** Extract the exact supported evidence form: a Git archive with no .git directory. */
    private static void extractGitArchive(Path repo, String ref, Path target)
            throws IOException, InterruptedException {
        Files.createDirectories(target);
        ProcessBuilder archiveBuilder = gitProcess(repo, "archive", ref)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        ProcessBuilder extractBuilder = new ProcessBuilder("tar", "-x", "-C", target.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        // The host may export a locale its tar does not have; archive bytes need no locale.
        archiveBuilder.environment().put("LC_ALL", "C");
        archiveBuilder.environment().put("LANG", "C");
        extractBuilder.environment().put("LC_ALL", "C");
        extractBuilder.environment().put("LANG", "C");
        Process archive = archiveBuilder.start();
        Process extract = extractBuilder.start();
        try (var source = archive.getInputStream(); var sink = extract.getOutputStream()) {
            source.transferTo(sink);
        }
        int archiveExit = archive.waitFor();
        int extractExit = extract.waitFor();
        if (archiveExit != 0 || extractExit != 0 || Files.exists(target.resolve(".git"))) {
            throw new IOException("fixture archive extraction failed");
        }
    }

    /** Materialize one baseline file without checking out or mutating that tree. */
    private static boolean writeGitFile(Path root, String ref, String relative, Path target)
            throws IOException, InterruptedException {
        Process process = gitProcess(root, "show", ref + ":" + relative)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] content = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return true;
    }

    /** Temp trees contain only files this method wrote. Cleanup failure cannot alter a verdict. */
    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // The comparison already happened; an OS temp file is not a policy result.
        }
    }

    /** Pure policy kernel, retained in selfcheck below. */
    private static Policy policy(int current, int baseline,
                                 boolean currentReadable, boolean baselineReadable) {
        if (!currentReadable) {
            return new Policy("VACUOUS_GUARD_UNREAD", 0, 1, 0, Probes.Outcome.BROKE);
        }
        if (!baselineReadable) {
            return new Policy("VACUOUS_GUARD_UNCOMPARED", 0, 0, 1, Probes.Outcome.BROKE);
        }
        if (current > baseline) {
            return new Policy("VACUOUS_GUARD_GREW", 1, 0, 0, Probes.Outcome.BROKE);
        }
        return new Policy("VACUOUS_GUARD_COUNTED", 0, 0, 0, Probes.Outcome.HELD);
    }

    /**
     * Is this source's {@code NEVER_AROSE} reached because the population was EMPTY? (#1609)
     *
     * <p>The word guard asked whether the constant is anywhere in the file, and
     * {@code VacuousGuard}'s crown (#1541) named that as generous from the day it landed:
     * <em>it asks whether the constant is reachable in the file, not whether it is reachable
     * on the EMPTY path.</em> Six probes were counted as guarded by a rule that cannot tell
     * an empty-population guard from any other use of the constant.
     *
     * <p>The tighter question is decidable from the same text. The constant sits inside a
     * {@code Probes.leave(...)} call and the call sits under a branch, so this looks BACK
     * from the constant for a condition mentioning emptiness — {@code == 0},
     * {@code isEmpty()}, {@code < 1}. That separates <em>the population was empty</em> from
     * <em>something else happened</em>.
     *
     * <p>The window is {@value #EMPTY_WINDOW} lines, which is a number and therefore a
     * weakness: it is wide enough for every real guard here — the widest is
     * {@code BondScenario}, five lines from its {@code fired > 0} arms to the {@code else}
     * that follows them — and narrow enough that an unrelated condition elsewhere in the
     * method does not reach. Stated rather than tuned silently: it was four, and four
     * called {@code BondScenario} unguarded.
     *
     * <p><b>What it cannot do</b>: decide whether the branch is REACHABLE, or whether the
     * condition is the RIGHT one — {@code if (census == 1)} reads as a guard. The direction
     * of that error is unchanged and generous: it over-counts guards and under-counts
     * {@code unguarded=}, which is the safe side for a census (#1207) and the wrong side
     * for a gate.
     */
    private static boolean guardsTheEmptyPath(Path src) throws IOException {
        List<String> lines = List.of(Probes.uncommented(src).split("\n", -1));
        String constant = "Probes.Outcome." + "NEVER_" + "AROSE";
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains(constant)) {
                continue;
            }
            for (int back = Math.max(0, i - EMPTY_WINDOW); back <= i; back++) {
                String line = lines.get(back);
                // `> 0` counts because an `else` after it IS the empty branch for a count.
                // `BondScenario` writes exactly that — two `fired > 0` arms and an else
                // whose comment says *the run produced no firing* — and a rule reading only
                // `== 0` called a legitimate guard unguarded. `AllocMeter`'s `ranAt != 1` is
                // NOT in the list and must not be: its NEVER_AROSE is a refused
                // configuration, not an empty population, which is the case #1609 predicted
                // this rule was always going to get wrong.
                if (line.contains("== 0") || line.contains("isEmpty()")
                        || line.contains("< 1") || line.contains("> 0")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How far back a guard's condition may sit from the constant it protects (#1609). */
    private static final int EMPTY_WINDOW = 6;


    /**
     * The empty-path reader's own cases (#1611).
     *
     * <p>{@link #guardsTheEmptyPath} shipped with a FITTED number and no cases: the window
     * was set to four, run, found {@code BondScenario} reported unguarded, measured its gap
     * at five, and set to six. The javadoc says so, which does not make it principled —
     * and without cases, changing it is a guess against a live sweep.
     *
     * <p>With them the width is a thing somebody can move and see the effect of, which is
     * what {@code window-just-outside} is for: it sits one line beyond the window and must
     * read as unguarded, so widening the number breaks a case rather than quietly changing
     * a census.
     */
    private static int selfcheck(Path tmp) throws IOException, InterruptedException {
        int pass = 0;
        int fail = 0;
        String never = "Probes.Outcome." + "NEVER_" + "AROSE";

        String[][] cases = {
            // SameTick's shape: the simplest guard there is.
            {"guard-equals-zero", "if (census == 0) {\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // BondScenario's: two `> 0` arms and an else whose comment says the run produced
            // nothing. Five lines from condition to constant, which is what set the window.
            {"guard-else-after-positive",
                "} else if (tally.fired > 0 && tally.spent > 0) {\n  Probes.leave(\"H\", true);\n"
                        + "} else {\n  // nothing arose\n  int n = 0;\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // Accepted and written by nobody today.
            {"guard-is-empty", "if (rows.isEmpty()) {\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // AllocMeter's: a refused configuration, not an empty population.
            {"guard-refused-config", "if (ranAt != 1) {\n  Probes.leave(\"V\", " + never + ");\n}", "false"},
            {"guard-nothing-near", "Probes.leave(\"V\", " + never + ");", "false"},
            // THE FALSE POSITIVE THE LOOSE `> 0` INVITES, written down as a case rather
            // than as a sentence in a review: an unrelated condition inside the window.
            // Had AllocMeter's guard been `ranAt > 0`, this rule would call it guarded and
            // the finding #1609 exists for would have vanished.
            {"guard-unrelated-positive",
                "if (retries > 0) {\n  log();\n}\nif (ranAt != 1) {\n  Probes.leave(\"V\", " + never + ");\n}", "true"},
            // AND THE WINDOW ITSELF. Seven lines from condition to constant — one beyond
            // the six — so this must read as unguarded. Widening the number breaks THIS
            // case rather than quietly moving a census.
            {"window-just-outside",
                "if (census == 0) {\n  a();\n  b();\n  c();\n  d();\n  e();\n  f();\n"
                        + "  Probes.leave(\"V\", " + never + ");\n}", "false"},
        };

        for (String[] c : cases) {
            Path f = tmp.resolve(c[0] + ".java");
            Files.writeString(f, c[1], StandardCharsets.UTF_8);
            boolean got = guardsTheEmptyPath(f);
            boolean ok = String.valueOf(got).equals(c[2]);
            System.out.printf("VACUOUS case=%-26s want=%-7s got=%-7s %s%n",
                    c[0], c[2], got, ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // THE RATCHET'S OWN NEGATIVE PROOF (#1649). The live tree is normally equal
        // to or below its base, so without these cases replacing the growth predicate
        // with `false` leaves every ordinary lock green. Below and equal must leave 0;
        // the first row above the base must name GROWTH and leave 1. The two unread
        // paths are separate words because neither is a population regression.
        String[][] policyCases = {
            {"policy-below", "23", "24", "true", "true", "VACUOUS_GUARD_COUNTED", "0"},
            {"policy-equal", "24", "24", "true", "true", "VACUOUS_GUARD_COUNTED", "0"},
            {"policy-above", "25", "24", "true", "true", "VACUOUS_GUARD_GREW", "1"},
            {"policy-current-unread", "24", "24", "false", "true", "VACUOUS_GUARD_UNREAD", "1"},
            {"policy-base-unread", "24", "-1", "true", "false", "VACUOUS_GUARD_UNCOMPARED", "1"},
        };
        for (String[] c : policyCases) {
            Policy got = policy(Integer.parseInt(c[1]), Integer.parseInt(c[2]),
                    Boolean.parseBoolean(c[3]), Boolean.parseBoolean(c[4]));
            boolean ok = got.word().equals(c[5])
                    && got.outcome().code() == Integer.parseInt(c[6]);
            System.out.printf("VACUOUS_POLICY case=%-22s want=%-26s/%s got=%-26s/%d %s%n",
                    c[0], c[5], c[6], got.word(), got.outcome().code(), ok ? "OK" : "BROKEN");
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        // THE RESOLVER AND MATERIALIZER, NOT A PURE MODEL OF THEM. The first ratchet
        // suite exercised `policy` only; a named topic without origin/main therefore
        // fell back to HEAD^, and a local topic merge was mistaken for GitHub's merge
        // checkout. Both compared the synthetic +1 backlog (2) with itself (2) and
        // printed COUNTED. These fixtures commit that exact topology in a private repo.
        HistoryFixture history = historyFixture(tmp);
        Path repo = history.repo();
        List<Boolean> baselineCases = new ArrayList<>();

        fixtureGit(repo, "checkout", "-q", "main");
        baselineCases.add(baselineCase("named-main", repo,
                null, null, history.mainTip(), 1, 1, "VACUOUS_GUARD_COUNTED", 0));

        // Main may be exactly origin/main or one local commit whose first parent is it.
        // A different first parent is a renamed/diverged topic, not main evidence.
        fixtureGit(repo, "checkout", "-q", "-B", "main", history.prMerge());
        baselineCases.add(baselineCase("named-main-one-local", repo,
                null, null, history.mainAdvanced(), 2, 1, "VACUOUS_GUARD_GREW", 1));
        fixtureGit(repo, "checkout", "-q", "-B", "main", history.topicMerge());
        baselineCases.add(baselineCase("named-main-diverged", repo,
                null, null, null, 2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        fixtureGit(repo, "checkout", "-q", "-B", "main", history.mainAdvanced());

        // `topic` forked before main advanced. A stale merge-base cannot stand in for
        // the current local main; required hardline rebase is a prerequisite.
        fixtureGit(repo, "checkout", "-q", "topic");
        baselineCases.add(baselineCase("named-topic-stale", repo,
                null, null, null, 2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));

        fixtureGit(repo, "checkout", "-q", "fresh-topic");
        baselineCases.add(baselineCase("named-topic-fresh", repo,
                null, null, history.mainAdvanced(), 2, 1, "VACUOUS_GUARD_GREW", 1));

        // HEAD^ already contains NewBlind here. Removing origin/main must be unreadable,
        // not a silent 2-versus-2 comparison against that parent.
        fixtureGit(repo, "checkout", "-q", "topic");
        fixtureGit(repo, "update-ref", "-d", "refs/remotes/origin/main");
        baselineCases.add(baselineCase("topic-no-origin", repo,
                null, null, null, 2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        fixtureGit(repo, "update-ref", "refs/remotes/origin/main", history.mainAdvanced());

        // First parent is the topic (and already reads 2); merge-base with main reads 1.
        fixtureGit(repo, "checkout", "-q", "topic-merge");
        baselineCases.add(baselineCase("named-topic-merge", repo,
                null, null, history.mainAdvanced(), 2, 1, "VACUOUS_GUARD_GREW", 1));

        fixtureGit(repo, "checkout", "-q", "--detach", history.prMerge());
        baselineCases.add(baselineCase("detached-pr-merge", repo,
                null, null, history.mainAdvanced(), 2, 1, "VACUOUS_GUARD_GREW", 1));

        fixtureGit(repo, "checkout", "-q", "--detach", history.topicMerge());
        baselineCases.add(baselineCase("detached-topic-merge", repo,
                null, null, history.mainAdvanced(), 2, 1, "VACUOUS_GUARD_GREW", 1));

        fixtureGit(repo, "checkout", "-q", "--detach", history.mainAdvanced());
        baselineCases.add(baselineCase("detached-main-tip", repo,
                null, null, history.mainTip(), 1, 1, "VACUOUS_GUARD_COUNTED", 0));

        fixtureGit(repo, "checkout", "-q", "--detach", history.topic());
        baselineCases.add(baselineCase("detached-topic-stale", repo,
                null, null, null, 2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));

        fixtureGit(repo, "checkout", "-q", "--detach", history.mainTip());
        baselineCases.add(baselineCase("detached-main-ancestor", repo,
                null, null, null, 1, -1, "VACUOUS_GUARD_UNCOMPARED", 1));

        // THE ORIGINAL SHALLOW FAILURES AS CHECKOUTS, NOT ONLY ABSENT-REF MODELS.
        // Depth 1 main has no parent. Depth 2 topic contains the +1 commit and its
        // unchanged child but not origin/main: the old fallback compared 2 with 2.
        String fixtureUrl = repo.toUri().toASCIIString();
        Path shallowMain = tmp.resolve("shallow-main");
        fixtureGit(repo, "clone", "-q", "--depth", "1", "--single-branch",
                "--branch", "main", fixtureUrl, shallowMain.toString());
        baselineCases.add(baselineCase("shallow-depth1-main", shallowMain,
                null, null, null,
                1, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        Path shallowTopic = tmp.resolve("shallow-topic");
        fixtureGit(repo, "clone", "-q", "--depth", "2", "--single-branch",
                "--branch", "topic", fixtureUrl, shallowTopic.toString());
        baselineCases.add(baselineCase("shallow-depth2-topic", shallowTopic,
                null, null, null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));

        // A git archive cannot derive its base: two pinned trees are the complete input.
        // Extract the supported Ag9 form itself — not a hand-built lookalike. The ref is
        // identity for evidence; the separate tree supplies the bytes, and neither has
        // a .git directory from which the resolver could accidentally borrow an answer.
        Path headArchive = tmp.resolve("head-archive");
        Path baseArchive = tmp.resolve("base-archive");
        extractGitArchive(repo, history.topic(), headArchive);
        extractGitArchive(repo, history.mainTip(), baseArchive);
        baselineCases.add(baselineCase("archive-pair", headArchive,
                baseArchive.toString(), history.mainTip(), history.mainTip(),
                2, 1, "VACUOUS_GUARD_GREW", 1));
        baselineCases.add(baselineCase("archive-no-base", headArchive,
                null, null, null, 2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        baselineCases.add(baselineCase("archive-tree-only", headArchive,
                baseArchive.toString(), null, null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        baselineCases.add(baselineCase("archive-ref-only", headArchive,
                null, history.mainTip(), null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        baselineCases.add(baselineCase("archive-short-ref", headArchive,
                baseArchive.toString(), "deadbeef", null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        baselineCases.add(baselineCase("archive-base-is-checkout", headArchive,
                repo.toString(), history.mainTip(), null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        baselineCases.add(baselineCase("archive-base-is-head", headArchive,
                headArchive.toString(), history.mainTip(), null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));
        Path headArchiveAlias = tmp.resolve("head-archive-alias");
        Files.createSymbolicLink(headArchiveAlias, headArchive);
        baselineCases.add(baselineCase("archive-base-is-head-alias", headArchive,
                headArchiveAlias.toString(), history.mainTip(), null,
                2, -1, "VACUOUS_GUARD_UNCOMPARED", 1));

        // An inherited environment must not override a real checkout by pointing the
        // baseline tree at the head itself. The nested GIT_DIR case below exercises
        // this refusal and the selector sanitization together.
        fixtureGit(repo, "checkout", "-q", "topic");

        // Git repository selectors are inherited process environment, not evidence.
        // Without sanitizing them, an invalid GIT_DIR makes this live checkout look
        // gitless and the already-regressed head archive false-greens as its baseline.
        ProcessBuilder spoofBuilder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                VacuousGuard.class.getName(), repo.toString());
        spoofBuilder.environment().put("GIT_DIR", tmp.resolve("spoofed-missing.git").toString());
        spoofBuilder.environment().put("VACUOUS_BASELINE_TREE", headArchive.toString());
        spoofBuilder.environment().put("VACUOUS_BASELINE_REF", history.topic());
        Process spoof = spoofBuilder.redirectErrorStream(true).start();
        String spoofOutput = new String(spoof.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int spoofExit = spoof.waitFor();
        boolean spoofOk = spoofExit == 1
                && spoofOutput.contains("VACUOUS_BASELINE ref=unread unguarded=unread delta=unread")
                && spoofOutput.contains("VERDICT VACUOUS_GUARD_UNCOMPARED"
                        + " growth=0 judged_none=0 baseline_none=1");
        System.out.printf("VACUOUS_SUBPROCESS case=%-25s want=%-26s/%d got=%s/%d %s%n",
                "git-dir-spoof-refused", "VACUOUS_GUARD_UNCOMPARED", 1,
                spoofOk ? "VACUOUS_GUARD_UNCOMPARED" : "WRONG", spoofExit,
                spoofOk ? "OK" : "BROKEN");
        baselineCases.add(spoofOk);

        for (boolean ok : baselineCases) {
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }

        Probes.leave("VACUOUS SELFCHECK VERDICT " + (fail == 0 ? "READER_HOLDS" : "READER_BROKEN")
                + " cases=" + (pass + fail) + " failed=" + fail + " window=" + EMPTY_WINDOW,
                fail == 0 ? Probes.Outcome.HELD : Probes.Outcome.BROKE);
        return fail;
    }

    private VacuousGuard() {}
}
