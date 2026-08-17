import matrix.Simulation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * <p>{@link #measure} is the scan without the printing, so a second
 * instrument can ask what the film did without re-implementing the needles.
 * {@link DocLint} is that second instrument: it compares these ticks with
 * the ones the documents publish. One scan, two readers — the beat list is
 * a contract with the event log and belongs in exactly one place.
 *
 * Usage: java -cp out:probes/out ArcBeats [ticks] [seed]
 */
public final class ArcBeats {

    private record Beat(String name, String needle) {}

    /** One beat as the run found it; {@code tick} is -1 when the needle never appeared. */
    public record Found(String name, long tick) {}

    /** One run's beats, in the order the film demands, plus what the run emitted. */
    public record Arc(List<Found> beats, long seed, long ticks, int lines) {}

    private static final List<Beat> BEATS = List.of(
            new Beat("birth", "The One is born"),
            new Beat("refusal", "I DIDN'T"),
            new Beat("overflow", "SMITH OVERFLOW"),
            new Beat("flatline", "Thomas A. Anderson flatlined"),
            new Beat("peace", "The One: \"Peace.\""),
            new Beat("reboot", "REBOOT v"),
            new Beat("door", "open door tally"),
            new Beat("second_birth", "The One is born"));

    /** Runs a private universe and reports where each beat landed. Prints nothing. */
    public static Arc measure(long ticks, long seed) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 22);
        new Simulation(seed, buf, null).run(ticks);
        String[] lines = buf.toString(StandardCharsets.UTF_8).split("\n");

        List<Found> found = new ArrayList<>(BEATS.size());
        int cursor = 0;
        for (Beat beat : BEATS) {
            long at = -1;
            while (cursor < lines.length) {
                String line = lines[cursor++];
                if (line.startsWith("[") && line.contains(beat.needle())) {
                    int close = line.indexOf(']');
                    at = Long.parseLong(line.substring(1, close));
                    break;
                }
            }
            found.add(new Found(beat.name(), at));
        }
        return new Arc(List.copyOf(found), seed, ticks, lines.length);
    }

    public static void main(String[] args) {
        matrix.Streams.utf8();
        long ticks = args.length > 0 ? Long.parseLong(args[0]) : 6_000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;

        Arc arc = measure(ticks, seed);

        long lastTick = -1;
        boolean ok = true;
        for (Found beat : arc.beats()) {
            if (beat.tick() < 0) {
                ok = false;
                System.out.println("MISSING " + beat.name());
            } else {
                System.out.println("BEAT " + beat.name() + " t=" + beat.tick());
                if (beat.tick() < lastTick) {
                    ok = false;
                    System.out.println("OUT_OF_ORDER " + beat.name());
                }
                lastTick = beat.tick();
            }
        }
        // HOW MANY BEATS WERE COMPARED (#1421, one of #1373's twenty-five). The
        // loop above does not run on an empty list, `ok` stays true, and this
        // probe used to report that the film played in order having compared
        // nothing. The needles are a hand-written list, so emptying them is a
        // plausible refactor rather than a hypothetical.
        //
        // The count rides the CENSUS and the emptiness rides the VERDICT
        // (#1221): the number of beats moves whenever D-036's list moves, and a
        // count on an exact-line row is a number people edit until the lane is
        // quiet — while a run that compared nothing must not be able to print a
        // clean film's line.
        int beats = arc.beats().size();
        System.out.println("ARC seed=" + arc.seed() + " ticks=" + arc.ticks()
                + " lines=" + arc.lines() + " beats=" + beats);
        boolean held = ok && beats > 0;
        Probes.leave(held
                ? "VERDICT BEATS_IN_ORDER beats_none=0"
                : "VERDICT ARC_BROKEN beats_none=" + (beats == 0 ? 1 : 0), held);
    }

    private ArcBeats() {}
}
