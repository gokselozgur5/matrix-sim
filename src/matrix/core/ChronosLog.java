package matrix.core;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * The append-only record that makes a run reconstructible (D-023 stage 1,
 * crown #177): EventLog records the story, ChronosLog records the inputs.
 * One JSONL line per record — genesis (seed, version, config fingerprint),
 * operator commands, epoch boundaries, and each flushed WorldEvent batch
 * as counts. Counts are evidence for the fold, not resurrection payloads:
 * in the coarse+seeded model, re-execution regenerates the events.
 *
 * Write-only at stage 1. It draws nothing, mutates nothing, and none of
 * it enters the digest chain — the chain is the referee this log must
 * satisfy. Byte ownership mirrors EventLog: UTF-8 PrintStream,
 * Locale.ROOT, explicit \n (D-010; D-020 grammar law).
 */
public final class ChronosLog {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final PrintStream out;

    public ChronosLog(OutputStream sink) {
        this.out = new PrintStream(sink, true, StandardCharsets.UTF_8);
    }

    /** The first line, before tick 1: which universe this is a recording of. */
    public void genesis(long seed, int version) {
        out.print("{\"chronos\":\"genesis\",\"seed\":" + seed + ",\"version\":" + version
                + ",\"config\":\"" + configFingerprint() + "\"}\n");
    }

    /** An operator command at its tick — the only true external nondeterminism. */
    public void command(long tick, String cmd) {
        out.print("{\"chronos\":\"command\",\"tick\":" + tick
                + ",\"cmd\":\"" + escape(cmd) + "\"}\n");
    }

    /** An epoch boundary at its tick: {@code "reload"} or {@code "treaty"}. */
    public void boundary(long tick, String kind) {
        out.print("{\"chronos\":\"boundary\",\"tick\":" + tick
                + ",\"kind\":\"" + kind + "\"}\n");
    }

    /**
     * One flushed batch, counts only. All-zero batches are skipped: flush
     * runs every tick and most ticks move nothing — the file stays
     * proportionate to what actually happened.
     */
    public void onFlush(long tick, int spawns, int removes, int replaces) {
        if (spawns == 0 && removes == 0 && replaces == 0) {
            return;
        }
        out.print("{\"chronos\":\"flush\",\"tick\":" + tick + ",\"spawns\":" + spawns
                + ",\"removes\":" + removes + ",\"replaces\":" + replaces + "}\n");
    }

    /**
     * SHA-256 over Config's public static final fields, serialized as
     * name=value lines in field-name order via reflection. Declaration
     * order from reflection is not a contract; the sort is. Stable across
     * runs and JVMs, changes exactly when Config changes — a replayed
     * recording can refuse a universe with different physics.
     */
    private static String configFingerprint() {
        Field[] fields = Config.class.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        StringBuilder canon = new StringBuilder();
        for (Field f : fields) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m)) {
                try {
                    canon.append(f.getName()).append('=').append(f.get(null)).append('\n');
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Config refused reflection: " + f.getName(), e);
                }
            }
        }
        byte[] digest = sha256().digest(canon.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(HEX[(b >>> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return hex.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing — no JVM ships without it", e);
        }
    }

    /** Minimal JSON string escape — operator input is text, not trusted grammar. */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
