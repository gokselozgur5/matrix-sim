package matrix.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Synchronous pub/sub spine of observability. Listener order is subscription order — deterministic. */
public final class EventBus {
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    public void subscribe(Consumer<Event> listener) {
        listeners.add(listener);
    }

    public void publish(Event event) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(event);
        }
    }
}
