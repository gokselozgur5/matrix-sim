import matrix.causal.CausalId;
import matrix.causal.CausalRecord;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Probe: is D-066's cross-world record grammar immutable and authority-safe?
 *
 * <p>No universe is booted. The probe constructs every carrier, compares its
 * fixed route with an independent expected table, attacks every constructor
 * boundary, mutates caller-owned lists after publication, and reflects over
 * every record component for mutable/object/world/mind references. It also
 * constructs two different hidden audits for the same visible receipt: the
 * root may distinguish them, while the mind projection remains the same value.
 *
 * <p>Usage: {@code java -cp out:probes/out CausalRecords}. It accepts no
 * arguments, exits 1 on a broken invariant, and spends the common refusal code
 * when invoked with a flag.
 */
public final class CausalRecords {

    private record Expected(CausalRecord.Owner owner,
                            CausalRecord.Boundary producer,
                            List<CausalRecord.Boundary> consumers,
                            CausalRecord.Exposure exposure) {}

    private record Fixture(CausalRecord.TruthEntry truth,
                           CausalRecord.DeliveryAttempt delivery,
                           CausalRecord.PerceptReceipt receipt,
                           CausalRecord.ReceiptAudit audit,
                           CausalRecord.IntentProposal intent,
                           CausalRecord.IntentValidation validation,
                           CausalRecord.CommittedAction action,
                           CausalRecord.Effect effect,
                           CausalRecord.SettlementEntry settlement) {
        List<CausalRecord> records() {
            return List.of(truth, delivery, receipt, audit, intent,
                    validation, action, effect, settlement);
        }
    }

    private static final Map<CausalRecord.Kind, Expected> EXPECTED = Map.ofEntries(
            Map.entry(CausalRecord.Kind.TRUTH_ENTRY,
                    expected(CausalRecord.Owner.SIMULATION_ROOT,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Exposure.ROOT_ONLY,
                            CausalRecord.Boundary.SIMULATION_ROOT)),
            Map.entry(CausalRecord.Kind.DELIVERY_ATTEMPT,
                    expected(CausalRecord.Owner.SIMULATION_ROOT,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Exposure.ROOT_ONLY,
                            CausalRecord.Boundary.SIMULATION_ROOT)),
            Map.entry(CausalRecord.Kind.PERCEPT_RECEIPT,
                    expected(CausalRecord.Owner.HUMAN_MIND,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Exposure.MIND_VISIBLE,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Boundary.HUMAN_MIND)),
            Map.entry(CausalRecord.Kind.RECEIPT_AUDIT,
                    expected(CausalRecord.Owner.SIMULATION_ROOT,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Exposure.ROOT_ONLY,
                            CausalRecord.Boundary.SIMULATION_ROOT)),
            Map.entry(CausalRecord.Kind.INTENT_PROPOSAL,
                    expected(CausalRecord.Owner.HUMAN_MIND,
                            CausalRecord.Boundary.HUMAN_MIND,
                            CausalRecord.Exposure.ROOT_INPUT,
                            CausalRecord.Boundary.SIMULATION_ROOT)),
            Map.entry(CausalRecord.Kind.INTENT_VALIDATION,
                    expected(CausalRecord.Owner.SIMULATION_ROOT,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Exposure.ROOT_ONLY,
                            CausalRecord.Boundary.SIMULATION_ROOT)),
            Map.entry(CausalRecord.Kind.COMMITTED_ACTION,
                    expected(CausalRecord.Owner.SIMULATION_ROOT,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Exposure.CANONICAL_COMMAND,
                            CausalRecord.Boundary.CANONICAL_STATE)),
            Map.entry(CausalRecord.Kind.EFFECT,
                    expected(CausalRecord.Owner.CANONICAL_STATE,
                            CausalRecord.Boundary.CANONICAL_STATE,
                            CausalRecord.Exposure.SETTLEMENT_INPUT,
                            CausalRecord.Boundary.SIMULATION_ROOT,
                            CausalRecord.Boundary.SETTLEMENT_LEDGER)),
            Map.entry(CausalRecord.Kind.SETTLEMENT_ENTRY,
                    expected(CausalRecord.Owner.SETTLEMENT_LEDGER,
                            CausalRecord.Boundary.SETTLEMENT_LEDGER,
                            CausalRecord.Exposure.ROOT_ONLY,
                            CausalRecord.Boundary.SIMULATION_ROOT)));

    private static final List<Class<? extends CausalRecord>> CARRIERS = List.of(
            CausalRecord.TruthEntry.class,
            CausalRecord.DeliveryAttempt.class,
            CausalRecord.PerceptReceipt.class,
            CausalRecord.ReceiptAudit.class,
            CausalRecord.IntentProposal.class,
            CausalRecord.IntentValidation.class,
            CausalRecord.CommittedAction.class,
            CausalRecord.Effect.class,
            CausalRecord.SettlementEntry.class);

    private static final Set<String> FORBIDDEN_TYPES = Set.of(
            "matrix.core.World",
            "matrix.realworld.Brain",
            "matrix.realworld.Human",
            "matrix.entities.Avatar");

