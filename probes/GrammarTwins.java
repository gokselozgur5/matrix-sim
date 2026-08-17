import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probe: does the recorder's field order still match the reader's field
 * lists? (#1468)
 *
 * <p>{@code ChronosLine} states the coupling as a virtue: <i>the lists are
 * the writer's, copied deliberately — when the recorder learns a field the
 * reader learns it in the same breath, exactly as it learns a kind.</i> In
 * the same breath is a hope about how somebody will edit two files. Nothing
 * checked it.
 *
 * <p>This is #1053 surviving one layer down. That finding was that one
 * format with two readers is two formats waiting to happen — and they had
 * already drifted: the fold refused a doubled field after #976 while the
 * bench's private copy still took the first occurrence and called the record
 * whole. #1053 removed the second READER. It did not remove the second COPY.
 * Six kinds are spelled out in {@code ChronosLog}'s print statements and
 * spelled again in {@code ChronosLine.grammarOf}, and until this file nothing
 * compared them.
 *
 * <p><b>What breaks, and in which direction.</b> {@code offGrammar} refuses a
 * line carrying a field its kind does not define, so a writer that gains a
 * field the reader has not learned makes every record of that kind refusable
 * — at replay time, long after the world it recorded is gone. #847 adding
 * {@code rack} and {@code id} to {@code birth} is exactly that edit shape.
 * The other direction is quieter: a reader that gains a field the writer does
 * not write passes the gate on every line and fails per-line at the field
 * read, with a message about a value it cannot find rather than a grammar
 * that is wrong. Order divergence is quietest of all, because the reader
 * searches by needle and does not care — while {@code ChronosLog}'s own
 * javadoc calls field order the grammar.
 *
 * <p><b>Why both sides are read as text.</b> The writer's list is not a list:
 * it is a format string with values interleaved, and there is no runtime at
 * which it exists as data. So the comparison is between two source files, and
 * that makes this a probe (contract clause 2 — a probe reads the tree) rather
 * than a test. Escaped quotes are unescaped first, which lets ONE key pattern
 * read both files; full-line comments are dropped before anything is matched,
 * because {@code ChronosLog}'s javadoc names the fields it writes and a
 * checker that finds its own subject in prose is what bit {@code SheetFence}
 * four times.
 *
 * <p><b>What this does not do:</b> merge the two declarations. That is the
 * obvious fix and a bigger argument than a keeper — a shared source would
 * have to be a third thing both derive from, which is a change to D-023's
 * record grammar. A keeper first: it costs one file, it works today, and it
 * makes the merge argument checkable if anyone ever wants to have it.
 */
public final class GrammarTwins {

    private static final String DEFAULT_WRITER = "src/matrix/core/ChronosLog.java";
    private static final String DEFAULT_READER = "src/matrix/core/ChronosLine.java";

    /** The opening of one written record, naming its kind. */
    private static final Pattern WRITTEN_KIND = Pattern.compile("\\{\"chronos\":\"([a-z]+)\"");

    /** A JSON key in the writer's format string: a quoted word followed by a colon. */
    private static final Pattern WRITTEN_KEY = Pattern.compile("\"([a-zA-Z]+)\":");

    /** One arm of {@code grammarOf}: the kind, then its field list. */
    private static final Pattern READ_KIND =
            Pattern.compile("case \"([a-z]+)\" -> List\\.of\\(([^)]*)\\)");

    /** A quoted field name inside that list. */
    private static final Pattern READ_FIELD = Pattern.compile("\"([a-zA-Z]+)\"");

    /**
     * The end of one print statement. Every writer ends its line the same way
     * — the closing brace, the newline, the string, the call — and that is the
     * boundary a key scan must not cross, or the next record's fields join this
     * one's.
     */
    private static final String STATEMENT_END = "}\\n\");";

    public static void main(String[] args) throws IOException {
        // Clause 7 of the probe contract: a probe's first statement pins its
        // streams, because a verdict quoted in a PR must be the bytes another
        // box prints (#836, #965).
        matrix.Streams.utf8();
        String writerPath = DEFAULT_WRITER;
        String readerPath = DEFAULT_READER;
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            if ("--writer".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                writerPath = args[i];
            } else if ("--reader".equals(args[i])) {
                if (++i == args.length) {
                    System.exit(Probes.Outcome.REFUSED.code());
                }
                readerPath = args[i];
            } else if ("--list".equals(args[i])) {
                list = true;
            } else {
                System.exit(Probes.Outcome.REFUSED.code());
            }
        }

        Map<String, List<String>> written = written(source(Path.of(writerPath)));
        Map<String, List<String>> read = read(source(Path.of(readerPath)));

        List<String> kinds = new ArrayList<>(written.keySet());
        for (String kind : read.keySet()) {
            if (!kinds.contains(kind)) {
                kinds.add(kind);
            }
        }

        int writerOnly = 0;
        int readerOnly = 0;
        int diverged = 0;
        int fieldsWritten = 0;
        int fieldsRead = 0;
        for (String kind : kinds) {
            List<String> w = written.get(kind);
            List<String> r = read.get(kind);
            fieldsWritten += w == null ? 0 : w.size();
            fieldsRead += r == null ? 0 : r.size();
            String state;
            if (r == null) {
                writerOnly++;
                state = "WRITER_ONLY";
            } else if (w == null) {
                readerOnly++;
                state = "READER_ONLY";
            } else if (!w.equals(r)) {
                diverged++;
                state = "DIVERGED";
            } else {
                state = "AGREE";
            }
            // Every kind's row is printed on a finding, and all of them under
            // --list: a run that names only the breakage cannot be read as a
            // census, and a reader who wants the census should not have to
            // break something to see one.
            if (list || !"AGREE".equals(state)) {
                System.out.printf("TWIN %-9s %-11s writer=%s reader=%s%n",
                        kind, state, w == null ? "-" : String.join(",", w),
                        r == null ? "-" : String.join(",", r));
            }
        }

        // The populations ride the census and never the verdict (#1221): they
        // move whenever the record learns a kind, and a count on an exact-line
        // row is a number people edit until the lane is quiet.
        System.out.println("TWINS_CENSUS kinds_written=" + written.size()
                + " kinds_read=" + read.size()
                + " fields_written=" + fieldsWritten
                + " fields_read=" + fieldsRead
                + " writer=" + writerPath + " reader=" + readerPath);

        // `read_none=` is on the VERDICT: a reading that found no kind on
        // either side must not print the line a matching pair prints. Nothing
        // read is the finding, not a clean result over an empty set (#1207).
        boolean read0 = written.isEmpty() || read.isEmpty();
        boolean held = writerOnly == 0 && readerOnly == 0 && diverged == 0 && !read0;
        Probes.leave(String.format(
                "VERDICT GRAMMAR_TWINS_AGREE writer_only=%d reader_only=%d diverged=%d read_none=%d",
                writerOnly, readerOnly, diverged, read0 ? 1 : 0), held);
    }

    /**
     * The file as matchable text: full-line comments dropped, escaped quotes
     * unescaped. Line comments are dropped whole rather than trimmed at
     * {@code //}, because trimming at a marker inside a string literal is how
     * a stripper eats code — and no writer here carries a trailing comment.
     */
    private static String source(Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = raw.strip();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            out.append(raw).append('\n');
        }
        return out.toString().replace("\\\"", "\"");
    }

    /** Each written record's field order, keyed by kind, in the order printed. */
    private static Map<String, List<String>> written(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Matcher kinds = WRITTEN_KIND.matcher(text);
        while (kinds.find()) {
            int end = text.indexOf(STATEMENT_END, kinds.start());
            if (end < 0) {
                // An opening with no ending is not a record this probe may
                // guess at: it is left out, and the kind then shows up as
                // READER_ONLY rather than as a silent agreement.
                continue;
            }
            List<String> fields = new ArrayList<>();
            Matcher keys = WRITTEN_KEY.matcher(text.substring(kinds.start(), end));
            while (keys.find()) {
                fields.add(keys.group(1));
            }
            out.put(kinds.group(1), fields);
        }
        return out;
    }

    /** Each read grammar's field list, keyed by kind, in declaration order. */
    private static Map<String, List<String>> read(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Matcher arms = READ_KIND.matcher(text);
        while (arms.find()) {
            List<String> fields = new ArrayList<>();
            Matcher names = READ_FIELD.matcher(arms.group(2));
            while (names.find()) {
                fields.add(names.group(1));
            }
            out.put(arms.group(1), fields);
        }
        return out;
    }

    private GrammarTwins() {}
}
