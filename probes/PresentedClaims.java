import matrix.causal.CausalId;
import matrix.causal.CausalRecord;
import matrix.causal.PerceptInputs;
import matrix.causal.PerceptReceipts;
import matrix.realworld.MindReducer;
import matrix.realworld.MindState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Adversarial keeper for #1765's typed, visible, non-truth claim projection. */
public final class PresentedClaims {
    private static int cases, failures, sourceReds;

    public static void main(String[] args) throws Exception {
        matrix.Streams.utf8();
        // AN ARGUMENT DOOR OWES A REFUSAL (DoorRefusal, #1531).
        if (args.length != 0) {
            System.err.println("FATAL unknown argument: " + args[0]
                    + " (this probe takes no arguments)");
            System.exit(Probes.Outcome.REFUSED.code());
        }
        Path root = Path.of(".").toAbsolutePath().normalize();
        values();
        propagation();
        orderingAndBytes();
        hiddenTwins();
        sourceMutants(root);
        Probes.leave("VERDICT PRESENTED_CLAIMS_HELD cases=" + cases
                        + " cases_none=" + (cases == 0 ? 1 : 0)
                        + " claim_fail=" + failures + " source_red=" + sourceReds,
                cases > 0 && failures == 0 && sourceReds == 6);
    }

    private static void values() {
        CausalRecord.PresentedClaim legacy = legacy();
        check("legacy-explicit", legacy.claimClass()
                == CausalRecord.ClaimClass.LEGACY_UNCLASSIFIED);
        check("legacy-not-relatable", !legacy.relatable());
        check("legacy-exact-key",
                legacy.claim().key().value().equals("legacy-film.unclassified"));
        check("legacy-exact-position",
                legacy.position().key().value().equals("unclassified"));

        CausalRecord.PresentedClaim open = claim("door.state", "open");
        check("structured-relatable", open.relatable());
        check("structured-exact", open.claim().key().value().equals("door.state")
                && open.position().key().value().equals("open"));
        refused("legacy-class-structured-values", () -> new CausalRecord.PresentedClaim(
                CausalRecord.ClaimClass.LEGACY_UNCLASSIFIED,
                new CausalRecord.ClaimKey("door.state"),
                new CausalRecord.ClaimPosition("open")));
        refused("structured-exact-legacy-pair", () -> new CausalRecord.PresentedClaim(
                CausalRecord.ClaimClass.STRUCTURED,
                new CausalRecord.ClaimKey("legacy-film.unclassified"),
                new CausalRecord.ClaimPosition("unclassified")));
        refused("structured-legacy-key", () -> new CausalRecord.PresentedClaim(
                CausalRecord.ClaimClass.STRUCTURED,
                new CausalRecord.ClaimKey("legacy-film.unclassified"),
                new CausalRecord.ClaimPosition("open")));
        refused("structured-legacy-position", () -> new CausalRecord.PresentedClaim(
                CausalRecord.ClaimClass.STRUCTURED,
                new CausalRecord.ClaimKey("door.state"),
                new CausalRecord.ClaimPosition("unclassified")));
        refused("null-class", () -> new CausalRecord.PresentedClaim(null,
                open.claim(), open.position()));
        refused("null-key", () -> new CausalRecord.PresentedClaim(
                CausalRecord.ClaimClass.STRUCTURED, null, open.position()));
        refused("null-position", () -> new CausalRecord.PresentedClaim(
                CausalRecord.ClaimClass.STRUCTURED, open.claim(), null));
    }

    private static void propagation() {
        Fixture fixture = fixture("hidden-a", "sensor-a", 0);
        CausalRecord.PresentedClaim shown = claim("door.state", "open");
        PerceptReceipts.Presentation presentation = new PerceptReceipts.Presentation(
                new CausalId.Percept(12, 9), 731, shown);
        CausalRecord.ReceiptAudit projected = PerceptReceipts.project(
                fixture.attempt(), Optional.of(presentation)).orElseThrow();
        check("presentation-copied", projected.receipt().presentedClaim().equals(shown));
        check("projection-does-not-relabel", projected.receipt().presentedClaim() == shown);

        PerceptInputs.Allocation allocation = PerceptInputs.allocate(
                12, fixture.subject(), List.of(projected));
        CausalRecord.PerceptReceipt allocated = allocation.input().receipts().get(0);
        check("allocation-copied", allocated.presentedClaim().equals(shown));
        MindState reduced = MindReducer.reduce(MindState.initial(fixture.subject()),
                allocation.input());
        check("interpretation-copied", reduced.history().get(0).interpretation()
                .presentedClaim().equals(shown));
        check("interpretation-unresolved", reduced.history().get(0).interpretation().status()
                == MindState.EpistemicStatus.UNRESOLVED);

        PerceptReceipts.Presentation oldFilm = new PerceptReceipts.Presentation(
                new CausalId.Percept(12, 10), 0, legacy());
        CausalRecord.PerceptReceipt legacyReceipt = PerceptReceipts.project(
                fixture.attempt(), Optional.of(oldFilm)).orElseThrow().receipt();
        check("legacy-survives-explicitly", !legacyReceipt.presentedClaim().relatable());
    }

