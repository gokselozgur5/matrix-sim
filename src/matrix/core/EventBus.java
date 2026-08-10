package matrix.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Synchronous pub/sub spine of observability. Deterministic by construction:
 * subscriptions happen at boot and seal on the first publish; reentrant
 * publishing is a hard error; a throwing listener fails the run loudly.
 * Accidental semantics are how replays die (skeptic finding, 2026-08-10).
 */
public final class EventBus {
    private final List<Consumer<Event>> listeners = new ArrayList<>();
    private boolean sealed = false;
    private boolean publishing = false;

    public void subscribe(Consumer<Event> listener) {
        if (sealed) {
            throw new IllegalStateException("EventBus is sealed: subscribe at boot, before the first publish");
        }
        listeners.add(listener);
    }

    public void publish(Event event) {
        if (publishing) {
            throw new IllegalStateException("reentrant publish: a listener may not publish while receiving");
        }
        sealed = true;
        publishing = true;
        try {
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).accept(event);
            }
        } finally {
            publishing = false;
        }
    }
}
