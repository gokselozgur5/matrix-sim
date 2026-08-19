import matrix.causal.CausalId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Probe: are D-066's six causal identity domains actually different types
 * with one strict, prefix-free grammar?
 *
 * <p>No universe is booted. The probe drives the shipped constructors and
 * parser over boundary values, malformed spellings, every cross-domain pair,
 * and every pair of canonical strings in its generated corpus. Runtime cannot
 * compile a negative Java assignment, so the {@code Comparable<Domain>}
 * signatures remain a source-review fact; this probe tests the complementary
 * boundary that execution can falsify: equality, parsing, ordering, and text.
 *
 * <p>Usage: {@code java -cp out:probes/out CausalIds}. It accepts no flags,
 * needs no seed or tick budget, exits 1 when a property breaks, and spends the
 * shared probe refusal code when an argument is supplied.
 */
public final class CausalIds {

    @FunctionalInterface
    private interface Factory {
        CausalId make(long tick, int sequence);
    }

    @FunctionalInterface
    private interface Parser {
        CausalId parse(String text);
    }

    @FunctionalInterface
    private interface Order {
        int compare(CausalId left, CausalId right);
    }

    private record Domain(String tag, Class<? extends CausalId> type,
                          Factory factory, Parser parser, Order order) {}

    private static final List<Domain> DOMAINS = List.of(
            new Domain("percept", CausalId.Percept.class, CausalId.Percept::new,
                    CausalId.Percept::parse,
                    (a, b) -> ((CausalId.Percept) a).compareTo((CausalId.Percept) b)),
            new Domain("choice", CausalId.Choice.class, CausalId.Choice::new,
                    CausalId.Choice::parse,
                    (a, b) -> ((CausalId.Choice) a).compareTo((CausalId.Choice) b)),
            new Domain("intent", CausalId.Intent.class, CausalId.Intent::new,
                    CausalId.Intent::parse,
                    (a, b) -> ((CausalId.Intent) a).compareTo((CausalId.Intent) b)),
            new Domain("commit", CausalId.Commit.class, CausalId.Commit::new,
                    CausalId.Commit::parse,
                    (a, b) -> ((CausalId.Commit) a).compareTo((CausalId.Commit) b)),
            new Domain("effect", CausalId.Effect.class, CausalId.Effect::new,
                    CausalId.Effect::parse,
                    (a, b) -> ((CausalId.Effect) a).compareTo((CausalId.Effect) b)),
            new Domain("cause", CausalId.Cause.class, CausalId.Cause::new,
                    CausalId.Cause::parse,
                    (a, b) -> ((CausalId.Cause) a).compareTo((CausalId.Cause) b)));

    private static final Map<String, Integer> CASES = new LinkedHashMap<>();
    private static final Map<String, Integer> FAILURES = new LinkedHashMap<>();
    private static final List<String> BREAKS = new ArrayList<>();