    private static void orderingAndBytes() {
        Fixture a = fixture("same-hidden", "sensor-a", 0);
        CausalRecord.PerceptReceipt base = receipt(a, 0,
                claim("door.state", "open"));
        CausalRecord.PerceptReceipt claimTwin = receipt(a, 0,
                claim("window.state", "open"));
        CausalRecord.PerceptReceipt positionTwin = receipt(a, 0,
                claim("door.state", "closed"));
        check("claim-only-not-equal", !base.equals(claimTwin)
                && base.compareTo(claimTwin) != 0);
        check("position-only-not-equal", !base.equals(positionTwin)
                && base.compareTo(positionTwin) != 0);
        check("tree-keeps-claim-twin",
                new java.util.TreeSet<>(List.of(base, claimTwin)).size() == 2);
        check("tree-keeps-position-twin",
                new java.util.TreeSet<>(List.of(base, positionTwin)).size() == 2);

        PerceptInputs.MindInput baseInput = new PerceptInputs.MindInput(
                12, a.subject(), List.of(base));
        PerceptInputs.MindInput claimInput = new PerceptInputs.MindInput(
                12, a.subject(), List.of(claimTwin));
        PerceptInputs.MindInput positionInput = new PerceptInputs.MindInput(
                12, a.subject(), List.of(positionTwin));
        check("claim-moves-canonical", !baseInput.canonical().equals(claimInput.canonical()));
        check("position-moves-canonical",
                !baseInput.canonical().equals(positionInput.canonical()));
        check("independent-mind-input-v2",
                baseInput.canonical().equals(independent(baseInput)));
        check("schema-v2", baseInput.canonical().startsWith("mind-input/2;"));
        check("claim-in-mind-bytes", contains(MindReducer.reduce(
                MindState.initial(a.subject()), baseInput).canonicalBytes(), "door.state"));
        check("position-in-mind-bytes", contains(MindReducer.reduce(
                MindState.initial(a.subject()), baseInput).canonicalBytes(), "open"));
    }

    private static void hiddenTwins() {
        Fixture first = fixture("truth-a", "sensor-a", 3);
        Fixture second = fixture("truth-b", "sensor-b", 91);
        CausalRecord.PresentedClaim fixed = claim("door.state", "open");
        PerceptReceipts.Presentation visible = new PerceptReceipts.Presentation(
                new CausalId.Percept(12, 7), 411, fixed);
        CausalRecord.ReceiptAudit left = PerceptReceipts.project(
                first.attempt(), Optional.of(visible)).orElseThrow();
        CausalRecord.ReceiptAudit right = PerceptReceipts.project(
                second.attempt(), Optional.of(visible)).orElseThrow();
        check("hidden-attempts-differ", !left.delivery().equals(right.delivery()));
        check("fixed-presentation-equal", left.receipt().equals(right.receipt()));
        PerceptInputs.Allocation leftIn = PerceptInputs.allocate(12, first.subject(), List.of(left));
        PerceptInputs.Allocation rightIn = PerceptInputs.allocate(12, second.subject(), List.of(right));
        check("hidden-input-bytes-equal", leftIn.input().canonical()
                .equals(rightIn.input().canonical()));
        check("hidden-mind-bytes-equal", Arrays.equals(
                MindReducer.reduce(MindState.initial(first.subject()), leftIn.input())
                        .canonicalBytes(),
                MindReducer.reduce(MindState.initial(second.subject()), rightIn.input())
                        .canonicalBytes()));
    }

    private static void sourceMutants(Path root) throws Exception {
        String needle = "visible.presentedClaim(),";
        mutantRed(root, "derive-hidden-truth", needle,
                "CausalRecord.PresentedClaim.structured("
                        + "attempt.truth().fact().predicate().value(), \"derived\"),");
        mutantRed(root, "parse-payload", needle,
                "CausalRecord.PresentedClaim.structured("
                        + "attempt.presentedContent().orElseThrow().text(), \"parsed\"),");
        mutantRed(root, "default-legacy", needle,
                "CausalRecord.PresentedClaim.legacyUnclassified(),");
        mutantRed(root, "null-claim-handoff", needle, "null,");
        mutantRed(root, "hidden-static-field", "public final class PerceptReceipts {",
                "public final class PerceptReceipts {\n"
                        + "    private static final CausalRecord.PresentedClaim HIDDEN = "
                        + "CausalRecord.PresentedClaim.legacyUnclassified();");
        mutantRed(root, "factory-system-property", "matrix/causal/CausalRecord.java",
                "new ClaimKey(claim), new ClaimPosition(position));",
                "new ClaimKey(claim), new ClaimPosition(System.getProperty("
                        + "\"matrix.claim.position\", position)));");
    }