    private static final Map<String, Integer> CASES = new LinkedHashMap<>();
    private static final Map<String, Integer> FAILURES = new LinkedHashMap<>();
    private static final List<String> BREAKS = new ArrayList<>();

    public static void main(String[] args) {
        matrix.Streams.utf8();
        if (args.length != 0) {
            System.err.println("CausalRecords takes no arguments");
            System.exit(Probes.Outcome.REFUSED.code());
        }

        Fixture fixture = fixture();
        routeAndRoster(fixture);
        visibleAuditSplit(fixture);
        immutability(fixture);
        constructorRefusals(fixture);
        componentFence(fixture);

        BREAKS.forEach(System.out::println);
        int cases = CASES.values().stream().mapToInt(Integer::intValue).sum();
        int failures = FAILURES.values().stream().mapToInt(Integer::intValue).sum();
        int kindsMissing = symmetricDifference(
                new LinkedHashSet<>(EXPECTED.keySet()),
                new LinkedHashSet<>(Arrays.asList(CausalRecord.Kind.values()))).size();

        System.out.printf(Locale.ROOT,
                "CAUSAL_RECORD_CENSUS cases=%d route=%d owner=%d visibility=%d"
                        + " immutable=%d constructor=%d forbidden=%d%n",
                cases, cases("route"), cases("owner"), cases("visibility"),
                cases("immutable"), cases("constructor"), cases("forbidden"));
        Probes.leave(String.format(Locale.ROOT,
                "VERDICT %s kinds=9 kinds_missing=%d route_fail=%d owner_fail=%d"
                        + " visibility_fail=%d immutable_fail=%d constructor_fail=%d"
                        + " forbidden_ref=%d cases_none=%d",
                failures == 0 && kindsMissing == 0 && cases > 0
                        ? "CAUSAL_RECORDS_HELD" : "CAUSAL_RECORDS_BROKEN",
                kindsMissing, failures("route"), failures("owner"),
                failures("visibility"), failures("immutable"),
                failures("constructor"), failures("forbidden"), cases == 0 ? 1 : 0),
                failures == 0 && kindsMissing == 0 && cases > 0);
    }

    private static Expected expected(CausalRecord.Owner owner,
                                     CausalRecord.Boundary producer,
                                     CausalRecord.Exposure exposure,
                                     CausalRecord.Boundary... consumers) {
        return new Expected(owner, producer, List.of(consumers), exposure);
    }

