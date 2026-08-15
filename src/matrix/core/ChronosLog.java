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
 * operator commands, epoch boundaries, births, and each flushed WorldEvent
 * batch as counts. Counts are evidence for the fold, not resurrection
 * payloads: in the coarse+seeded model, re-execution regenerates the events.
 *
 * Write-only at stage 1. It draws nothing, mutates nothing, and none of
 * it enters the digest chain — the chain is the referee this log must
 * satisfy. Byte ownership mirrors EventLog: UTF-8 PrintStream,
 * Locale.ROOT, explicit \n (D-010; D-020 grammar law).
 */
public final class ChronosLog {
    // Read-only: a lookup table, never written after the class loads, so one per
    // process is one per world (#1148).
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

    /**
     * One birth as the record states it: which {@code family} of being came
     * to exist, at which {@code tick}, under which {@code name} — the
     * name-at-birth — in which {@code rack} unit, as the universe's
     * {@code id}-th mind.
     *
     * <p>{@code name} is not a duplicate of the current name. The ruling of
     * 2026-08-11 keys derivation to the birth EVENT and never to the name a
     * soul answers to today, so this field is written once, at the birth,
     * and never updated: renaming is not rebirth, and identity papers
     * cannot launder fate. Everything downstream that wants a birth-invariant
     * input reads it from here.
     *
     * <p>{@code rack} and {@code id} are the two fields #847 found missing.
     * The record's job is to hold the derivation's INPUTS, not its outcome —
     * D-023's founding argument, that a coarse seeded model records what went
     * in and re-executes to get what came out. The daemon's key mixes five
     * facts; the seed is on genesis and the other four are these, so a reader
     * holding nothing but the file can now re-derive the key the daemon used
     * and say why one mind woke and another did not. A key stored instead
     * would be a derived value in a record of inputs, and the day the mixing
     * function moves, every old recording would state a number no build
     * reproduces.
     *
     * <p>{@code rack} is the empty string for a mind grown with no slot, and
     * that is a value rather than an absence: it is exactly what the
     * derivation reads for the free-born, so writing it down is writing down
     * what went in. {@code id} is the farm's growth ordinal ({@code Human.id})
     * and never the avatar's world handle.
     */
    public record Birth(long tick, String name, String family, String rack, int id) {}

    /**
     * A birth at its tick. The recorder OBSERVES: the record is written
     * where the world has already decided someone exists, it draws nothing,
     * it mutates nothing, and no run ever reads it back — a recording-on run
     * and a recording-off run walk the same universe, link for link.
     *
     * <p>{@code rack} and {@code id} are appended after {@code family}, in
     * that order. Field order is the grammar: a reader written against the
     * three-field form still finds every field it knew, at the key it knew it
     * by, and the two it does not know are past the end.
     */
    public void birth(Birth b) {
        out.print("{\"chronos\":\"birth\",\"tick\":" + b.tick()
                + ",\"name\":\"" + escape(b.name())
                + "\",\"family\":\"" + escape(b.family())
                + "\",\"rack\":\"" + escape(b.rack())
                + "\",\"id\":" + b.id() + "}\n");
    }

    /** An epoch boundary at its tick: {@code "reload"} or {@code "treaty"}. */
    public void boundary(long tick, String kind) {
        out.print("{\"chronos\":\"boundary\",\"tick\":" + tick
                + ",\"kind\":\"" + kind + "\"}\n");
    }

    /**
     * The epoch-boundary marker — the crown's deferred {@code ChronosLog
     * o-- Snapshot} edge, landed at stage 4 (#128). The certificate of
     * the closing epoch enters the record: tick, the epoch it seals
     * (the pre-bump version), the sha over the retained walk, and the
     * byte count — never the payload. A seal, not a save-game (crown
     * #179): the fold re-takes the same walk at the same point and must
     * find the same sha. Written BEFORE the boundary line and BEFORE
     * the purge touches the world — the record leads, the world follows.
     */
    public void snapshot(Snapshot boundary) {
        out.print("{\"chronos\":\"snapshot\",\"tick\":" + boundary.tick()
                + ",\"epoch\":" + boundary.version()
                + ",\"sha\":\"" + boundary.sha256Hex()
                + "\",\"bytes\":" + boundary.bytes().length + "}\n");
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
     * recording can refuse a universe with different physics. Public for
     * exactly that refusal: ReplayHarness is the reader (crown #178) and
     * recomputes the writer's fingerprint rather than invent its own.
     */
    public static String configFingerprint() {
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