    private static void mutantRed(Path root, String name, String needle,
                                  String replacement) throws Exception {
        mutantRed(root, name, "matrix/causal/PerceptReceipts.java", needle, replacement);
    }

    private static void mutantRed(Path root, String name, String relativeSource,
                                  String needle, String replacement) throws Exception {
        Path scratch = Files.createTempDirectory("presented-claim-mutant-");
        copyTree(root.resolve("src"), scratch.resolve("src"));
        Path mapper = scratch.resolve("src").resolve(relativeSource);
        String source = Files.readString(mapper, StandardCharsets.UTF_8);
        if (!source.contains(needle)) throw new IOException("mutant needle absent: " + name);
        Files.writeString(mapper, source.replace(needle, replacement), StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(javaCommand(), "-cp",
                System.getProperty("java.class.path"), "PerceptReceipts",
                "--root", scratch.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        boolean red = exit != 0 && output.contains("PERCEPT_RECEIPTS_BROKEN");
        cases++;
        if (red) {
            sourceReds++;
            System.out.println("PRESENTED_CLAIM_MUTANT_RED " + name);
        } else {
            failures++;
            System.out.println("FAIL source-mutant " + name + " exit=" + exit);
        }
    }

    private static String javaCommand() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Fixture fixture(String hidden, String actualKey, int sequence) {
        CausalRecord.Subject subject = new CausalRecord.Subject("human-7");
        CausalRecord.Principal human = new CausalRecord.Principal(
                CausalRecord.PrincipalKind.HUMAN, "human-7");
        CausalRecord.Principal actual = new CausalRecord.Principal(
                CausalRecord.PrincipalKind.MACHINE, actualKey);
        CausalRecord.Principal claimed = new CausalRecord.Principal(
                CausalRecord.PrincipalKind.SYSTEM, "claimed-system");
        CausalRecord.Payload shown = new CausalRecord.Payload("door looked open");
        CausalRecord.TruthEntry truth = new CausalRecord.TruthEntry(12, sequence, human,
                new CausalRecord.Fact(new CausalRecord.Symbol("fixture.fact"),
                        new CausalRecord.Payload(hidden)), actual);
        CausalRecord.DeliveryAttempt attempt = new CausalRecord.DeliveryAttempt(
                12, sequence, subject, CausalRecord.Channel.VISION, actual, claimed, truth,
                CausalRecord.Fidelity.PARTIAL, CausalRecord.DeliveryOutcome.DEGRADED,
                Optional.of(shown), CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                CausalRecord.DisclosureClass.AUDIT_DIVERGED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        return new Fixture(subject, attempt);
    }

    private static CausalRecord.PerceptReceipt receipt(
            Fixture fixture, int sequence, CausalRecord.PresentedClaim claim) {
        CausalRecord.DeliveryAttempt attempt = fixture.attempt();
        return new CausalRecord.PerceptReceipt(new CausalId.Percept(12, sequence),
                fixture.subject(), attempt.channel(), attempt.presentedContent().orElseThrow(),
                claim, attempt.declaredSource(), 411, attempt.fidelity());
    }

    private static CausalRecord.PresentedClaim claim(String key, String position) {
        return CausalRecord.PresentedClaim.structured(key, position);
    }

    private static CausalRecord.PresentedClaim legacy() {
        return CausalRecord.PresentedClaim.legacyUnclassified();
    }

    private static String independent(PerceptInputs.MindInput input) {
        StringBuilder out = new StringBuilder("mind-input/2;");
        number(out, input.tick()); word(out, input.subject().key().value());
        number(out, input.receipts().size());
        for (CausalRecord.PerceptReceipt receipt : input.receipts()) {
            word(out, receipt.id().canonical()); word(out, receipt.channel().name());
            word(out, receipt.content().text());
            word(out, receipt.perceivedSource().kind().name());
            word(out, receipt.perceivedSource().key().value());
            number(out, receipt.uncertaintyBasisPoints()); word(out, receipt.fidelity().name());
            word(out, receipt.presentedClaim().claimClass().name());
            word(out, receipt.presentedClaim().claim().key().value());
            word(out, receipt.presentedClaim().position().key().value());
        }
        return out.toString();
    }

    private static void word(StringBuilder out, String value) {
        out.append(value.getBytes(StandardCharsets.UTF_8).length)
                .append(':').append(value).append(';');
    }

    private static void number(StringBuilder out, long value) { out.append(value).append(';'); }

    private static boolean contains(byte[] bytes, String text) {
        return new String(bytes, StandardCharsets.UTF_8).contains(text);
    }

    private static void check(String name, boolean held) {
        cases++;
        if (!held) { failures++; System.out.println("FAIL " + name); }
    }

    private static void refused(String name, Runnable action) {
        cases++;
        try { action.run(); failures++; System.out.println("FAIL " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { }
    }

    private record Fixture(CausalRecord.Subject subject,
                           CausalRecord.DeliveryAttempt attempt) { }

    private PresentedClaims() { }
}
