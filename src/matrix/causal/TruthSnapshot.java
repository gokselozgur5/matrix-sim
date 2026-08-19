package matrix.causal;

import java.util.List;
import java.util.Objects;

/**
 * The immutable root-owned view of facts eligible for one tick's delivery.
 *
 * <p>This is not {@code matrix.core.Snapshot}: it cannot restore a world and
 * does not retain every canonical byte. It is also not a percept. Minds never
 * receive this object; a later delivery phase may project selected entries
 * into mind-visible receipts only after recording its root-side audit.
 *
 * <p>The eligibility rule is closed and named. Version one admits exactly the
 * connected resident's own living-brain fact plus pill and position facts
 * about that resident's present Matrix body. Every admitted subject has the
 * complete three-entry group in the rule's order. Empty means no subject met the rule;
 * it is a valid, explicit snapshot rather than a missing value.
 *
 * <p>Construction rejects rather than repairs malformed input. The producer
 * must assign dense sequence numbers after canonical subject ordering, name
 * the correct source for every predicate, and use one source tick throughout.
 * That makes an unstable producer fail at the boundary instead of silently
 * sorting a lie into a plausible snapshot.
 */
public record TruthSnapshot(long tick, EligibilityRule eligibility,
                            List<CausalRecord.TruthEntry> entries) {

    /** The one accepted eligibility rule; a new rule is an ontology change. */
    public enum EligibilityRule {
        CONNECTED_RESIDENT_SELF_V1(List.of(Predicate.BRAIN_ALIVE,
                Predicate.AVATAR_PILL, Predicate.AVATAR_POSITION_CM));

        private final List<Predicate> predicates;

        EligibilityRule(List<Predicate> predicates) {
            this.predicates = List.copyOf(predicates);
        }

        /** Complete fact order required once a subject is eligible. */
        public List<Predicate> predicates() {
            return predicates;
        }
    }

    /** Closed fact vocabulary and the root-side system that supplied it. */
    public enum Predicate {
        BRAIN_ALIVE("brain.alive", "real-world"),
        AVATAR_PILL("avatar.pill", "matrix-world"),
        AVATAR_POSITION_CM("avatar.position_cm", "matrix-world");

        private final CausalRecord.Symbol symbol;
        private final CausalRecord.Principal provenance;
        private final CausalRecord.Fact trueFact;
        private final CausalRecord.Fact falseFact;
        private final CausalRecord.Fact blueFact;
        private final CausalRecord.Fact redFact;

        Predicate(String symbol, String provenance) {
            this.symbol = new CausalRecord.Symbol(symbol);
            this.provenance = new CausalRecord.Principal(
                    CausalRecord.PrincipalKind.SYSTEM, provenance);
            this.trueFact = rawFact("true");
            this.falseFact = rawFact("false");
            this.blueFact = rawFact("blue");
            this.redFact = rawFact("red");
        }

        public CausalRecord.Symbol symbol() {
            return symbol;
        }

        public CausalRecord.Principal provenance() {
            return provenance;
        }

        public CausalRecord.Fact fact(String value) {
            CausalRecord.Payload payload = new CausalRecord.Payload(value);
            if (!accepts(payload)) {
                throw new IllegalArgumentException(
                        "truth value violates predicate " + symbol.value());
            }
            return switch (value) {
                case "true" -> trueFact;
                case "false" -> falseFact;
                case "blue" -> blueFact;
                case "red" -> redFact;
                default -> new CausalRecord.Fact(symbol, payload);
            };
        }

        private boolean accepts(CausalRecord.Payload payload) {
            String value = payload.text();
            return switch (this) {
                case BRAIN_ALIVE -> value.equals("true") || value.equals("false");
                case AVATAR_PILL -> value.equals("blue") || value.equals("red");
                case AVATAR_POSITION_CM -> canonicalPosition(value);
            };
        }

        private CausalRecord.Fact rawFact(String value) {
            return new CausalRecord.Fact(symbol, new CausalRecord.Payload(value));
        }

        private static Predicate named(CausalRecord.Symbol symbol) {
            for (Predicate predicate : values()) {
                if (predicate.symbol.equals(symbol)) {
                    return predicate;
                }
            }
            throw new IllegalArgumentException(
                    "truth predicate is not perception-eligible: " + symbol.value());
        }
    }

    public TruthSnapshot {
        if (tick < 0) {
            throw new IllegalArgumentException("truth snapshot tick must be nonnegative");
        }
        Objects.requireNonNull(eligibility, "truth eligibility rule");
        entries = List.copyOf(Objects.requireNonNull(entries, "truth entries"));

        List<Predicate> predicates = eligibility.predicates();
        if (entries.size() % predicates.size() != 0) {
            throw new IllegalArgumentException(
                    "truth snapshot must carry one complete fact group per subject");
        }

        int previousSubject = -1;
        for (int i = 0; i < entries.size(); i++) {
            CausalRecord.TruthEntry entry = entries.get(i);
            if (entry.tick() != tick || entry.sequence() != i) {
                throw new IllegalArgumentException(
                        "truth entries must use the snapshot tick and dense canonical sequence");
            }
            if (entry.subject().kind() != CausalRecord.PrincipalKind.HUMAN) {
                throw new IllegalArgumentException(
                        "connected-resident truth requires a Human subject");
            }
            int subject = humanOrdinal(entry.subject());

            int position = i % predicates.size();
            Predicate predicate = Predicate.named(entry.fact().predicate());
            Predicate expected = predicates.get(position);
            if (predicate != expected || !expected.accepts(entry.fact().value())
                    || !entry.provenance().equals(expected.provenance())) {
                throw new IllegalArgumentException(
                        "truth fact order, value, or provenance violates the eligibility rule");
            }

            if (position == 0) {
                if (subject <= previousSubject) {
                    throw new IllegalArgumentException(
                            "truth subjects must be strictly canonical and duplicate-free");
                }
                previousSubject = subject;
            } else if (subject != previousSubject) {
                throw new IllegalArgumentException(
                        "one truth fact group cannot cross subject identities");
            }
        }
    }

    /** Explicit no-eligible-subject value for the named rule and source tick. */
    public static TruthSnapshot empty(long tick) {
        return new TruthSnapshot(tick, EligibilityRule.CONNECTED_RESIDENT_SELF_V1,
                List.of());
    }

    /** Number of eligible residents, not number of entries. */
    public int subjects() {
        return entries.size() / eligibility.predicates().size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Human identity is the immutable growth ordinal, with one canonical spelling. */
    private static int humanOrdinal(CausalRecord.Principal subject) {
        String key = subject.key().value();
        if (!key.startsWith("human-") || key.length() == "human-".length()) {
            throw new IllegalArgumentException("truth subject must use human-N identity");
        }
        String ordinal = key.substring("human-".length());
        if (!canonicalNonnegativeInt(ordinal)) {
            throw new IllegalArgumentException("truth subject must use canonical Human ordinal");
        }
        return Integer.parseInt(ordinal);
    }

    /** One comma, two strict decimal coordinates, no alternate byte spelling. */
    private static boolean canonicalPosition(String value) {
        int comma = value.indexOf(',');
        return comma > 0 && comma == value.lastIndexOf(',')
                && comma < value.length() - 1
                && canonicalNonnegativeInt(value.substring(0, comma))
                && canonicalNonnegativeInt(value.substring(comma + 1));
    }

    private static boolean canonicalNonnegativeInt(String value) {
        if (value.isEmpty() || value.length() > 10
                || value.length() > 1 && value.charAt(0) == '0') {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
