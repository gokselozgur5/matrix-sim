package matrix.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The chronos record's grammar, stated once, for everyone who reads the file
 * {@link ChronosLog} writes.
 *
 * <p>Until #1053 there were two readers. The fold's ({@code ReplayHarness})
 * and the bench's ({@code probes/BirthInputs}), each carrying a private copy
 * of the same field helpers — and they had drifted. #976 taught the fold to
 * refuse a line carrying a field twice; the probe, reading the same records
 * with its own copy, still took the first occurrence and called the record
 * whole. One format with two readers is two formats waiting to happen, so the
 * grammar lives here and both call it.
 *
 * <p>This is a reader for our own recorder's grammar (crown #177), not a
 * general JSON parser. Values are found by needle: {@code "key":} is searched
 * for literally, first match wins, and what follows is unescaped per the
 * writer's escapes and nothing else. First-match reading is honest only
 * because {@link #offGrammar} runs first and refuses the shapes under which
 * a needle search silently reads something other than what the line says — a
 * field the kind does not define, the same field twice, and a colon the
 * recorder would not have spaced.
 */
public final class ChronosLine {

    /**
     * The recorder's field list for one kind, in the order {@link ChronosLog}
     * writes them — null for a kind this grammar does not know, which callers
     * refuse by kind rather than by field. The lists are the writer's, copied
     * deliberately: when the recorder learns a field the reader learns it in
     * the same breath, exactly as it learns a kind.
     */
    private static List<String> grammarOf(String kind) {
        return switch (kind) {
            case "genesis" -> List.of("chronos", "seed", "version", "config");
            case "command" -> List.of("chronos", "tick", "cmd");
            case "snapshot" -> List.of("chronos", "tick", "epoch", "sha", "bytes");
            case "boundary" -> List.of("chronos", "tick", "kind");
            case "birth" -> List.of("chronos", "tick", "name", "family", "rack", "id");
            case "flush" -> List.of("chronos", "tick", "spawns", "removes", "replaces");
            default -> null;
        };
    }

    /**
     * The field gate (#976): walk the line's own top-level keys and name the
     * first one that is off the recorder's grammar, or null when the line is
     * clean. Null is also the answer for a kind this grammar does not know —
     * there is no field list to measure against, and the caller refuses the
     * kind itself.
     *
     * <p>Keys are read structurally — a quoted run followed by a colon, with
     * string contents skipped whole — so a brace or a colon inside a command's
     * text is text, not grammar. An unterminated string ends the walk and is
     * left to the field reads, which already refuse the line for the value
     * they cannot find.
     *
     * <p>Three shapes are named. A field the kind does not define, because the
     * record would state something the reader never looks for. The same field
     * twice, because first-match reading would take one of two answers and the
     * fold would apply it as though it were the only one. And whitespace
     * against the colon (#1053), because that is where the gate and the field
     * reads used to disagree: the gate skips it to recognise the key, while
     * {@link #string} and {@link #number} search for the exact needle
     * {@code "key":} and find nothing — so {@code {"chronos":"birth","name" :
     * "Neo",…}} passed the gate and then read as though it carried no name.
     * The recorder writes {@code "key":value} with nothing between, so a
     * spaced colon is refused by name rather than misread as an absent field.
     *
     * <p>The returned reason carries no line number: the caller knows which
     * line it handed over and says so in its own voice.
     */
    public static String offGrammar(String line, String kind) {
        List<String> grammar = grammarOf(kind);
        if (grammar == null) {
            return null;
        }
        List<String> seen = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == '"') {
                int end = i + 1;
                while (end < line.length() && line.charAt(end) != '"') {
                    end += line.charAt(end) == '\\' ? 2 : 1;
                }
                if (end >= line.length()) {
                    return null;
                }
                int after = end + 1;
                while (after < line.length() && Character.isWhitespace(line.charAt(after))) {
                    after++;
                }
                if (depth == 1 && after < line.length() && line.charAt(after) == ':') {
                    String key = line.substring(i + 1, end);
                    if (after != end + 1
                            || (after + 1 < line.length() && Character.isWhitespace(line.charAt(after + 1)))) {
                        return "field '" + key + "' is spaced from its colon";
                    }
                    if (seen.contains(key)) {
                        return "duplicate field '" + key + "'";
                    }
                    seen.add(key);
                    if (!grammar.contains(key)) {
                        return "unknown field '" + key + "' on kind '" + kind + "'";
                    }
                }
                i = end;
            }
        }
        return null;
    }

    /** Whether the line carries {@code "key":} at all — the absent case, told apart from the unreadable one. */
    public static boolean has(String line, String key) {
        return line.contains("\"" + key + "\":");
    }

    /** First string field named {@code key}, unescaped per the writer's grammar; null when absent. */
    public static String string(String line, String key) {
        String needle = "\"" + key + "\":\"";
        int i = line.indexOf(needle);
        if (i < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + needle.length(); j < line.length(); j++) {
            char c = line.charAt(j);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && j + 1 < line.length()) {
                char e = line.charAt(++j);
                if (e == 'u' && j + 4 < line.length()) {
                    sb.append((char) Integer.parseInt(line.substring(j + 1, j + 5), 16));
                    j += 4;
                } else {
                    sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return null; // unterminated string — treated as absent, callers refuse
    }

    /**
     * First numeric field named {@code key}, as the digits stand on the line;
     * null when the key is absent or its value is not a number. {@link #has}
     * tells the two apart for a caller that reports them differently.
     */
    public static String number(String line, String key) {
        String needle = "\"" + key + "\":";
        int i = line.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        int end = start;
        if (end < line.length() && line.charAt(end) == '-') {
            end++;
        }
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        return end == start ? null : line.substring(start, end);
    }

    private ChronosLine() {}
}
