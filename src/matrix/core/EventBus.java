package matrix.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EventBus {
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    public void subscribe(Consumer<Event> l) {
        listeners.add(l);
    }

    public void publish(Event e) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(e);
        }
    }
}
