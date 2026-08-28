package matrix.causal;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

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
 * complete three-entry group in the rule's order. Empty means no subject met
 * the rule; it is a valid, explicit snapshot rather than a missing value.
 *
 * <p>The snapshot owns compact primitive arrays in canonical Human-ordinal
 * order. Its immutable {@link #entries()} view derives a typed
 * {@link CausalRecord.TruthEntry} only when a consumer asks for that index.
 * Tick-start capture therefore does not mint hundreds of short-lived record,
 * fact, payload, principal, and string objects before any delivery exists.
 * The derived view is not a second truth source: every byte comes from the
 * owned tick, ordinal, pill, and coordinate arrays.
 */
public final class TruthSnapshot {

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
        BRAIN_ALIVE("brain.alive", "real-world", "true", null),
        AVATAR_PILL("avatar.pill", "matrix-world", "blue", "red"),
        AVATAR_POSITION_CM("avatar.position_cm", "matrix-world", null, null);

        private final CausalRecord.Symbol symbol;
        private final CausalRecord.Principal provenance;
        private final CausalRecord.Fact primaryFact;
        private final CausalRecord.Fact secondaryFact;

        Predicate(String symbol, String provenance,
                String primaryValue, String secondaryValue) {
            this.symbol = new CausalRecord.Symbol(symbol);
            this.provenance = new CausalRecord.Principal(
                    CausalRecord.PrincipalKind.SYSTEM, provenance);
            this.primaryFact = primaryValue == null ? null : rawFact(primaryValue);
            this.secondaryFact = secondaryValue == null ? null : rawFact(secondaryValue);
        }

        private CausalRecord.Fact fact(ResidentPill pill, int xCm, int yCm) {
            return switch (this) {
                case BRAIN_ALIVE -> primaryFact;
                case AVATAR_PILL -> switch (pill) {
                    case BLUE -> primaryFact;
                    case RED -> secondaryFact;
                };
                case AVATAR_POSITION_CM -> rawFact(xCm + "," + yCm);
            };
        }

        private CausalRecord.Fact rawFact(String value) {
            return new CausalRecord.Fact(symbol, new CausalRecord.Payload(value));
        }
    }

    /** Scalar pill spelling accepted by the neutral builder. */
    public enum ResidentPill {
        BLUE,
        RED
    }

    /**
     * Reusable root staging area. It accepts only the V1 scalar schema and
     * keeps arbitrary candidate iteration in numeric ordinal order. A build
     * copies the eligible prefix into a new compact snapshot; a
     * later {@link #begin} can therefore reuse this mutable scratch without
     * changing any snapshot already published.
     */
    public static final class Builder {
        private static final int INITIAL_CAPACITY = 16;

        private int[] ordinals = new int[INITIAL_CAPACITY];
        private byte[] pills = new byte[INITIAL_CAPACITY];
        private int[] xCm = new int[INITIAL_CAPACITY];
        private int[] yCm = new int[INITIAL_CAPACITY];
        private int count;
        private boolean collecting;

        /** Begin one capture, discarding only the previous scratch contents. */
        public void begin() {
            if (collecting) {
                throw new IllegalStateException("truth builder already has an open capture");
            }
            count = 0;
            collecting = true;
        }

        /** Add one subject that already satisfied the root's eligibility conjunction. */
        public void add(int humanOrdinal, ResidentPill pill, int x, int y) {
            if (!collecting) {
                throw new IllegalStateException("truth builder has not begun a capture");
            }
            if (humanOrdinal < 0) {
                throw new IllegalArgumentException("Human ordinal must be nonnegative");
            }
            Objects.requireNonNull(pill, "resident pill");
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("resident coordinates must be nonnegative");
            }
            int insertion = Arrays.binarySearch(ordinals, 0, count, humanOrdinal);
            if (insertion >= 0) {
                throw new IllegalArgumentException(
                        "truth subject ordinal appears more than once: " + humanOrdinal);
            }
            insertion = -insertion - 1;
            ensureCapacity(count + 1);
            if (insertion < count) {
                int moved = count - insertion;
                System.arraycopy(ordinals, insertion, ordinals, insertion + 1, moved);
                System.arraycopy(pills, insertion, pills, insertion + 1, moved);
                System.arraycopy(xCm, insertion, xCm, insertion + 1, moved);
                System.arraycopy(yCm, insertion, yCm, insertion + 1, moved);
            }
            ordinals[insertion] = humanOrdinal;
            pills[insertion] = switch (pill) {
                case BLUE -> (byte) 1;
                case RED -> (byte) 2;
            };
            xCm[insertion] = x;
            yCm[insertion] = y;
            count++;
        }

        /** Publish one immutable value and close this capture. */
        public TruthSnapshot build(long tick) {
            if (!collecting) {
                throw new IllegalStateException("truth builder has no open capture");
            }
            requireTick(tick);
            int[] compactOrdinals = Arrays.copyOf(ordinals, count);
            byte[] compactPills = Arrays.copyOf(pills, count);
            int[] compactX = Arrays.copyOf(xCm, count);
            int[] compactY = Arrays.copyOf(yCm, count);
            collecting = false;
            return new TruthSnapshot(tick, compactOrdinals,
                    compactPills, compactX, compactY);
        }

        private void ensureCapacity(int needed) {
            if (needed <= ordinals.length) {
                return;
            }
            int capacity = ordinals.length;
            while (capacity < needed) {
                capacity = Math.multiplyExact(capacity, 2);
            }
            ordinals = Arrays.copyOf(ordinals, capacity);
            pills = Arrays.copyOf(pills, capacity);
            xCm = Arrays.copyOf(xCm, capacity);
            yCm = Arrays.copyOf(yCm, capacity);
        }
    }

    private final long tick;
    private final int[] ordinals;
    private final byte[] pills;
    private final int[] xCm;
    private final int[] yCm;
    private final List<CausalRecord.TruthEntry> entries;

    private TruthSnapshot(long tick, int[] ordinals, byte[] pills,
            int[] xCm, int[] yCm) {
        requireTick(tick);
        this.tick = tick;
        this.ordinals = ordinals;
        this.pills = pills;
        this.xCm = xCm;
        this.yCm = yCm;
        this.entries = new EntryView();
    }

    /** Explicit no-eligible-subject value for the named rule and source tick. */
    public static TruthSnapshot empty(long tick) {
        return new TruthSnapshot(tick, new int[0], new byte[0], new int[0], new int[0]);
    }

    public long tick() {
        return tick;
    }

    public EligibilityRule eligibility() {
        return EligibilityRule.CONNECTED_RESIDENT_SELF_V1;
    }

    /** Immutable, canonically ordered, lazily derived typed fact view. */
    public List<CausalRecord.TruthEntry> entries() {
        return entries;
    }

    /** Number of eligible residents, not number of derived entries. */
    public int subjects() {
        return ordinals.length;
    }

    public boolean isEmpty() {
        return ordinals.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TruthSnapshot snapshot)) {
            return false;
        }
        return tick == snapshot.tick
                && Arrays.equals(ordinals, snapshot.ordinals)
                && Arrays.equals(pills, snapshot.pills)
                && Arrays.equals(xCm, snapshot.xCm)
                && Arrays.equals(yCm, snapshot.yCm);
    }

    @Override
    public int hashCode() {
        int hash = Long.hashCode(tick);
        hash = 31 * hash + Arrays.hashCode(ordinals);
        hash = 31 * hash + Arrays.hashCode(pills);
        hash = 31 * hash + Arrays.hashCode(xCm);
        return 31 * hash + Arrays.hashCode(yCm);
    }

    /** One read-only projection over the snapshot's owned primitive state. */
    private final class EntryView extends AbstractList<CausalRecord.TruthEntry>
            implements RandomAccess {
        @Override
        public CausalRecord.TruthEntry get(int index) {
            Objects.checkIndex(index, size());
            int factCount = eligibility().predicates().size();
            int subjectIndex = index / factCount;
            Predicate predicate = eligibility().predicates().get(index % factCount);
            ResidentPill pill = switch (pills[subjectIndex]) {
                case 1 -> ResidentPill.BLUE;
                case 2 -> ResidentPill.RED;
                default -> throw new IllegalStateException(
                        "truth snapshot carries an unknown pill code");
            };
            CausalRecord.Principal subject = new CausalRecord.Principal(
                    CausalRecord.PrincipalKind.HUMAN,
                    "human-" + ordinals[subjectIndex]);
            return new CausalRecord.TruthEntry(tick, index, subject,
                    predicate.fact(pill, xCm[subjectIndex], yCm[subjectIndex]),
                    predicate.provenance);
        }

        @Override
        public int size() {
            return Math.multiplyExact(ordinals.length,
                    eligibility().predicates().size());
        }
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("truth snapshot tick must be nonnegative");
        }
    }
}
