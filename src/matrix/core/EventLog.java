package matrix.core;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Append-only sink: the narrative, one line per event.
 * Owns its encoding end to end: UTF-8 PrintStream, Locale.ROOT formatting,
 * explicit \n — the line is byte-identical on every machine, locale and OS
 * (D-010; skeptic residual S1 closed by owning the charset here rather
 * than trusting the platform default of whoever wires the stream).
 */
public final class EventLog {
    private final PrintStream out;

    public EventLog(OutputStream sink) {
        this.out = new PrintStream(sink, true, StandardCharsets.UTF_8);
    }

    public void onEvent(Event e) {
        out.print(String.format(Locale.ROOT, "[%06d] %-5s %s\n", e.tick(), e.sev(), e.msg()));
    }
}
