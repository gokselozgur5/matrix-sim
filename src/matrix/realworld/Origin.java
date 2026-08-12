package matrix.realworld;

/**
 * Which door a freed mind walked out through — the closed vocabulary every
 * liberation is tagged with, and the one place a new door has to declare
 * itself.
 *
 * <p>It was a bare {@code String} from #187 until #831: chosen when there
 * was one door, still a String when D-033 opened the second. An unbounded
 * tag cannot be grouped without guessing the vocabulary, so anything that
 * counts the census by door either hardcodes today's spellings and
 * silently under-counts the door it has not heard of, or refuses to count
 * at all — which is what the census did for two merged PRs. The ZION
 * line's per-door columns are generated from {@link #values()}, so
 * {@code census} equals the doors summed by construction, and a third door
 * cannot be added without the line growing a column for it.
 *
 * <p>The tag is the printed spelling and it is the one the doors already
 * used. An instrument column is a byte contract (D-020), so the name lives
 * on the constant rather than at the format string that emits it.
 */
public enum Origin {
    /** The treaty's open door (#187): a sleeper opts out, the link closes clean, the brain lives. */
    TREATY("treaty"),
    /** The Kid's own exit (D-033): acceptance overflows its threshold and the mind walks out unaided. */
    SELFSUB("selfsub");

    private final String tag;

    Origin(String tag) {
        this.tag = tag;
    }

    /** The spelling that goes on an instrument line — lowercase ASCII, no locale in it. */
    public String tag() {
        return tag;
    }
}