    public static void main(String[] args) {
        matrix.Streams.utf8();
        if (args.length != 0) {
            System.err.println("CausalIds takes no arguments");
            System.exit(Probes.Outcome.REFUSED.code());
        }

        List<CausalId> sameCoordinates = new ArrayList<>();
        Set<String> canonical = new LinkedHashSet<>();
        long[] ticks = {0, 1, Long.MAX_VALUE};
        int[] sequences = {0, 1, Integer.MAX_VALUE};

        for (Domain domain : DOMAINS) {
            for (long tick : ticks) {
                for (int sequence : sequences) {
                    CausalId id = domain.factory().make(tick, sequence);
                    String text = id.canonical();
                    check("construct", domain.tag() + "-tag-" + tick + "-" + sequence,
                            id.domain().equals(domain.tag()));
                    check("parse", domain.tag() + "-common-" + tick + "-" + sequence,
                            CausalId.parse(text).equals(id));
                    check("parse", domain.tag() + "-specific-" + tick + "-" + sequence,
                            domain.parser().parse(text).equals(id));
                    check("parse", domain.tag() + "-type-" + tick + "-" + sequence,
                            domain.type().isInstance(CausalId.parse(text)));
                    canonical.add(text);
                }
            }
            rejects("construct", domain.tag() + "-negative-tick",
                    () -> domain.factory().make(-1, 0));
            rejects("construct", domain.tag() + "-negative-sequence",
                    () -> domain.factory().make(0, -1));

            CausalId at = domain.factory().make(7, 3);
            CausalId laterTick = domain.factory().make(8, 0);
            CausalId laterSequence = domain.factory().make(7, 4);
            check("order", domain.tag() + "-equal", domain.order().compare(at, at) == 0);
            check("order", domain.tag() + "-tick", domain.order().compare(at, laterTick) < 0);
            check("order", domain.tag() + "-sequence", domain.order().compare(at, laterSequence) < 0);
            rejects("order", domain.tag() + "-null", () -> domain.order().compare(at, null));
            sameCoordinates.add(domain.factory().make(11, 5));
        }

        for (int i = 0; i < sameCoordinates.size(); i++) {
            for (int j = i + 1; j < sameCoordinates.size(); j++) {
                CausalId left = sameCoordinates.get(i);
                CausalId right = sameCoordinates.get(j);
                check("equality", left.domain() + "-not-" + right.domain(), !left.equals(right));
            }
        }

        List<String> malformed = List.of(
                "", "percept", "percept/0/0", "percept/0/0;;", "percept//0;",
                "percept/0/;", "percept/0/0/1;", "percept/00/0;", "percept/0/00;",
                "percept/+1/0;", "percept/-0/0;", "percept/0/-0;", "unknown/0/0;",
                " percept/0/0;", "percept/0/0; ", "percept/9223372036854775808/0;",
                "percept/0/2147483648;");
        rejects("parse", "null", () -> CausalId.parse(null));
        for (int i = 0; i < malformed.size(); i++) {
            String text = malformed.get(i);
            rejects("parse", "malformed-" + i, () -> CausalId.parse(text));
        }
        for (int i = 0; i < DOMAINS.size(); i++) {
            Domain expected = DOMAINS.get(i);
            Domain other = DOMAINS.get((i + 1) % DOMAINS.size());
            rejects("parse", expected.tag() + "-refuses-" + other.tag(),
                    () -> expected.parser().parse(other.factory().make(0, 0).canonical()));
        }

        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String message = rejectionMessage(() -> CausalId.Intent.parse("percept/0/0;"));
            check("parse", "domain-error-is-locale-root",
                    "causal id belongs to percept, not intent".equals(message));
        } finally {
            Locale.setDefault(originalLocale);
        }

        List<String> strings = List.copyOf(canonical);
        for (int i = 0; i < strings.size(); i++) {
            for (int j = 0; j < strings.size(); j++) {
                if (i != j) {
                    String left = strings.get(i);
                    String right = strings.get(j);
                    check("prefix", "pair-" + i + "-" + j, !right.startsWith(left));
                }
            }
        }

        BREAKS.forEach(System.out::println);
        int cases = CASES.values().stream().mapToInt(Integer::intValue).sum();
        int failures = FAILURES.values().stream().mapToInt(Integer::intValue).sum();
        int domainsMissing = DOMAINS.size() == 6 ? 0 : Math.abs(6 - DOMAINS.size());
        System.out.println("CAUSAL_ID_MEMBERS "
                + String.join(",", DOMAINS.stream().map(Domain::tag).toList()));
        System.out.printf("CAUSAL_ID_CENSUS cases=%d construct=%d parse=%d order=%d equality=%d prefix=%d%n",
                cases, cases("construct"), cases("parse"), cases("order"), cases("equality"), cases("prefix"));
        Probes.leave(String.format(java.util.Locale.ROOT,
                "VERDICT %s domains=6 domains_missing=%d construct_fail=%d parse_fail=%d"
                        + " order_fail=%d equality_fail=%d prefix_fail=%d cases_none=%d",
                failures == 0 && domainsMissing == 0 && cases > 0
                        ? "CAUSAL_IDS_HELD" : "CAUSAL_IDS_BROKEN",
                domainsMissing, failures("construct"), failures("parse"), failures("order"),
                failures("equality"), failures("prefix"), cases == 0 ? 1 : 0),
                failures == 0 && domainsMissing == 0 && cases > 0);
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
            BREAKS.add("CAUSAL_ID_BREAK subject=" + subject + " case=" + name);
        }
    }

    private static void rejects(String subject, String name, Runnable action) {
        check(subject, name, rejectionMessage(action) != null);
    }

    private static String rejectionMessage(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return expected.getMessage();
        }
        return null;
    }

    private CausalIds() {}
}
