package matrix.core;

import java.io.PrintStream;

/** Append-only sink: the narrative, one line per event. */
public final class EventLog {
    private final PrintStream out;

    public EventLog(PrintStream out) {
        this.out = out;
    }

    public void onEvent(Event e) {
        out.printf("[%06d] %-4s %s%n", e.tick(), e.sev(), e.msg());
    }
}
