import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Probe: the D-036 finish line as a machine verdict.
 *
 * Runs a universe into an in-memory sink, then asserts the film's beats
 * occur IN ORDER in the framed event stream: birth → I DIDN'T → OVERFLOW →
 * the One's flatline → Peace → the open door → reboot → second birth.
 * Prints each beat's tick; a missing or out-of-order beat breaks the
 * verdict. The DoD stops needing eyeballs — grep one line.
 *
 * Usage: java -cp out:probes/out ArcBeats [ticks] [seed]
 */
public final class ArcBeats {

    private record Beat(String name, String needle) {}

    private static final List<Beat> BEATS = List.of(
            new Beat("birth", "The One is born"),
            new Beat("refusal", "I DIDN'T"),
            new Beat("overflow", "SMITH OVERFLOW"),
            new Beat("flatline", "Thomas A. Anderson flatlined"),
            new Beat("peace", "The One: \"Peace.\""),
            new Beat("reboot", "REBOOT v"),
            new Beat("door", "open door tally"),
            new Beat("second_birth", "The One is born"));

    public static void main(String[] args) {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
        new Simulation(seed, buf, null).run(ticks);
        String[] lines = buf.toString(StandardCharsets.UTF_8).split("\n");

        int cursor = 0;
        long lastTick = -1;
        boolean ok = true;
        for (Beat beat : BEATS) {
            long found = -1;
            while (cursor < lines.length) {
                String line = lines[cursor++];
                if (line.startsWith("[") && line.contains(beat.needle())) {
                    int close = line.indexOf(']');
                    found = Long.parseLong(line.substring(1, close));
                    break;
                }
            }
            if (found < 0) {
                ok = false;
                System.out.println("MISSING " + beat.name());
            } else {
                System.out.println("BEAT " + beat.name() + " t=" + found);
                if (found < lastTick) {
                    ok = false;
                    System.out.println("OUT_OF_ORDER " + beat.name());
                }
                lastTick = found;
            }
        }
        System.out.println("ARC seed=" + seed + " ticks=" + ticks + " lines=" + lines.length);
        System.out.println(ok ? "VERDICT BEATS_IN_ORDER" : "VERDICT ARC_BROKEN");
    }

    private ArcBeats() {}
}
