package matrix.causal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The immutable carrier grammar for D-066's Human causal boundary.
 *
 * <p>This is deliberately not an omniscient event envelope. Each phase has a
 * different Java record, and each kind derives one fixed custody contract:
 * who owns the carrier, who may produce it, who may consume it, and which
 * projection may cross a boundary. Callers cannot relabel a root audit as a
 * mind-visible receipt.
 *
 * <p>The most important split is physical in the type graph. A
 * {@link PerceptReceipt} contains only the projection a mind may read. Hidden
 * truth and actual source live in {@link DeliveryAttempt}; the root-only
 * {@link ReceiptAudit} pairs the two. Passing a receipt to a mind therefore
 * cannot accidentally pass the audit object that justified it.
 *
 * <p>Every component is a primitive, enum, immutable value, causal identity,
 * optional immutable value, or defensively copied list. No record may hold a
 * {@code World}, {@code Brain}, {@code Human}, {@code Avatar}, or generic
 * mutable object. This file defines vocabulary and invariants only: allocation,
 * phase execution, persistence, and behavior belong to later build units.
 */
public sealed interface CausalRecord permits CausalRecord.TruthEntry,
        CausalRecord.DeliveryAttempt, CausalRecord.PerceptReceipt,
        CausalRecord.ReceiptAudit, CausalRecord.IntentProposal,
        CausalRecord.IntentValidation, CausalRecord.CommittedAction,
        CausalRecord.Effect, CausalRecord.SettlementEntry {

    /** Which phase-specific carrier this value is. */
    Kind kind();

    /** The non-forgeable custody and projection law of this carrier kind. */
    default Contract contract() {
        return kind().contract();
    }

    /** State owner at the boundary; this is custody, not moral responsibility. */
    enum Owner {
        SIMULATION_ROOT,
        HUMAN_MIND,
        CANONICAL_STATE,
        SETTLEMENT_LEDGER
    }

    /** Legal endpoints at the neutral causal seam. */
    enum Boundary {
        SIMULATION_ROOT,
        HUMAN_MIND,
        CANONICAL_STATE,
        SETTLEMENT_LEDGER
    }

    /** What a consumer is allowed to receive from this carrier. */
    enum Exposure {
        ROOT_ONLY,
        MIND_VISIBLE,
        ROOT_INPUT,
        CANONICAL_COMMAND,
        SETTLEMENT_INPUT
    }

    /**
     * Fixed authority declaration for one carrier kind.
     *
     * <p>The consumer list is copied and must be nonempty and duplicate-free.
     * Exposure-specific checks make it impossible to describe a mind-visible
     * value without a mind consumer, or to smuggle another kind into the mind.
     */
    record Contract(Owner owner, Boundary producer, List<Boundary> consumers,
                    Exposure exposure) {
        public Contract {
            Objects.requireNonNull(owner, "record owner");
            Objects.requireNonNull(producer, "record producer");
            Objects.requireNonNull(exposure, "record exposure");
            consumers = immutableNonempty(consumers, "record consumers");
            for (int i = 0; i < consumers.size(); i++) {
                for (int j = i + 1; j < consumers.size(); j++) {
                    if (consumers.get(i) == consumers.get(j)) {
                        throw new IllegalArgumentException("record consumers must be distinct");
                    }
                }
            }
            boolean reachesMind = consumers.contains(Boundary.HUMAN_MIND);
            if ((exposure == Exposure.MIND_VISIBLE) != reachesMind) {
                throw new IllegalArgumentException(
                        "only a mind-visible projection may reach the Human mind");
            }
            if (exposure == Exposure.CANONICAL_COMMAND
                    && !consumers.contains(Boundary.CANONICAL_STATE)) {
                throw new IllegalArgumentException(
                        "a canonical command must reach canonical state");
            }
            if (exposure == Exposure.ROOT_INPUT
                    && !consumers.contains(Boundary.SIMULATION_ROOT)) {
                throw new IllegalArgumentException("a root input must reach the root");
            }
            if (exposure == Exposure.SETTLEMENT_INPUT
                    && !consumers.contains(Boundary.SETTLEMENT_LEDGER)) {
                throw new IllegalArgumentException(
                        "a settlement input must reach the settlement ledger");
            }
        }
    }

    /** One closed roster: adding a carrier requires declaring its route here. */
    enum Kind {
        TRUTH_ENTRY(new Contract(Owner.SIMULATION_ROOT,
                Boundary.SIMULATION_ROOT,
                List.of(Boundary.SIMULATION_ROOT), Exposure.ROOT_ONLY)),
        DELIVERY_ATTEMPT(new Contract(Owner.SIMULATION_ROOT,
                Boundary.SIMULATION_ROOT,
                List.of(Boundary.SIMULATION_ROOT), Exposure.ROOT_ONLY)),
        PERCEPT_RECEIPT(new Contract(Owner.HUMAN_MIND,
                Boundary.SIMULATION_ROOT,
                List.of(Boundary.SIMULATION_ROOT, Boundary.HUMAN_MIND),
                Exposure.MIND_VISIBLE)),
        RECEIPT_AUDIT(new Contract(Owner.SIMULATION_ROOT,
                Boundary.SIMULATION_ROOT,
                List.of(Boundary.SIMULATION_ROOT), Exposure.ROOT_ONLY)),
        INTENT_PROPOSAL(new Contract(Owner.HUMAN_MIND,
                Boundary.HUMAN_MIND,
                List.of(Boundary.SIMULATION_ROOT), Exposure.ROOT_INPUT)),
        INTENT_VALIDATION(new Contract(Owner.SIMULATION_ROOT,
                Boundary.SIMULATION_ROOT,
                List.of(Boundary.SIMULATION_ROOT), Exposure.ROOT_ONLY)),
        COMMITTED_ACTION(new Contract(Owner.SIMULATION_ROOT,
                Boundary.SIMULATION_ROOT,
                List.of(Boundary.CANONICAL_STATE), Exposure.CANONICAL_COMMAND)),
        EFFECT(new Contract(Owner.CANONICAL_STATE,
                Boundary.CANONICAL_STATE,
                List.of(Boundary.SIMULATION_ROOT, Boundary.SETTLEMENT_LEDGER),
                Exposure.SETTLEMENT_INPUT)),
        SETTLEMENT_ENTRY(new Contract(Owner.SETTLEMENT_LEDGER,
                Boundary.SETTLEMENT_LEDGER,
                List.of(Boundary.SIMULATION_ROOT), Exposure.ROOT_ONLY));

        private final Contract contract;

        Kind(Contract contract) {
            this.contract = contract;
        }

        public Contract contract() {
            return contract;
        }
    }

    /** A strict stable symbol, never a display name or object handle. */
    record Symbol(String value) implements Comparable<Symbol> {
        public Symbol {
            value = requireSymbol(value, "symbol");
        }

        @Override public int compareTo(Symbol other) {
            return value.compareTo(Objects.requireNonNull(other, "other symbol").value);
        }
    }

    /** Immutable canonical content; control characters and edge whitespace are refused. */
    record Payload(String text) {
        public Payload {
            text = requireText(text, "payload");
        }
    }

    /** Stable Human identity token; never a Human or Avatar reference. */
    record Subject(Symbol key) implements Comparable<Subject> {
        public Subject {
            Objects.requireNonNull(key, "subject key");
        }

        public Subject(String key) {
            this(new Symbol(key));
        }

        @Override public int compareTo(Subject other) {
            return key.compareTo(Objects.requireNonNull(other, "other subject").key);
        }
    }

    enum PrincipalKind {
        HUMAN,
        MACHINE,
        SYSTEM,
        INSTITUTION,
        PLACE,
        UNKNOWN
    }

    /** Stable source/target/owner token for something that is not carried as an object. */
    record Principal(PrincipalKind kind, Symbol key) implements Comparable<Principal> {
        public Principal {
            Objects.requireNonNull(kind, "principal kind");
            Objects.requireNonNull(key, "principal key");
            boolean unknownKey = key.value().equals("unknown");
            if ((kind == PrincipalKind.UNKNOWN) != unknownKey) {
                throw new IllegalArgumentException(
                        "only the UNKNOWN principal may use the unknown key");
            }
        }

        public Principal(PrincipalKind kind, String key) {
            this(kind, new Symbol(key));
        }

        public static Principal unknown() {
            return new Principal(PrincipalKind.UNKNOWN, "unknown");
        }

        public boolean known() {
            return kind != PrincipalKind.UNKNOWN;
        }

        @Override public int compareTo(Principal other) {
            Objects.requireNonNull(other, "other principal");
            int byKind = kind.compareTo(other.kind);
            return byKind != 0 ? byKind : key.compareTo(other.key);
        }
    }

    record Fact(Symbol predicate, Payload value) {
        public Fact {
            Objects.requireNonNull(predicate, "fact predicate");
            Objects.requireNonNull(value, "fact value");
        }
    }

    record MemoryRef(Symbol key) implements Comparable<MemoryRef> {
        public MemoryRef {
            Objects.requireNonNull(key, "memory key");
        }

        public MemoryRef(String key) {
            this(new Symbol(key));
        }

        @Override public int compareTo(MemoryRef other) {
            return key.compareTo(Objects.requireNonNull(other, "other memory").key);
        }
    }

    /**
     * Subject-scoped citation of one mind-visible percept.
     *
     * <p>{@link CausalId.Percept} sequences are dense only inside one
     * subject's tick input. Carrying the subject in the reference prevents two
     * residents' equal local identities from aliasing in deduplication,
     * memory, or later intent evidence.
     */
    record PerceptRef(Subject subject, CausalId.Percept id)
            implements Comparable<PerceptRef> {
        public PerceptRef {
            Objects.requireNonNull(subject, "percept reference subject");
            Objects.requireNonNull(id, "percept reference id");
        }

        @Override public int compareTo(PerceptRef other) {
            Objects.requireNonNull(other, "other percept reference");
            int bySubject = subject.compareTo(other.subject);
            return bySubject != 0 ? bySubject : id.compareTo(other.id);
        }
    }

    record ConsentRef(Symbol key) {
        public ConsentRef {
            Objects.requireNonNull(key, "consent key");
        }

        public ConsentRef(String key) {
            this(new Symbol(key));
        }
    }

    /** One perception-eligible fact frozen at canonical tick start. */
    record TruthEntry(long tick, int sequence, Principal subject, Fact fact,
                      Principal provenance)
            implements CausalRecord, Comparable<TruthEntry> {
        public TruthEntry {
            requireOrder(tick, sequence, "truth entry");
            requireKnown(subject, "truth subject");
            Objects.requireNonNull(fact, "truth fact");
            Objects.requireNonNull(provenance, "truth provenance");
        }

        @Override public Kind kind() { return Kind.TRUTH_ENTRY; }

        @Override public int compareTo(TruthEntry other) {
            Objects.requireNonNull(other, "other truth entry");
            int byTick = Long.compare(tick, other.tick);
            return byTick != 0 ? byTick : Integer.compare(sequence, other.sequence);
        }
    }

    enum Channel {
        VISION,
        AUDIO,
        TEXT,
        HAPTIC,
        INTERNAL,
        NO_SIGNAL
    }

    enum Fidelity {
        FULL,
        PARTIAL,
        NONE
    }

    enum DeliveryOutcome {
        DELIVERED,
        DEGRADED,
        OCCLUDED
    }

    /** Named policy that selected a fact for an attempted delivery. */
    enum DeliveryRule {
        CONNECTED_RESIDENT_SELF_V1
    }

    /** Epistemic classifications: none of these values manufactures permission. */
    enum AuthorityClass { UNESTABLISHED }
    enum ConsentClass { UNESTABLISHED }

    /** Structural relation between presented material and the frozen audit fact. */
    enum DisclosureClass {
        AUDIT_MATCHED,
        AUDIT_DIVERGED,
        NOT_PRESENTED
    }

    enum ConstraintClass { NO_EVIDENCE }

    /** NONE_CITED means no obligation evidence accompanies this attempt, not no debt. */
    enum ObligationClass { NONE_CITED }

    /** Root-side account of one attempted delivery, including hidden audit facts. */
    record DeliveryAttempt(long tick, int sequence, Subject subject, Channel channel,
                           Principal actualSource, Principal declaredSource,
                           TruthEntry truth, Fidelity fidelity, DeliveryOutcome outcome,
                           Optional<Payload> presentedContent, DeliveryRule rule,
                           AuthorityClass authority, ConsentClass consent,
                           DisclosureClass disclosure, ConstraintClass constraint,
                           ObligationClass obligation)
            implements CausalRecord, Comparable<DeliveryAttempt> {
        public DeliveryAttempt {
            requireOrder(tick, sequence, "delivery attempt");
            Objects.requireNonNull(subject, "delivery subject");
            Objects.requireNonNull(channel, "delivery channel");
            Objects.requireNonNull(actualSource, "actual source");
            Objects.requireNonNull(declaredSource, "declared source");
            Objects.requireNonNull(truth, "delivery truth");
            Objects.requireNonNull(fidelity, "delivery fidelity");
            Objects.requireNonNull(outcome, "delivery outcome");
            presentedContent = Objects.requireNonNull(presentedContent,
                    "presented content");
            Objects.requireNonNull(rule, "delivery rule");
            Objects.requireNonNull(authority, "delivery authority classification");
            Objects.requireNonNull(consent, "delivery consent classification");
            Objects.requireNonNull(disclosure, "delivery disclosure classification");
            Objects.requireNonNull(constraint, "delivery constraint classification");
            Objects.requireNonNull(obligation, "delivery obligation classification");
            if (truth.tick() != tick || truth.sequence() != sequence) {
                throw new IllegalArgumentException(
                        "delivery and frozen truth must have the same order");
            }
            if (truth.subject().kind() != PrincipalKind.HUMAN
                    || !truth.subject().key().equals(subject.key())) {
                throw new IllegalArgumentException(
                        "delivery subject must be the frozen truth's Human subject");
            }
            if (!actualSource.equals(truth.provenance())) {
                throw new IllegalArgumentException(
                        "actual delivery source must be frozen truth provenance");
            }
            boolean contentMatches = presentedContent
                    .map(content -> content.equals(truth.fact().value()))
                    .orElse(false);
            boolean sourceMatches = declaredSource.equals(actualSource);
            boolean shapeHeld = switch (outcome) {
                case DELIVERED -> fidelity == Fidelity.FULL && contentMatches
                        && sourceMatches && disclosure == DisclosureClass.AUDIT_MATCHED;
                case DEGRADED -> fidelity == Fidelity.PARTIAL && presentedContent.isPresent()
                        && (!contentMatches || !sourceMatches)
                        && disclosure == DisclosureClass.AUDIT_DIVERGED;
                case OCCLUDED -> fidelity == Fidelity.NONE && presentedContent.isEmpty()
                        && disclosure == DisclosureClass.NOT_PRESENTED;
            };
            if (!shapeHeld) {
                throw new IllegalArgumentException(
                        "delivery outcome, fidelity, disclosure, and frozen audit disagree");
            }
        }

        @Override public Kind kind() { return Kind.DELIVERY_ATTEMPT; }

        @Override public int compareTo(DeliveryAttempt other) {
            Objects.requireNonNull(other, "other delivery attempt");
            int byTick = Long.compare(tick, other.tick);
            return byTick != 0 ? byTick : Integer.compare(sequence, other.sequence);
        }
    }

    /**
     * The complete and only carrier a mind may receive from a delivery phase.
     *
     * <p>{@code fidelity} is the delivery system's presented classification,
     * not independent access to the root's frozen truth. Likewise,
     * {@code perceivedSource} is only the source presented or claimed to the
     * subject. The separate root audit is what can compare either claim with
     * hidden fact and provenance.
     */
    record PerceptReceipt(CausalId.Percept id, Subject subject, Channel channel,
                          Payload content, Principal perceivedSource,
                          int uncertaintyBasisPoints, Fidelity fidelity)
            implements CausalRecord, Comparable<PerceptReceipt> {
        public PerceptReceipt {
            Objects.requireNonNull(id, "percept id");
            Objects.requireNonNull(subject, "percept subject");
            Objects.requireNonNull(channel, "percept channel");
            Objects.requireNonNull(content, "percept content");
            Objects.requireNonNull(perceivedSource, "perceived source");
            Objects.requireNonNull(fidelity, "presented percept fidelity");
            if (channel == Channel.NO_SIGNAL) {
                throw new IllegalArgumentException(
                        "NO_SIGNAL needs typed observable availability evidence");
            }
            if (uncertaintyBasisPoints < 0 || uncertaintyBasisPoints > 10_000) {
                throw new IllegalArgumentException(
                        "percept uncertainty must be between 0 and 10000 basis points");
            }
            if (fidelity == Fidelity.NONE) {
                throw new IllegalArgumentException(
                        "a percept receipt must present full or partial content");
            }
        }

        @Override public Kind kind() { return Kind.PERCEPT_RECEIPT; }

        /** Visible source tick, normalized through the percept identity. */
        public long tick() { return id.tick(); }

        /** Subject-scoped identity used by deduplication and later citations. */
        public PerceptRef ref() { return new PerceptRef(subject, id); }

        @Override public int compareTo(PerceptReceipt other) {
            Objects.requireNonNull(other, "other percept receipt");
            int order = ref().compareTo(other.ref());
            if (order == 0) order = channel.compareTo(other.channel);
            if (order == 0) order = content.text().compareTo(other.content.text());
            if (order == 0) order = perceivedSource.compareTo(other.perceivedSource);
            if (order == 0) {
                order = Integer.compare(uncertaintyBasisPoints,
                        other.uncertaintyBasisPoints);
            }
            if (order == 0) order = fidelity.compareTo(other.fidelity);
            return order;
        }
    }

    /** Root-only mapping from one visible receipt to the hidden audit that licensed it. */
    record ReceiptAudit(PerceptReceipt receipt, DeliveryAttempt delivery)
            implements CausalRecord {
        public ReceiptAudit {
            Objects.requireNonNull(receipt, "audited receipt");
            Objects.requireNonNull(delivery, "receipt delivery");
            if (delivery.outcome() == DeliveryOutcome.OCCLUDED) {
                throw new IllegalArgumentException("an occluded delivery issues no receipt");
            }
            if (receipt.id().tick() != delivery.tick()
                    || !receipt.subject().equals(delivery.subject())
                    || receipt.channel() != delivery.channel()
                    || !receipt.content().equals(delivery.presentedContent().orElseThrow())
                    || !receipt.perceivedSource().equals(delivery.declaredSource())
                    || receipt.fidelity() != delivery.fidelity()) {
                throw new IllegalArgumentException(
                        "receipt projection does not match its root-side delivery audit");
            }
        }

        @Override public Kind kind() { return Kind.RECEIPT_AUDIT; }
    }

    /** Immutable request emitted by a mind; it has no authority to mutate state. */
    record IntentProposal(CausalId.Intent id, CausalId.Choice choice, Subject actor,
                          Symbol goal, Symbol requestedAct, Principal target,
                          List<PerceptRef> receiptBasis,
                          List<MemoryRef> memoryBasis)
            implements CausalRecord, Comparable<IntentProposal> {
        public IntentProposal {
            Objects.requireNonNull(id, "intent id");
            Objects.requireNonNull(choice, "choice id");
            Objects.requireNonNull(actor, "intent actor");
            Objects.requireNonNull(goal, "intent goal");
            Objects.requireNonNull(requestedAct, "requested act");
            requireKnown(target, "intent target");
            receiptBasis = orderedDistinct(receiptBasis, "receipt basis");
            memoryBasis = orderedDistinct(memoryBasis, "memory basis");
            if (receiptBasis.isEmpty() && memoryBasis.isEmpty()) {
                throw new IllegalArgumentException("an intent must name its reachable basis");
            }
            if (choice.tick() > id.tick()) {
                throw new IllegalArgumentException("a future choice cannot cause an intent");
            }
            for (PerceptRef percept : receiptBasis) {
                if (!percept.subject().equals(actor)) {
                    throw new IllegalArgumentException(
                            "an intent cannot cite another subject's percept");
                }
                if (percept.id().tick() > id.tick()) {
                    throw new IllegalArgumentException(
                            "a future percept cannot be an intent basis");
                }
            }
        }

        @Override public Kind kind() { return Kind.INTENT_PROPOSAL; }

        @Override public int compareTo(IntentProposal other) {
            return id.compareTo(Objects.requireNonNull(other, "other intent").id);
        }
    }

    enum ValidationOutcome {
        ACCEPTED,
        REJECTED
    }

    /** Root-side validation result; a rejection is not itself a mind receipt. */
    record IntentValidation(CausalId.Intent intent, Subject actor,
                            ValidationOutcome outcome, Symbol rule,
                            Optional<Payload> rejectionReason)
            implements CausalRecord {
        public IntentValidation {
            Objects.requireNonNull(intent, "validated intent");
            Objects.requireNonNull(actor, "validated actor");
            Objects.requireNonNull(outcome, "validation outcome");
            Objects.requireNonNull(rule, "validation rule");
            rejectionReason = Objects.requireNonNull(rejectionReason,
                    "rejection reason");
            if ((outcome == ValidationOutcome.REJECTED) != rejectionReason.isPresent()) {
                throw new IllegalArgumentException(
                        "only a rejected intent carries a rejection reason");
            }
        }

        @Override public Kind kind() { return Kind.INTENT_VALIDATION; }
    }

    /** The only Human-choice command canonical state is allowed to consume. */
    record CommittedAction(CausalId.Commit id, CausalId.Intent intent,
                           CausalId.Choice choice, Subject actor, Symbol act,
                           Principal target, Symbol validatingRule)
            implements CausalRecord, Comparable<CommittedAction> {
        public CommittedAction {
            Objects.requireNonNull(id, "commit id");
            Objects.requireNonNull(intent, "committed intent");
            Objects.requireNonNull(choice, "committed choice");
            Objects.requireNonNull(actor, "committed actor");
            Objects.requireNonNull(act, "committed act");
            requireKnown(target, "committed target");
            Objects.requireNonNull(validatingRule, "commit validating rule");
            if (intent.tick() > id.tick() || choice.tick() > id.tick()) {
                throw new IllegalArgumentException(
                        "a committed action cannot precede its intent or choice");
            }
        }

        @Override public Kind kind() { return Kind.COMMITTED_ACTION; }

        @Override public int compareTo(CommittedAction other) {
            return id.compareTo(Objects.requireNonNull(other, "other action").id);
        }
    }

    sealed interface EffectOrigin permits ChoiceOrigin, NonChoiceOrigin {
        long tick();
    }

    /** Human-choice origin, kept separate from every affected Human's role. */
    record ChoiceOrigin(Subject actor, CausalId.Choice choice,
                        CausalId.Commit commit, Symbol validatingRule)
            implements EffectOrigin {
        public ChoiceOrigin {
            Objects.requireNonNull(actor, "choice-origin actor");
            Objects.requireNonNull(choice, "choice-origin choice");
            Objects.requireNonNull(commit, "choice-origin commit");
            Objects.requireNonNull(validatingRule, "choice-origin rule");
            if (choice.tick() > commit.tick()) {
                throw new IllegalArgumentException("a commit cannot precede its choice");
            }
        }

        @Override public long tick() { return commit.tick(); }
    }

    enum NonChoiceSubtype {
        PHYSIOLOGY,
        ENVIRONMENT,
        NON_HUMAN_ACTOR,
        SYSTEM_IMPOSED,
        UNRESOLVED
    }

    /** Explicit non-choice cause; owner and initiator never silently disappear. */
    record NonChoiceOrigin(CausalId.Cause cause, NonChoiceSubtype subtype,
                           Principal initiator, Principal responsibleOwner)
            implements EffectOrigin {
        public NonChoiceOrigin {
            Objects.requireNonNull(cause, "non-choice cause");
            Objects.requireNonNull(subtype, "non-choice subtype");
            requireKnown(initiator, "non-choice initiator");
            requireKnown(responsibleOwner, "non-choice responsible owner");
        }

        @Override public long tick() { return cause.tick(); }
    }

    enum ParticipationRole {
        ACTOR,
        INFORMED_CONSENTER,
        AFFECTED_WITHOUT_CHOICE
    }

    /** Participation is not causation: consent evidence exists only on its own role. */
    record Participation(Subject subject, ParticipationRole role,
                         Optional<ConsentRef> consent)
            implements Comparable<Participation> {
        public Participation {
            Objects.requireNonNull(subject, "affected subject");
            Objects.requireNonNull(role, "participation role");
            consent = Objects.requireNonNull(consent, "consent evidence");
            if ((role == ParticipationRole.INFORMED_CONSENTER) != consent.isPresent()) {
                throw new IllegalArgumentException(
                        "only an informed consenter carries consent evidence");
            }
        }

        @Override public int compareTo(Participation other) {
            return subject.compareTo(Objects.requireNonNull(other,
                    "other participation").subject);
        }
    }

    /** One classified canonical effect with a complete affected-Human roster. */
    record Effect(CausalId.Effect id, EffectOrigin origin, Symbol transition,
                  List<Participation> affected)
            implements CausalRecord, Comparable<Effect> {
        public Effect {
            Objects.requireNonNull(id, "effect id");
            Objects.requireNonNull(origin, "effect origin");
            Objects.requireNonNull(transition, "effect transition");
            affected = orderedDistinctNonempty(affected, "affected participants");
            if (origin.tick() > id.tick()) {
                throw new IllegalArgumentException("an effect cannot precede its cause");
            }
            long actors = affected.stream()
                    .filter(p -> p.role() == ParticipationRole.ACTOR)
                    .count();
            if (origin instanceof ChoiceOrigin choiceOrigin) {
                boolean actorMatches = affected.stream().anyMatch(p ->
                        p.role() == ParticipationRole.ACTOR
                                && p.subject().equals(choiceOrigin.actor()));
                if (actors != 1 || !actorMatches) {
                    throw new IllegalArgumentException(
                            "a choice-caused effect needs exactly its actor participation");
                }
            } else if (actors != 0) {
                throw new IllegalArgumentException(
                        "a non-choice effect cannot claim a Human actor");
            }
        }

        @Override public Kind kind() { return Kind.EFFECT; }

        @Override public int compareTo(Effect other) {
            return id.compareTo(Objects.requireNonNull(other, "other effect").id);
        }
    }

    enum SettlementDomain {
        BIOGRAPHY,
        RELATIONSHIP,
        INSTITUTION,
        OBLIGATION
    }

    /** Future-causal consequence; it is state, not a report after the scene. */
    record SettlementEntry(CausalId.Effect effect, SettlementDomain domain,
                           Principal owner, List<Subject> subjects,
                           Symbol field, Payload value)
            implements CausalRecord {
        public SettlementEntry {
            Objects.requireNonNull(effect, "settlement effect");
            Objects.requireNonNull(domain, "settlement domain");
            requireKnown(owner, "settlement owner");
            subjects = orderedDistinctNonempty(subjects, "settlement subjects");
            Objects.requireNonNull(field, "settlement field");
            Objects.requireNonNull(value, "settlement value");
            if (domain == SettlementDomain.BIOGRAPHY && subjects.size() != 1) {
                throw new IllegalArgumentException(
                        "a biography entry belongs to exactly one subject");
            }
            if (domain == SettlementDomain.RELATIONSHIP && subjects.size() < 2) {
                throw new IllegalArgumentException(
                        "a relationship entry needs at least two subjects");
            }
        }

        @Override public Kind kind() { return Kind.SETTLEMENT_ENTRY; }
    }

    private static String requireSymbol(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.length() > 64
                || value.charAt(0) < 'a' || value.charAt(0) > 'z') {
            throw new IllegalArgumentException(name + " must be a lowercase canonical symbol");
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean held = c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9'
                    || c == '_' || c == '-' || c == '.';
            if (!held) {
                throw new IllegalArgumentException(
                        name + " must be a lowercase canonical symbol");
            }
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.length() > 4096 || !value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " must be bounded canonical text");
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalArgumentException(
                            name + " contains an unpaired UTF-16 surrogate");
                }
                i++;
                continue;
            }
            if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(
                        name + " contains an unpaired UTF-16 surrogate");
            }
            if (Character.isISOControl(current)) {
                throw new IllegalArgumentException(name + " contains a control character");
            }
        }
        return value;
    }

    private static void requireOrder(long tick, int sequence, String name) {
        if (tick < 0 || sequence < 0) {
            throw new IllegalArgumentException(name + " order must be nonnegative");
        }
    }

    private static Principal requireKnown(Principal principal, String name) {
        Objects.requireNonNull(principal, name);
        if (!principal.known()) {
            throw new IllegalArgumentException(name + " must be explicit");
        }
        return principal;
    }

    private static <T> List<T> immutableNonempty(List<T> values, String name) {
        List<T> copy = List.copyOf(Objects.requireNonNull(values, name));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return copy;
    }

    private static <T extends Comparable<? super T>> List<T> orderedDistinct(
            List<T> values, String name) {
        List<T> copy = List.copyOf(Objects.requireNonNull(values, name));
        for (int i = 1; i < copy.size(); i++) {
            if (copy.get(i - 1).compareTo(copy.get(i)) >= 0) {
                throw new IllegalArgumentException(
                        name + " must be strictly ordered and duplicate-free");
            }
        }
        return copy;
    }

    private static <T extends Comparable<? super T>> List<T> orderedDistinctNonempty(
            List<T> values, String name) {
        List<T> copy = orderedDistinct(values, name);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return copy;
    }
}
