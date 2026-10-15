package org.fidonet.binkp.common.events;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.events.EventBus;
import org.fidonet.events.HasEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Records lifecycle events on the production event bus, separately for each session. */
public final class SessionEventProbe extends HasEventBus implements AutoCloseable {
    private final EventBus bus = getEventBus();
    private final ConcurrentMap<SessionContext, Lifecycle> sessions = new ConcurrentHashMap<>();
    private final ConnectedEventHandler connected = new ConnectedEventHandler() {
        @Override
        public void onEventHandle(ConnectedEvent event) {
            Lifecycle lifecycle = session(event.getSessionContext());
            lifecycle.events.add("connected");
            lifecycle.connected.countDown();
        }
    };
    private final DisconnectedEventHandler disconnected = new DisconnectedEventHandler() {
        @Override
        public void onEventHandle(DisconnectedEvent event) {
            Lifecycle lifecycle = session(event.getSessionContext());
            lifecycle.events.add("disconnected");
            lifecycle.disconnected.countDown();
        }
    };

    public SessionEventProbe() {
        bus.subscribe(connected);
        bus.subscribe(disconnected);
    }

    public List<String> awaitLifecycle(SessionContext context) throws InterruptedException {
        Lifecycle lifecycle = session(context);
        if (!lifecycle.connected.await(2, TimeUnit.SECONDS)
                || !lifecycle.disconnected.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("Incomplete session lifecycle: " + lifecycle.events);
        }
        return new ArrayList<>(lifecycle.events);
    }

    private Lifecycle session(SessionContext context) {
        return sessions.computeIfAbsent(context, ignored -> new Lifecycle());
    }

    @Override
    public void close() {
        bus.unsubscribe(connected);
        bus.unsubscribe(disconnected);
    }

    private static final class Lifecycle {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch disconnected = new CountDownLatch(1);
    }
}