    private static Fixture fixture() {
        CausalRecord.Subject neo = new CausalRecord.Subject("neo");
        CausalRecord.Subject trinity = new CausalRecord.Subject("trinity");
        CausalRecord.Principal door = principal(CausalRecord.PrincipalKind.PLACE, "door");
        CausalRecord.Principal matrix = principal(CausalRecord.PrincipalKind.SYSTEM, "matrix");
        CausalRecord.Principal oracle = principal(CausalRecord.PrincipalKind.SYSTEM, "oracle");
        CausalRecord.Principal neoOwner = principal(CausalRecord.PrincipalKind.HUMAN, "neo");

        CausalRecord.Payload visible = payload("open");
        CausalRecord.TruthEntry truth = new CausalRecord.TruthEntry(10, 0, neoOwner,
                new CausalRecord.Fact(symbol("door.state"), payload("open")), matrix);
        CausalRecord.DeliveryAttempt delivery = new CausalRecord.DeliveryAttempt(
                10, 0, neo, CausalRecord.Channel.VISION, matrix, matrix, truth,
                CausalRecord.Fidelity.FULL, CausalRecord.DeliveryOutcome.DELIVERED,
                Optional.of(visible), CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                CausalRecord.DisclosureClass.AUDIT_MATCHED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        CausalRecord.PerceptReceipt receipt = new CausalRecord.PerceptReceipt(
                new CausalId.Percept(10, 0), neo, CausalRecord.Channel.VISION,
                visible, CausalRecord.PresentedClaim.structured("door.state", "open"),
                matrix, 250, CausalRecord.Fidelity.FULL);
        CausalRecord.ReceiptAudit audit = new CausalRecord.ReceiptAudit(receipt, delivery);
        CausalRecord.IntentProposal intent = new CausalRecord.IntentProposal(
                new CausalId.Intent(10, 0), new CausalId.Choice(10, 0), neo,
                symbol("escape"), symbol("open_door"), door,
                List.of(receipt.ref()), List.of());
        CausalRecord.IntentValidation validation = new CausalRecord.IntentValidation(
                intent.id(), neo, CausalRecord.ValidationOutcome.ACCEPTED,
                symbol("door.capability"), Optional.empty());
        CausalRecord.CommittedAction action = new CausalRecord.CommittedAction(
                new CausalId.Commit(10, 0), intent.id(), intent.choice(), neo,
                intent.requestedAct(), door, validation.rule());
        CausalRecord.Effect effect = new CausalRecord.Effect(
                new CausalId.Effect(10, 0),
                new CausalRecord.ChoiceOrigin(neo, intent.choice(), action.id(),
                        action.validatingRule()),
                symbol("door.opened"),
                List.of(
                        new CausalRecord.Participation(neo,
                                CausalRecord.ParticipationRole.ACTOR, Optional.empty()),
                        new CausalRecord.Participation(trinity,
                                CausalRecord.ParticipationRole.AFFECTED_WITHOUT_CHOICE,
                                Optional.empty())));
        CausalRecord.SettlementEntry settlement = new CausalRecord.SettlementEntry(
                effect.id(), CausalRecord.SettlementDomain.BIOGRAPHY, neoOwner,
                List.of(neo), symbol("door_opened"), payload("true"));
        return new Fixture(truth, delivery, receipt, audit, intent,
                validation, action, effect, settlement);
    }

    private static void routeAndRoster(Fixture fixture) {
        List<CausalRecord> records = fixture.records();
        check("route", "one-fixture-per-kind", records.size() == EXPECTED.size());
        Set<CausalRecord.Kind> seen = new LinkedHashSet<>();
        for (CausalRecord record : records) {
            seen.add(record.kind());
            Expected expected = EXPECTED.get(record.kind());
            CausalRecord.Contract actual = record.contract();
            check("route", record.kind() + "-known", expected != null);
            if (expected != null) {
                check("owner", record.kind() + "-owner", actual.owner() == expected.owner());
                check("route", record.kind() + "-producer",
                        actual.producer() == expected.producer());
                check("route", record.kind() + "-consumers",
                        actual.consumers().equals(expected.consumers()));
                check("visibility", record.kind() + "-exposure",
                        actual.exposure() == expected.exposure());
            }
            check("immutable", record.kind() + "-record", record.getClass().isRecord());
            check("immutable", record.kind() + "-final",
                    Modifier.isFinal(record.getClass().getModifiers()));
        }
        check("route", "all-kinds-seen", seen.equals(EXPECTED.keySet()));
        check("route", "all-carriers-permitted",
                new LinkedHashSet<>(Arrays.asList(CausalRecord.class.getPermittedSubclasses()))
                        .equals(new LinkedHashSet<>(CARRIERS)));

        long mindVisible = records.stream()
                .filter(r -> r.contract().exposure() == CausalRecord.Exposure.MIND_VISIBLE)
                .count();
        check("visibility", "one-mind-visible-kind", mindVisible == 1);
        check("visibility", "receipt-is-mind-visible",
                fixture.receipt.contract().exposure() == CausalRecord.Exposure.MIND_VISIBLE);
        check("visibility", "audit-is-root-only",
                fixture.audit.contract().exposure() == CausalRecord.Exposure.ROOT_ONLY);
        check("visibility", "intent-is-not-an-effect",
                fixture.intent.contract().exposure() == CausalRecord.Exposure.ROOT_INPUT);
        check("visibility", "action-is-only-command",
                records.stream().filter(r -> r.contract().exposure()
                        == CausalRecord.Exposure.CANONICAL_COMMAND).count() == 1);
    }

    private static void visibleAuditSplit(Fixture fixture) {
        List<String> receiptFields = Arrays.stream(
                        CausalRecord.PerceptReceipt.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        check("visibility", "receipt-exact-fields", receiptFields.equals(List.of(
                "id", "subject", "channel", "content", "presentedClaim", "perceivedSource",
                "uncertaintyBasisPoints", "fidelity")));
        for (String forbidden : List.of("actual", "truth", "audit", "provenance",
                "delivery", "outcome")) {
            check("visibility", "receipt-omits-" + forbidden,
                    receiptFields.stream().noneMatch(name -> name.contains(forbidden)));
        }

        CausalRecord.Principal sourceA = principal(CausalRecord.PrincipalKind.SYSTEM, "sensor_a");
        CausalRecord.Principal provenanceB = principal(CausalRecord.PrincipalKind.SYSTEM, "sensor_b");
        CausalRecord.TruthEntry hiddenA = new CausalRecord.TruthEntry(10, 0,
                principal(CausalRecord.PrincipalKind.HUMAN, "neo"),
                new CausalRecord.Fact(symbol("door.state"), payload("sealed")), sourceA);
        CausalRecord.TruthEntry hiddenB = new CausalRecord.TruthEntry(10, 1,
                principal(CausalRecord.PrincipalKind.HUMAN, "neo"),
                new CausalRecord.Fact(symbol("door.state"), payload("closed")), provenanceB);
        CausalRecord.DeliveryAttempt deliveryA = new CausalRecord.DeliveryAttempt(
                10, 0, fixture.receipt.subject(), fixture.receipt.channel(), sourceA,
                fixture.receipt.perceivedSource(), hiddenA, CausalRecord.Fidelity.PARTIAL,
                CausalRecord.DeliveryOutcome.DEGRADED,
                Optional.of(fixture.receipt.content()),
                CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                CausalRecord.DisclosureClass.AUDIT_DIVERGED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        CausalRecord.DeliveryAttempt deliveryB = new CausalRecord.DeliveryAttempt(
                10, 1, fixture.receipt.subject(), fixture.receipt.channel(), provenanceB,
                fixture.receipt.perceivedSource(), hiddenB, CausalRecord.Fidelity.PARTIAL,
                CausalRecord.DeliveryOutcome.DEGRADED,
                Optional.of(fixture.receipt.content()),
                CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                CausalRecord.DisclosureClass.AUDIT_DIVERGED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        CausalRecord.PerceptReceipt partialReceipt = new CausalRecord.PerceptReceipt(
                fixture.receipt.id(), fixture.receipt.subject(), fixture.receipt.channel(),
                fixture.receipt.content(), fixture.receipt.presentedClaim(),
                fixture.receipt.perceivedSource(), 250,
                CausalRecord.Fidelity.PARTIAL);
        CausalRecord.ReceiptAudit auditA = new CausalRecord.ReceiptAudit(
                partialReceipt, deliveryA);
        CausalRecord.ReceiptAudit auditB = new CausalRecord.ReceiptAudit(
                partialReceipt, deliveryB);
        check("visibility", "hidden-audits-differ",
                !auditA.delivery().equals(auditB.delivery()));
        check("visibility", "visible-receipt-identical",
                auditA.receipt().equals(auditB.receipt()));
        check("visibility", "visible-order-identical",
                auditA.receipt().compareTo(auditB.receipt()) == 0);

        CausalRecord.DeliveryAttempt occluded = new CausalRecord.DeliveryAttempt(
                10, 0, fixture.receipt.subject(), fixture.receipt.channel(),
                fixture.delivery.actualSource(), fixture.delivery.declaredSource(),
                fixture.truth, CausalRecord.Fidelity.NONE,
                CausalRecord.DeliveryOutcome.OCCLUDED, Optional.empty(),
                CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                CausalRecord.AuthorityClass.UNESTABLISHED,
                CausalRecord.ConsentClass.UNESTABLISHED,
                CausalRecord.DisclosureClass.NOT_PRESENTED,
                CausalRecord.ConstraintClass.NO_EVIDENCE,
                CausalRecord.ObligationClass.NONE_CITED);
        rejects("constructor", "occluded-cannot-issue-receipt",
                () -> new CausalRecord.ReceiptAudit(fixture.receipt, occluded));
        rejects("constructor", "receipt-content-must-match-audit",
                () -> new CausalRecord.ReceiptAudit(
                        new CausalRecord.PerceptReceipt(fixture.receipt.id(),
                                fixture.receipt.subject(), fixture.receipt.channel(),
                                payload("different"), fixture.receipt.presentedClaim(),
                                fixture.receipt.perceivedSource(), 0,
                                fixture.receipt.fidelity()),
                        fixture.delivery));
        rejects("constructor", "receipt-source-must-match-claim",
                () -> new CausalRecord.ReceiptAudit(
                        new CausalRecord.PerceptReceipt(fixture.receipt.id(),
                                fixture.receipt.subject(), fixture.receipt.channel(),
                                fixture.receipt.content(), fixture.receipt.presentedClaim(),
                                CausalRecord.Principal.unknown(), 0,
                                fixture.receipt.fidelity()),
                        fixture.delivery));
        rejects("constructor", "receipt-fidelity-must-match-audit",
                () -> new CausalRecord.ReceiptAudit(
                        new CausalRecord.PerceptReceipt(fixture.receipt.id(),
                                fixture.receipt.subject(), fixture.receipt.channel(),
                                fixture.receipt.content(), fixture.receipt.presentedClaim(),
                                fixture.receipt.perceivedSource(), 0,
                                CausalRecord.Fidelity.PARTIAL), fixture.delivery));
    }

    private static void immutability(Fixture fixture) {
        ArrayList<CausalRecord.PerceptRef> receiptBasis = new ArrayList<>();
        receiptBasis.add(new CausalRecord.PerceptRef(
                fixture.receipt.subject(), new CausalId.Percept(1, 0)));
        ArrayList<CausalRecord.MemoryRef> memoryBasis = new ArrayList<>();
        memoryBasis.add(new CausalRecord.MemoryRef(fixture.receipt.subject(), 1, 0));
        CausalRecord.IntentProposal intent = new CausalRecord.IntentProposal(
                new CausalId.Intent(2, 0), new CausalId.Choice(2, 0),
                fixture.receipt.subject(), symbol("remember"), symbol("wait"),
                fixture.delivery.truth().subject(), receiptBasis, memoryBasis);
        receiptBasis.add(new CausalRecord.PerceptRef(
                fixture.receipt.subject(), new CausalId.Percept(1, 1)));
        memoryBasis.add(new CausalRecord.MemoryRef(fixture.receipt.subject(), 1, 1));
        check("immutable", "intent-defensive-receipts", intent.receiptBasis().size() == 1);
        check("immutable", "intent-defensive-memories", intent.memoryBasis().size() == 1);
        rejectsMutation("intent-receipts-unmodifiable",
                () -> intent.receiptBasis().add(new CausalRecord.PerceptRef(
                        fixture.receipt.subject(), new CausalId.Percept(1, 2))));
        rejectsMutation("intent-memories-unmodifiable",
                () -> intent.memoryBasis().add(new CausalRecord.MemoryRef(
                        fixture.receipt.subject(), 1, 2)));
        rejects("constructor", "intent-memory-basis-is-subject-scoped",
                () -> new CausalRecord.IntentProposal(
                        new CausalId.Intent(2, 1), new CausalId.Choice(2, 1),
                        fixture.receipt.subject(), symbol("remember"), symbol("wait"),
                        fixture.delivery.truth().subject(), List.of(),
                        List.of(new CausalRecord.MemoryRef(
                                new CausalRecord.Subject("human-999"), 1, 0))));

        ArrayList<CausalRecord.Participation> affected = new ArrayList<>(
                fixture.effect.affected());
        CausalRecord.Effect effect = new CausalRecord.Effect(fixture.effect.id(),
                fixture.effect.origin(), fixture.effect.transition(), affected);
        affected.clear();
        check("immutable", "effect-defensive-affected", effect.affected().size() == 2);
        rejectsMutation("effect-affected-unmodifiable", effect.affected()::clear);

        ArrayList<CausalRecord.Subject> subjects = new ArrayList<>(fixture.settlement.subjects());
        CausalRecord.SettlementEntry settlement = new CausalRecord.SettlementEntry(
                fixture.settlement.effect(), fixture.settlement.domain(),
                fixture.settlement.owner(), subjects, fixture.settlement.field(),
                fixture.settlement.value());
        subjects.clear();
        check("immutable", "settlement-defensive-subjects",
                settlement.subjects().size() == 1);
        rejectsMutation("settlement-subjects-unmodifiable", settlement.subjects()::clear);
        rejectsMutation("contract-consumers-unmodifiable",
                () -> fixture.receipt.contract().consumers().add(
                        CausalRecord.Boundary.CANONICAL_STATE));
    }

    private static void constructorRefusals(Fixture fixture) {
        CausalRecord.Subject neo = fixture.receipt.subject();
        CausalRecord.Subject trinity = new CausalRecord.Subject("trinity");
        CausalRecord.Principal door = fixture.truth.subject();
        CausalRecord.Principal root = fixture.truth.provenance();

        rejects("constructor", "symbol-uppercase", () -> new CausalRecord.Symbol("Neo"));
        rejects("constructor", "symbol-empty", () -> new CausalRecord.Symbol(""));
        rejects("constructor", "payload-edge-space", () -> new CausalRecord.Payload(" x"));
        rejects("constructor", "payload-control", () -> new CausalRecord.Payload("x\ny"));
        rejects("constructor", "principal-unknown-kind-wrong-key",
                () -> new CausalRecord.Principal(CausalRecord.PrincipalKind.UNKNOWN, "neo"));
        rejects("constructor", "principal-known-kind-unknown-key",
                () -> new CausalRecord.Principal(CausalRecord.PrincipalKind.HUMAN, "unknown"));

        rejects("constructor", "truth-negative-order",
                () -> new CausalRecord.TruthEntry(-1, 0, door, fixture.truth.fact(), root));
        rejects("constructor", "truth-owner-unknown",
                () -> new CausalRecord.TruthEntry(0, 0,
                        CausalRecord.Principal.unknown(), fixture.truth.fact(), root));
        rejects("constructor", "delivery-cross-tick",
                () -> new CausalRecord.DeliveryAttempt(11, 0, neo,
                        CausalRecord.Channel.VISION, root, root, fixture.truth,
                        CausalRecord.Fidelity.FULL, CausalRecord.DeliveryOutcome.DELIVERED,
                        Optional.of(fixture.truth.fact().value()),
                        CausalRecord.DeliveryRule.CONNECTED_RESIDENT_SELF_V1,
                        CausalRecord.AuthorityClass.UNESTABLISHED,
                        CausalRecord.ConsentClass.UNESTABLISHED,
                        CausalRecord.DisclosureClass.AUDIT_MATCHED,
                        CausalRecord.ConstraintClass.NO_EVIDENCE,
                        CausalRecord.ObligationClass.NONE_CITED));
        rejects("constructor", "delivered-needs-full-fidelity",
                () -> deliveryShape(fixture, CausalRecord.Fidelity.PARTIAL,
                        CausalRecord.DeliveryOutcome.DELIVERED,
                        Optional.of(payload("visible"))));
        rejects("constructor", "degraded-needs-content",
                () -> deliveryShape(fixture, CausalRecord.Fidelity.PARTIAL,
                        CausalRecord.DeliveryOutcome.DEGRADED, Optional.empty()));
        rejects("constructor", "occluded-needs-empty-content",
                () -> deliveryShape(fixture, CausalRecord.Fidelity.NONE,
                        CausalRecord.DeliveryOutcome.OCCLUDED,
                        Optional.of(payload("hidden"))));
        rejects("constructor", "percept-negative-uncertainty",
                () -> percept(fixture, -1));
        rejects("constructor", "percept-over-uncertainty",
                () -> percept(fixture, 10_001));

        rejects("constructor", "intent-ownerless-target",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 1),
                        new CausalId.Choice(10, 0), neo, symbol("escape"),
                        symbol("open"), CausalRecord.Principal.unknown(),
                        List.of(fixture.receipt.ref()), List.of()));
        rejects("constructor", "intent-needs-basis",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 1),
                        new CausalId.Choice(10, 0), neo, symbol("escape"),
                        symbol("open"), door, List.of(), List.of()));
        rejects("constructor", "intent-receipts-ordered",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 2),
                        new CausalId.Choice(10, 0), neo, symbol("escape"),
                        symbol("open"), door,
                        List.of(new CausalRecord.PerceptRef(neo, new CausalId.Percept(10, 1)),
                                new CausalRecord.PerceptRef(neo,
                                        new CausalId.Percept(10, 0))),
                        List.of()));
        rejects("constructor", "intent-receipts-distinct",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 2),
                        new CausalId.Choice(10, 0), neo, symbol("escape"),
                        symbol("open"), door,
                        List.of(fixture.receipt.ref(), fixture.receipt.ref()), List.of()));
        rejects("constructor", "intent-no-future-choice",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 0),
                        new CausalId.Choice(11, 0), neo, symbol("escape"),
                        symbol("open"), door, List.of(fixture.receipt.ref()), List.of()));
        rejects("constructor", "intent-no-future-percept",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 0),
                        new CausalId.Choice(10, 0), neo, symbol("escape"),
                        symbol("open"), door, List.of(new CausalRecord.PerceptRef(
                                neo, new CausalId.Percept(11, 0))),
                        List.of()));
        rejects("constructor", "intent-no-other-subject-percept",
                () -> new CausalRecord.IntentProposal(new CausalId.Intent(10, 0),
                        new CausalId.Choice(10, 0), neo, symbol("escape"),
                        symbol("open"), door, List.of(new CausalRecord.PerceptRef(
                                new CausalRecord.Subject("trinity"),
                                new CausalId.Percept(10, 0))), List.of()));
        rejects("constructor", "percept-ref-needs-subject",
                () -> new CausalRecord.PerceptRef(null, new CausalId.Percept(10, 0)));
        rejects("constructor", "percept-ref-needs-id",
                () -> new CausalRecord.PerceptRef(neo, null));
        rejects("constructor", "payload-refuses-lone-high-surrogate",
                () -> new CausalRecord.Payload("\uD800"));
        rejects("constructor", "payload-refuses-lone-low-surrogate",
                () -> new CausalRecord.Payload("\uDC00"));
        check("constructor", "payload-allows-valid-surrogate-pair",
                new CausalRecord.Payload("signal-😀").text().equals("signal-😀"));

        rejects("constructor", "accepted-has-no-rejection",
                () -> new CausalRecord.IntentValidation(fixture.intent.id(), neo,
                        CausalRecord.ValidationOutcome.ACCEPTED, symbol("rule"),
                        Optional.of(payload("no"))));
        rejects("constructor", "rejected-needs-reason",
                () -> new CausalRecord.IntentValidation(fixture.intent.id(), neo,
                        CausalRecord.ValidationOutcome.REJECTED, symbol("rule"),
                        Optional.empty()));
        rejects("constructor", "commit-ownerless-target",
                () -> new CausalRecord.CommittedAction(new CausalId.Commit(10, 1),
                        fixture.intent.id(), fixture.intent.choice(), neo, symbol("open"),
                        CausalRecord.Principal.unknown(), symbol("rule")));
        rejects("constructor", "commit-no-future-intent",
                () -> new CausalRecord.CommittedAction(new CausalId.Commit(9, 0),
                        fixture.intent.id(), fixture.intent.choice(), neo, symbol("open"),
                        door, symbol("rule")));

        rejects("constructor", "choice-origin-order",
                () -> new CausalRecord.ChoiceOrigin(neo, new CausalId.Choice(11, 0),
                        new CausalId.Commit(10, 0), symbol("rule")));
        rejects("constructor", "non-choice-needs-initiator",
                () -> new CausalRecord.NonChoiceOrigin(new CausalId.Cause(10, 0),
                        CausalRecord.NonChoiceSubtype.ENVIRONMENT,
                        CausalRecord.Principal.unknown(), root));
        rejects("constructor", "consenter-needs-evidence",
                () -> new CausalRecord.Participation(neo,
                        CausalRecord.ParticipationRole.INFORMED_CONSENTER,
                        Optional.empty()));
        rejects("constructor", "non-consenter-cannot-carry-consent",
                () -> new CausalRecord.Participation(neo,
                        CausalRecord.ParticipationRole.ACTOR,
                        Optional.of(new CausalRecord.ConsentRef("consent_1"))));
        rejects("constructor", "effect-needs-affected",
                () -> new CausalRecord.Effect(new CausalId.Effect(10, 1),
                        fixture.effect.origin(), symbol("change"), List.of()));
        rejects("constructor", "effect-subjects-distinct",
                () -> new CausalRecord.Effect(new CausalId.Effect(10, 1),
                        fixture.effect.origin(), symbol("change"),
                        List.of(actor(neo), actor(neo))));
        rejects("constructor", "choice-effect-needs-origin-actor",
                () -> new CausalRecord.Effect(new CausalId.Effect(10, 1),
                        fixture.effect.origin(), symbol("change"),
                        List.of(actor(trinity))));
        CausalRecord.NonChoiceOrigin nonChoice = new CausalRecord.NonChoiceOrigin(
                new CausalId.Cause(10, 0), CausalRecord.NonChoiceSubtype.ENVIRONMENT,
                root, root);
        rejects("constructor", "non-choice-cannot-claim-actor",
                () -> new CausalRecord.Effect(new CausalId.Effect(10, 1), nonChoice,
                        symbol("change"), List.of(actor(neo))));
        rejects("constructor", "effect-no-future-origin",
                () -> new CausalRecord.Effect(new CausalId.Effect(9, 0), nonChoice,
                        symbol("change"), List.of(affected(neo))));

        rejects("constructor", "settlement-needs-owner",
                () -> new CausalRecord.SettlementEntry(fixture.effect.id(),
                        CausalRecord.SettlementDomain.BIOGRAPHY,
                        CausalRecord.Principal.unknown(), List.of(neo), symbol("field"),
                        payload("value")));
        rejects("constructor", "settlement-needs-subjects",
                () -> new CausalRecord.SettlementEntry(fixture.effect.id(),
                        CausalRecord.SettlementDomain.OBLIGATION, root, List.of(),
                        symbol("field"), payload("value")));
        rejects("constructor", "biography-one-subject",
                () -> new CausalRecord.SettlementEntry(fixture.effect.id(),
                        CausalRecord.SettlementDomain.BIOGRAPHY, root,
                        List.of(neo, trinity), symbol("field"), payload("value")));
        rejects("constructor", "relationship-two-subjects",
                () -> new CausalRecord.SettlementEntry(fixture.effect.id(),
                        CausalRecord.SettlementDomain.RELATIONSHIP, root,
                        List.of(neo), symbol("field"), payload("value")));

        rejects("constructor", "contract-needs-owner",
                () -> new CausalRecord.Contract(null,
                        CausalRecord.Boundary.SIMULATION_ROOT,
                        List.of(CausalRecord.Boundary.SIMULATION_ROOT),
                        CausalRecord.Exposure.ROOT_ONLY));
        rejects("constructor", "contract-needs-consumer",
                () -> new CausalRecord.Contract(CausalRecord.Owner.SIMULATION_ROOT,
                        CausalRecord.Boundary.SIMULATION_ROOT, List.of(),
                        CausalRecord.Exposure.ROOT_ONLY));
        rejects("constructor", "contract-consumers-distinct",
                () -> new CausalRecord.Contract(CausalRecord.Owner.SIMULATION_ROOT,
                        CausalRecord.Boundary.SIMULATION_ROOT,
                        List.of(CausalRecord.Boundary.SIMULATION_ROOT,
                                CausalRecord.Boundary.SIMULATION_ROOT),
                        CausalRecord.Exposure.ROOT_ONLY));
        rejects("constructor", "mind-exposure-needs-mind",
                () -> new CausalRecord.Contract(CausalRecord.Owner.HUMAN_MIND,
                        CausalRecord.Boundary.SIMULATION_ROOT,
                        List.of(CausalRecord.Boundary.SIMULATION_ROOT),
                        CausalRecord.Exposure.MIND_VISIBLE));
        rejects("constructor", "mind-consumer-needs-visible-projection",
                () -> new CausalRecord.Contract(CausalRecord.Owner.HUMAN_MIND,
                        CausalRecord.Boundary.SIMULATION_ROOT,
                        List.of(CausalRecord.Boundary.HUMAN_MIND),
                        CausalRecord.Exposure.ROOT_ONLY));
        rejects("constructor", "command-needs-state-consumer",
                () -> new CausalRecord.Contract(CausalRecord.Owner.SIMULATION_ROOT,
                        CausalRecord.Boundary.SIMULATION_ROOT,
                        List.of(CausalRecord.Boundary.SIMULATION_ROOT),
                        CausalRecord.Exposure.CANONICAL_COMMAND));
        rejects("constructor", "settlement-input-needs-ledger",
                () -> new CausalRecord.Contract(CausalRecord.Owner.CANONICAL_STATE,
                        CausalRecord.Boundary.CANONICAL_STATE,
                        List.of(CausalRecord.Boundary.SIMULATION_ROOT),
                        CausalRecord.Exposure.SETTLEMENT_INPUT));
    }

    private static void componentFence(Fixture fixture) {
        for (Class<?> nested : CausalRecord.class.getDeclaredClasses()) {
            if (!nested.isRecord()) {
                continue;
            }
            long staticState = Arrays.stream(nested.getDeclaredFields())
                    .filter(field -> Modifier.isStatic(field.getModifiers()))
                    .count();
            check("forbidden", nested.getSimpleName() + "-no-static-hidden-state",
                    staticState == 0);
            for (RecordComponent component : nested.getRecordComponents()) {
                Class<?> raw = component.getType();
                String generic = component.getGenericType().getTypeName();
                boolean forbidden = FORBIDDEN_TYPES.stream().anyMatch(generic::contains);
                boolean neutral = raw.isPrimitive()
                        || raw == String.class
                        || raw == List.class
                        || raw == Optional.class
                        || raw.getName().startsWith("matrix.causal.CausalId$")
                        || raw.getName().startsWith("matrix.causal.CausalRecord$");
                check("forbidden", nested.getSimpleName() + "-" + component.getName()
                                + "-no-world-or-mind",
                        !forbidden);
                check("forbidden", nested.getSimpleName() + "-" + component.getName()
                                + "-no-object-envelope",
                        raw != Object.class && !Map.class.isAssignableFrom(raw)
                                && !generic.contains("java.lang.Object"));
                check("forbidden", nested.getSimpleName() + "-" + component.getName()
                                + "-no-array",
                        !raw.isArray());
                check("forbidden", nested.getSimpleName() + "-" + component.getName()
                                + "-neutral-component",
                        neutral);
            }
        }
        check("forbidden", "effect-origin-sealed", CausalRecord.EffectOrigin.class.isSealed());
        check("forbidden", "effect-origin-exhaustive",
                Set.of(CausalRecord.EffectOrigin.class.getPermittedSubclasses()).equals(Set.of(
                        CausalRecord.ChoiceOrigin.class, CausalRecord.NonChoiceOrigin.class)));

        Map<Class<?>, CausalRecord> examples = new LinkedHashMap<>();
        for (CausalRecord record : fixture.records()) {
            examples.put(record.getClass(), record);
        }
        for (CausalRecord outer : fixture.records()) {
            for (RecordComponent component : outer.getClass().getRecordComponents()) {
                if (!CausalRecord.class.isAssignableFrom(component.getType())) {
                    continue;
                }
                CausalRecord inner = examples.get(component.getType());
                check("visibility", outer.kind() + "-" + component.getName()
                                + "-known-inner-route",
                        inner != null);
                check("visibility", outer.kind() + "-" + component.getName()
                                + "-no-transitive-escalation",
                        inner != null && inner.contract().consumers()
                                .containsAll(outer.contract().consumers()));
            }
        }
    }

    private static CausalRecord.DeliveryAttempt deliveryShape(
            Fixture fixture, CausalRecord.Fidelity fidelity,
            CausalRecord.DeliveryOutcome outcome, Optional<CausalRecord.Payload> content) {
        return new CausalRecord.DeliveryAttempt(fixture.delivery.tick(),
                fixture.delivery.sequence(), fixture.delivery.subject(),
                fixture.delivery.channel(), fixture.delivery.actualSource(),
                fixture.delivery.declaredSource(), fixture.delivery.truth(),
                fidelity, outcome, content, fixture.delivery.rule(),
                fixture.delivery.authority(), fixture.delivery.consent(),
                switch (outcome) {
                    case DELIVERED -> CausalRecord.DisclosureClass.AUDIT_MATCHED;
                    case DEGRADED -> CausalRecord.DisclosureClass.AUDIT_DIVERGED;
                    case OCCLUDED -> CausalRecord.DisclosureClass.NOT_PRESENTED;
                }, fixture.delivery.constraint(), fixture.delivery.obligation());
    }

    private static CausalRecord.PerceptReceipt percept(Fixture fixture, int uncertainty) {
        return new CausalRecord.PerceptReceipt(fixture.receipt.id(),
                fixture.receipt.subject(), fixture.receipt.channel(),
                fixture.receipt.content(), fixture.receipt.presentedClaim(),
                fixture.receipt.perceivedSource(), uncertainty,
                fixture.receipt.fidelity());
    }

    private static CausalRecord.Participation actor(CausalRecord.Subject subject) {
        return new CausalRecord.Participation(subject,
                CausalRecord.ParticipationRole.ACTOR, Optional.empty());
    }

    private static CausalRecord.Participation affected(CausalRecord.Subject subject) {
        return new CausalRecord.Participation(subject,
                CausalRecord.ParticipationRole.AFFECTED_WITHOUT_CHOICE, Optional.empty());
    }

    private static CausalRecord.Symbol symbol(String value) {
        return new CausalRecord.Symbol(value);
    }

    private static CausalRecord.Payload payload(String value) {
        return new CausalRecord.Payload(value);
    }

    private static CausalRecord.Principal principal(CausalRecord.PrincipalKind kind,
                                                    String key) {
        return new CausalRecord.Principal(kind, key);
    }

    private static <T> Set<T> symmetricDifference(Set<T> left, Set<T> right) {
        Set<T> difference = new LinkedHashSet<>(left);
        for (T value : right) {
            if (!difference.add(value)) {
                difference.remove(value);
            }
        }
        return difference;
    }

    private static int cases(String subject) {
        return CASES.getOrDefault(subject, 0);
    }

    private static int failures(String subject) {
        return FAILURES.getOrDefault(subject, 0);
    }

    private static void check(String subject, String name, boolean held) {
        CASES.merge(subject, 1, Integer::sum);
        if (!held) {
            FAILURES.merge(subject, 1, Integer::sum);
            BREAKS.add("CAUSAL_RECORD_BREAK subject=" + subject + " case=" + name);
        }
    }

    private static void rejects(String subject, String name, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            rejected = true;
        }
        check(subject, name, rejected);
    }

    private static void rejectsMutation(String name, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        check("immutable", name, rejected);
    }

    private CausalRecords() {}
}
