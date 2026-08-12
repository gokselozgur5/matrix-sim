import matrix.Simulation;
import matrix.core.Digest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: did the control group move? The NEUTRAL lane's digest chain, held
 * against the sealed baseline <b>link by link</b>, in one verdict line.
 *
 * <p>The permanent-NEUTRAL ruling (#212) made the control group a fixture
 * rather than a scaffold, so the phase DoD's first clause — a NEUTRAL run
 * bit-identical to the seal — needs an instrument that outlives migration
 * week. This is it. Every v6.0 unit quotes its line as a merge condition and
 * the CI lane (#532) runs it on every push.
 *
 * <pre>
 *   java -cp out:probes/out NeutralDiff 6000
 *   java -cp out:probes/out NeutralDiff 6000 --seal /path/to/chain --seed 42
 *   java -cp out:probes/out NeutralDiff --selfcheck
 * </pre>
 *
 * <h2>What it prints</h2>
 *
 * Two facts, then exactly one verdict line, and on failure the first unequal
 * link between them:
 *
 * <pre>
 *   NEUTRALDIFF seal path=ci/fixtures/neutral-baseline.chain links=60 head=421d7263...
 *   NEUTRALDIFF lane seed=42 ticks=6000 links=60
 *   NEUTRALDIFF 60/60 byte-equal VERDICT PASS
 * </pre>
 *
 * The seal is named on its own line because a verdict that does not say what
 * it compared against is a claim, not evidence — and because the one way this
 * instrument can lie is by refereeing yesterday's fixture.
 *
 * <h2>First unequal link, not a count</h2>
 *
 * The failure line is the <b>first</b> divergence and nothing else:
 *
 * <pre>
 *   NEUTRALDIFF FAIL first_unequal_link tick=3000 expected=&lt;sha&gt; got=&lt;sha&gt;
 *   NEUTRALDIFF 29/60 byte-equal VERDICT FAIL
 * </pre>
 *
 * Every link after the first is that link's echo: the chain is a fold, so one
 * moved byte at tick 3,000 moves all thirty links after it, and "30 links
 * differ" reports the echo as if it were thirty findings.
 *
 * <h2>Ordering: the overlap is judged before the lengths</h2>
 *
 * A short or long seal is a real failure and gets its own line, but it is
 * checked <b>after</b> the links the two chains share. A seal truncated to 55
 * links whose link 30 also moved has two things wrong with it, and only one of
 * them says where the world changed; reporting the length first would hide the
 * divergence behind the bookkeeping. The CI lane's awk gets this right by
 * streaming; this probe has both chains in hand at once and so has to choose,
 * and it chooses the same way.
 *
 * <h2>The lane it runs</h2>
 *
 * The referee builds its own universe at the canonical seed, like every probe
 * (contract clause 2), and its chain is byte-equal to the one {@code
 * matrix.Main --headless --seed 42 --ticks 6000} emits — same {@code
 * Simulation}, same links, same order.
 *
 * <p>Today that is the only lane there is: nothing in {@code src/} consults a
 * NEUTRAL fence, so the flagged and unflagged worlds are the same world and
 * this probe refereeing the default construction <i>is</i> refereeing the
 * control group. That stops being true the first time a v6.0 unit puts a
 * coupling behind the fence, and on that day this probe must be routed through
 * the fence in the same PR. It is stated here rather than assumed because
 * nothing in this file can enforce it — a probe cannot check a branch that
 * does not exist yet.
 *
 * <h2>Exit codes</h2>
 *
 * <ul>
 *   <li>0 — the chains are byte-equal.</li>
 *   <li>1 — <b>the control group moved.</b> A finding about the world.</li>
 *   <li>2 — the seal is missing or unusable. A finding about the bench: no
 *       comparison happened, and a referee with no fixture must be red rather
 *       than vacuously green.</li>
 * </ul>
 *
 * The seal is read <b>before</b> the world is run, so a missing fixture costs
 * milliseconds instead of 6,000 ticks.
 *
 * <p>Read-only, own universe, streams pinned (contract clauses 1, 2 and 7).
 * Every line this probe prints is ASCII, so the pin changes none of its bytes
 * today — it is there because clause 7 binds the file and not the sentence,
 * and because a seal path echoed back in {@code seal_malformed} is text this
 * probe did not write and cannot promise is ASCII. The default seal path is
 * relative to the working directory, which the pinned form and {@code
 * probes/bench.sh} both make the repository root.
 */
public final class NeutralDiff {

    /** #528's fixture: the sealed chain the control group is held against. */
    static final String DEFAULT_SEAL = "ci/fixtures/neutral-baseline.chain";

    /** D-010's seed. The control group is refereed at the canonical one or not at all. */
    static final long CANONICAL_SEED = 42;

    private static final Pattern LINK = Pattern.compile("DIGEST tick=(\\d+) sha=([0-9a-f]{64})");

    /** How the two chains ended up related. Exactly one of these is true of any pair. */
    enum Kind { PASS, FIRST_UNEQUAL, RUN_OUTRAN_SEAL, SEAL_OUTRAN_RUN }

    /** One link, kept with the line it was written as: the comparison is on bytes. */
    record Link(long tick, String sha, String line) {}

    /**
     * The referee's finding. {@code equal} is the number of leading byte-equal
     * links; {@code accounted} is the number of links that had to be accounted
     * for, which is the longer of the two chains — so a 55-link seal against a
     * 60-link run reads 55/60 and not the vacuous 55/55.
     */
    record Verdict(Kind kind, int equal, int accounted, int sealLinks, int runLinks,
                   long sealedTick, long runTick, String expected, String got) {
        boolean pass() {
            return kind == Kind.PASS;
        }
    }

    /** A seal that cannot be read at all: carries the detail line it fails with. */
    static final class SealException extends RuntimeException {
        final String detail;

        SealException(String detail) {
            super(detail);
            this.detail = detail;
        }
    }

    public static void main(String[] args) throws IOException {
        matrix.Streams.utf8();
        if (args.length > 0 && "--selfcheck".equals(args[0])) {
            selfcheck();
            return;
        }

        long ticks = 6_000;
        long seed = CANONICAL_SEED;
        String sealPath = DEFAULT_SEAL;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--seal") && i + 1 < args.length) {
                sealPath = args[++i];
            } else if (a.equals("--seed") && i + 1 < args.length) {
                seed = Long.parseLong(args[++i]);
            } else if (a.matches("\\d+")) {
                ticks = Long.parseLong(a);          // a bare number is the tick budget
            } else {
                refuse("bad_argument text=\"" + a + "\"");
                return;
            }
        }

        List<Link> seal;
        try {
            Path path = Path.of(sealPath);
            if (!Files.exists(path)) {
                throw new SealException("seal_missing path=" + sealPath);
            }
            if (!Files.isRegularFile(path)) {
                throw new SealException("seal_not_a_file path=" + sealPath);
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // A seal that exists and will not read — permissions, a bad
                // byte under UTF-8, a vanished mount. Without this the probe
                // dies on a stack trace and the JVM exits 1, which is this
                // instrument's code for "the control group moved". Saying that
                // about a file it never managed to open is the worst lie it
                // has available.
                throw new SealException("seal_unreadable path=" + sealPath
                        + " reason=" + e.getClass().getSimpleName());
            }
            seal = parseSeal(lines, sealPath);
        } catch (SealException e) {
            refuse(e.detail);
            return;
        }
        System.out.println("NEUTRALDIFF seal path=" + sealPath + " links=" + seal.size()
                + " head=" + seal.get(seal.size() - 1).sha());

        List<Link> run = lane(seed, ticks);
        System.out.println("NEUTRALDIFF lane seed=" + seed + " ticks=" + ticks
                + " links=" + run.size());

        Verdict v = compare(seal, run);
        report(v);
        if (!v.pass()) {
            System.exit(1);
        }
    }

    /** The NEUTRAL lane's own chain, one private universe, canonical seed. */
    static List<Link> lane(long seed, long ticks) {
        List<Link> links = new ArrayList<>();
        for (Digest d : new Simulation(seed, null, null).run(ticks)) {
            links.add(new Link(d.tick(), d.sha256(), d.format()));
        }
        return links;
    }

    /**
     * The seal, parsed strictly. A fixture is data the build trusts absolutely,
     * so every line of it must be a chain link and the ticks must climb: a file
     * that has been regenerated and appended rather than replaced would
     * otherwise surface as a confusing length verdict instead of as the
     * corrupt seal it is.
     */
    static List<Link> parseSeal(List<String> lines, String path) {
        List<Link> links = new ArrayList<>();
        long previous = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = LINK.matcher(line);
            if (!m.matches()) {
                throw new SealException("seal_malformed path=" + path
                        + " line=" + (i + 1) + " text=\"" + line + "\"");
            }
            long tick = Long.parseLong(m.group(1));
            if (tick <= previous) {
                throw new SealException("seal_out_of_order path=" + path
                        + " line=" + (i + 1) + " tick=" + tick + " previous_tick=" + previous);
            }
            previous = tick;
            links.add(new Link(tick, m.group(2), line));
        }
        if (links.isEmpty()) {
            throw new SealException("seal_empty path=" + path);
        }
        return links;
    }

    /**
     * The referee. Byte-equality on the whole line, not on the sha alone — a
     * seal written at a different cadence carries the right hashes at the wrong
     * ticks, and that is a divergence too.
     */
    static Verdict compare(List<Link> seal, List<Link> run) {
        int overlap = Math.min(seal.size(), run.size());
        int accounted = Math.max(seal.size(), run.size());
        for (int i = 0; i < overlap; i++) {
            Link s = seal.get(i);
            Link r = run.get(i);
            if (!s.line().equals(r.line())) {
                return new Verdict(Kind.FIRST_UNEQUAL, i, accounted, seal.size(), run.size(),
                        s.tick(), r.tick(), s.sha(), r.sha());
            }
        }
        if (run.size() > overlap) {
            Link r = run.get(overlap);
            return new Verdict(Kind.RUN_OUTRAN_SEAL, overlap, accounted, seal.size(), run.size(),
                    -1, r.tick(), "-", r.sha());
        }
        if (seal.size() > overlap) {
            Link s = seal.get(overlap);
            return new Verdict(Kind.SEAL_OUTRAN_RUN, overlap, accounted, seal.size(), run.size(),
                    s.tick(), -1, s.sha(), "-");
        }
        return new Verdict(Kind.PASS, overlap, accounted, seal.size(), run.size(), -1, -1, "-", "-");
    }

    /** The finding, then the one verdict line. The verdict is always last. */
    private static void report(Verdict v) {
        switch (v.kind()) {
            case FIRST_UNEQUAL -> System.out.println("NEUTRALDIFF FAIL first_unequal_link"
                    + " tick=" + v.sealedTick()
                    + " expected=" + v.expected()
                    + " got=" + v.got()
                    // Appended, never inserted (D-020): the sealed and run ticks
                    // agree on every chain written at the same cadence, and the
                    // field shows up only on the seal that was not.
                    + (v.runTick() == v.sealedTick() ? "" : " run_tick=" + v.runTick()));
            case RUN_OUTRAN_SEAL -> System.out.println("NEUTRALDIFF FAIL run_outran_seal"
                    + " seal_links=" + v.sealLinks() + " run_links=" + v.runLinks()
                    + " first_unsealed_tick=" + v.runTick());
            case SEAL_OUTRAN_RUN -> System.out.println("NEUTRALDIFF FAIL seal_outran_run"
                    + " seal_links=" + v.sealLinks() + " run_links=" + v.runLinks()
                    + " first_unrun_tick=" + v.sealedTick());
            case PASS -> { }
        }
        System.out.println("NEUTRALDIFF " + v.equal() + "/" + v.accounted()
                + " byte-equal VERDICT " + (v.pass() ? "PASS" : "FAIL"));
    }

    /** No comparison happened. Say which, say so in the verdict, exit 2. */
    private static void refuse(String detail) {
        System.out.println("NEUTRALDIFF FAIL " + detail);
        System.out.println("NEUTRALDIFF no_comparison VERDICT FAIL");
        System.exit(2);
    }

    // -----------------------------------------------------------------------
    // The referee's own referee.
    //
    // Every branch above except PASS is unreachable on a green tree: the seal
    // matches, so no run this repository makes ever prints a FAIL line, and an
    // instrument whose failure paths are never executed is a promise rather
    // than a lock. #871's lane found this the hard way in awk — its
    // short-seal diagnostic named a blank line as the thing the run failed to
    // match, and only a deliberately truncated fixture showed it.
    //
    // So the comparison is exercised here against hand-written chains, with no
    // universe, no seed and no ticks. The expected summary of each case is
    // written out as a literal; the probe computes the other side.
    // -----------------------------------------------------------------------

    private static void selfcheck() {
        String a = "a".repeat(64);
        String b = "b".repeat(64);
        String c = "c".repeat(64);
        String d = "d".repeat(64);

        boolean ok = true;
        ok &= compareCase("identical",
                List.of(link(100, a), link(200, b), link(300, c)),
                List.of(link(100, a), link(200, b), link(300, c)),
                "PASS 3/3");
        ok &= compareCase("doctored_middle_link",
                List.of(link(100, a), link(200, b), link(300, c)),
                List.of(link(100, a), link(200, d), link(300, c)),
                "FIRST_UNEQUAL tick=200 run_tick=200 expected=bbbb got=dddd 1/3");
        ok &= compareCase("run_outran_seal",
                List.of(link(100, a), link(200, b)),
                List.of(link(100, a), link(200, b), link(300, c)),
                "RUN_OUTRAN_SEAL first_unsealed_tick=300 2/3");
        ok &= compareCase("seal_outran_run",
                List.of(link(100, a), link(200, b), link(300, c)),
                List.of(link(100, a), link(200, b)),
                "SEAL_OUTRAN_RUN first_unrun_tick=300 2/3");
        // The ordering lock: both cases have a length mismatch AND an earlier
        // moved link, and the moved link is the one that carries information.
        ok &= compareCase("unequal_link_beats_short_seal",
                List.of(link(100, a), link(200, b)),
                List.of(link(100, d), link(200, b), link(300, c)),
                "FIRST_UNEQUAL tick=100 run_tick=100 expected=aaaa got=dddd 0/3");
        ok &= compareCase("unequal_link_beats_long_seal",
                List.of(link(100, a), link(200, b), link(300, c)),
                List.of(link(100, d), link(200, b)),
                "FIRST_UNEQUAL tick=100 run_tick=100 expected=aaaa got=dddd 0/3");
        // Same hashes, different ticks: equal shas are not an equal chain.
        ok &= compareCase("cadence_shift",
                List.of(link(200, a), link(400, b)),
                List.of(link(100, a), link(200, b)),
                "FIRST_UNEQUAL tick=200 run_tick=100 expected=aaaa got=aaaa 0/2");

        ok &= parseCase("seal_parses", List.of("DIGEST tick=100 sha=" + a,
                "DIGEST tick=200 sha=" + b), "links=2");
        ok &= parseCase("seal_malformed", List.of("DIGEST tick=100 sha=" + a, "CHAIN links=1"),
                "seal_malformed path=- line=2 text=\"CHAIN links=1\"");
        ok &= parseCase("seal_short_sha", List.of("DIGEST tick=100 sha=abcdef"),
                "seal_malformed path=- line=1 text=\"DIGEST tick=100 sha=abcdef\"");
        ok &= parseCase("seal_out_of_order", List.of("DIGEST tick=200 sha=" + a,
                "DIGEST tick=100 sha=" + b),
                "seal_out_of_order path=- line=2 tick=100 previous_tick=200");
        ok &= parseCase("seal_empty", List.of(), "seal_empty path=-");

        System.out.println("SELFCHECK cases=12 universes=0 ticks=0");
        System.out.println(ok ? "SELFCHECK VERDICT REFEREE_HOLDS"
                : "SELFCHECK VERDICT REFEREE_BROKEN");
        if (!ok) {
            System.exit(1);
        }
    }

    private static boolean compareCase(String name, List<Link> seal, List<Link> run, String want) {
        String got = summary(compare(seal, run));
        return caseLine("compare", name, want, got);
    }

    private static boolean parseCase(String name, List<String> lines, String want) {
        String got;
        try {
            got = "links=" + parseSeal(lines, "-").size();
        } catch (SealException e) {
            got = e.detail;
        }
        return caseLine("parse", name, want, got);
    }

    private static boolean caseLine(String leg, String name, String want, String got) {
        boolean ok = want.equals(got);
        // Single quotes on the outside: three of these cases carry a detail
        // line that quotes the offending seal text with double quotes of its
        // own, and a line that nests one inside the other reads as neither.
        System.out.println("SELFCHECK " + leg + " case=" + name
                + " want='" + want + "' got='" + got + "' " + (ok ? "OK" : "MISMATCH"));
        return ok;
    }

    /** A verdict as one short string, so a case's expectation can be a literal. */
    private static String summary(Verdict v) {
        return switch (v.kind()) {
            case PASS -> "PASS " + v.equal() + "/" + v.accounted();
            case FIRST_UNEQUAL -> "FIRST_UNEQUAL tick=" + v.sealedTick()
                    + " run_tick=" + v.runTick()
                    + " expected=" + v.expected().substring(0, 4)
                    + " got=" + v.got().substring(0, 4)
                    + " " + v.equal() + "/" + v.accounted();
            case RUN_OUTRAN_SEAL -> "RUN_OUTRAN_SEAL first_unsealed_tick=" + v.runTick()
                    + " " + v.equal() + "/" + v.accounted();
            case SEAL_OUTRAN_RUN -> "SEAL_OUTRAN_RUN first_unrun_tick=" + v.sealedTick()
                    + " " + v.equal() + "/" + v.accounted();
        };
    }

    private static Link link(long tick, String sha) {
        return new Link(tick, sha, "DIGEST tick=" + tick + " sha=" + sha);
    }

    private NeutralDiff() {}
}
