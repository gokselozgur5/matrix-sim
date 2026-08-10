package matrix.core;

import java.io.PrintStream;
import java.util.Locale;

/**
 * Append-only sink: the narrative, one line per event.
 * Locale.ROOT and an explicit \n keep the line byte-identical on every
 * machine and locale (D-010) — printf with %n would not.
 */
public final class EventLog {
    private final PrintStream out;

    public EventLog(PrintStream out) {
        this.out = out;
    }

    public void onEvent(Event e) {
        out.print(String.format(Locale.ROOT, "[%06d] %-5s %s\n", e.tick(), e.sev(), e.msg()));
    }
}
