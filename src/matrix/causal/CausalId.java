package matrix.causal;

import java.util.Locale;
import java.util.Objects;

/**
 * The six non-interchangeable identity domains in D-066's Human causal path.
 *
 * <p>An identity says only <em>where in canonical causal order</em> a value
 * was born: its domain, tick, and within-scope sequence. Percept scope is the
 * Subject carried beside the ID in {@code PerceptReceipt}/{@code PerceptRef};
 * the bare spelling is deliberately not a globally unique percept key. It
 * carries no subject name, object reference, hidden truth, audit provenance,
 * wall clock, random value, or JVM-shaped hash. Later records own those
 * meanings. Keeping them out of the identity is what lets a mind-visible
 * percept keep the same identity when only root-side audit facts differ.
 *
 * <p>The nested records share one strict textual grammar while remaining
 * distinct Java types. Code that asks for a {@link Commit} cannot be handed a
 * {@link Percept}; equality across records is false; and each record exposes
 * {@link Comparable} only for its own domain. The canonical spelling is
 * {@code domain/tick/sequence;} — the final terminator makes every valid
 * spelling prefix-free, and {@link #parse(String)} consumes the whole input.
 *
 * <p>This type does not allocate identities. {@link matrix.Simulation} will
 * own that policy when the canonical phase spine lands. In particular,
 * percept allocation must obey #1693's visible-input-only ordering law.
 */
public sealed interface CausalId permits CausalId.Percept, CausalId.Choice,
        CausalId.Intent, CausalId.Commit, CausalId.Effect, CausalId.Cause {

    /** The canonical tick at which this identity was allocated. */
    long tick();

    /** The zero-based sequence inside this identity's domain and tick. */
    int sequence();

    /** The stable lowercase domain tag written into the canonical spelling. */
    String domain();

    /** The strict, terminated, prefix-free spelling used at record boundaries. */
    default String canonical() {
        return domain() + "/" + tick() + "/" + sequence() + ";";
    }

    /**
     * Parses one complete canonical identity, returning its exact domain type.
     * Aliases, whitespace, signs, leading zeroes, suffixes, and unknown tags
     * are refused rather than normalized into another identity.
     */
    static CausalId parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("a causal id cannot be null");
        }
        int first = text.indexOf('/');
        int second = first < 0 ? -1 : text.indexOf('/', first + 1);
        if (!text.endsWith(";") || first <= 0 || second <= first + 1
                || second >= text.length() - 2 || text.indexOf('/', second + 1) >= 0
                || text.indexOf(';') != text.length() - 1) {
            throw malformed(text);
        }

        String tag = text.substring(0, first);
        long tick;
        int sequence;
        try {
            tick = Long.parseLong(text.substring(first + 1, second));
            sequence = Integer.parseInt(text.substring(second + 1, text.length() - 1));
        } catch (NumberFormatException e) {
            throw malformed(text);
        }

        CausalId id = switch (tag) {
            case "percept" -> new Percept(tick, sequence);
            case "choice" -> new Choice(tick, sequence);
            case "intent" -> new Intent(tick, sequence);
            case "commit" -> new Commit(tick, sequence);
            case "effect" -> new Effect(tick, sequence);
            case "cause" -> new Cause(tick, sequence);
            default -> throw new IllegalArgumentException("unknown causal id domain: " + tag);
        };
        if (!id.canonical().equals(text)) {
            throw new IllegalArgumentException("non-canonical causal id: " + text);
        }
        return id;
    }

    private static IllegalArgumentException malformed(String text) {
        return new IllegalArgumentException("malformed causal id: " + text);
    }

    private static void validate(long tick, int sequence) {
        if (tick < 0) {
            throw new IllegalArgumentException("causal id tick must be nonnegative: " + tick);
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("causal id sequence must be nonnegative: " + sequence);
        }
    }

    private static int order(CausalId left, CausalId right) {
        int byTick = Long.compare(left.tick(), right.tick());
        return byTick != 0 ? byTick : Integer.compare(left.sequence(), right.sequence());
    }

    private static <T extends CausalId> T parseAs(String text, Class<T> domain) {
        CausalId id = parse(text);
        if (!domain.isInstance(id)) {
            throw new IllegalArgumentException("causal id belongs to " + id.domain()
                    + ", not " + domain.getSimpleName().toLowerCase(Locale.ROOT));
        }
        return domain.cast(id);
    }

    /** Identity of one mind-visible percept receipt. */
    record Percept(long tick, int sequence) implements CausalId, Comparable<Percept> {
        public Percept {
            validate(tick, sequence);
        }

        @Override public String domain() { return "percept"; }

        @Override public int compareTo(Percept other) {
            return order(this, Objects.requireNonNull(other, "other percept"));
        }

        public static Percept parse(String text) { return parseAs(text, Percept.class); }
    }

    /** Identity of one resident choice before it requests an act. */
    record Choice(long tick, int sequence) implements CausalId, Comparable<Choice> {
        public Choice {
            validate(tick, sequence);
        }

        @Override public String domain() { return "choice"; }

        @Override public int compareTo(Choice other) {
            return order(this, Objects.requireNonNull(other, "other choice"));
        }

        public static Choice parse(String text) { return parseAs(text, Choice.class); }
    }

    /** Identity of an immutable act proposal. */
    record Intent(long tick, int sequence) implements CausalId, Comparable<Intent> {
        public Intent {
            validate(tick, sequence);
        }

        @Override public String domain() { return "intent"; }

        @Override public int compareTo(Intent other) {
            return order(this, Objects.requireNonNull(other, "other intent"));
        }

        public static Intent parse(String text) { return parseAs(text, Intent.class); }
    }

    /** Identity of a root-validated committed action. */
    record Commit(long tick, int sequence) implements CausalId, Comparable<Commit> {
        public Commit {
            validate(tick, sequence);
        }

        @Override public String domain() { return "commit"; }

        @Override public int compareTo(Commit other) {
            return order(this, Objects.requireNonNull(other, "other commit"));
        }

        public static Commit parse(String text) { return parseAs(text, Commit.class); }
    }

    /** Identity of one classified canonical effect. */
    record Effect(long tick, int sequence) implements CausalId, Comparable<Effect> {
        public Effect {
            validate(tick, sequence);
        }

        @Override public String domain() { return "effect"; }

        @Override public int compareTo(Effect other) {
            return order(this, Objects.requireNonNull(other, "other effect"));
        }

        public static Effect parse(String text) { return parseAs(text, Effect.class); }
    }

    /** Identity of one explicitly non-choice causal event. */
    record Cause(long tick, int sequence) implements CausalId, Comparable<Cause> {
        public Cause {
            validate(tick, sequence);
        }

        @Override public String domain() { return "cause"; }

        @Override public int compareTo(Cause other) {
            return order(this, Objects.requireNonNull(other, "other cause"));
        }

        public static Cause parse(String text) { return parseAs(text, Cause.class); }
    }
}
