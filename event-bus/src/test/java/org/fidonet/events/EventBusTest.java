package org.fidonet.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Invoke;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class EventBusTest {
    private final LinkedBlockingQueue<LogEvent> errors = new LinkedBlockingQueue<>();
    private Logger logger;
    private Level previousLevel;
    private boolean previousAdditivity;
    private AbstractAppender appender;
    private EventBus bus;

    @BeforeEach
    void captureErrors() {
        logger = (Logger) LogManager.getLogger(EventBus.class);
        previousLevel = logger.getLevel();
        previousAdditivity = logger.isAdditive();
        appender = new AbstractAppender("event-bus-errors",
                                        null,
                                        null,
                                        true,
                                        Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                errors.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ERROR);
        logger.setAdditive(false);
        bus = new EventBus();
    }

    @AfterEach
    void releaseBusAndLogger() {
        bus.shutdown();
        logger.removeAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditivity);
    }

    public static final class TestEvent implements Event {
        @Override
        public String toString() {
            throw new AssertionError("Event contents must not be logged");
        }
    }

    public static final class FailingListener {
        final IllegalStateException failure = new IllegalStateException("subscriber failed");

        @Handler
        public void receive(TestEvent event) {
            throw failure;
        }
    }

    public static final class AsyncFailingListener {
        final IllegalArgumentException failure = new IllegalArgumentException("async subscriber failed");

        @Handler(delivery = Invoke.Asynchronously)
        public void receive(TestEvent event) {
            throw failure;
        }
    }

    public static final class HealthyListener {
        final AtomicInteger deliveries = new AtomicInteger();

        @Handler
        public void receive(TestEvent event) {
            deliveries.incrementAndGet();
        }
    }

    @Test
    void loggedSubscriberFailureWithoutSerializingTheEventAndKeptDelivering() throws Exception {
        FailingListener failing = new FailingListener();
        HealthyListener healthy = new HealthyListener();
        bus.subscribe(failing);
        bus.subscribe(healthy);
        bus.publish(new TestEvent());
        verifyFailure(FailingListener.class, failing.failure);
        assertEquals(1, healthy.deliveries.get());
        bus.unsubscribe(failing);
        bus.publish(new TestEvent());
        assertEquals(2, healthy.deliveries.get());
        assertTrue(errors.isEmpty());
    }

    @Test
    void loggedAsynchronousPublicationFailuresThroughTheSameHandler() throws Exception {
        AsyncFailingListener listener = new AsyncFailingListener();
        bus.subscribe(listener);
        bus.publishAsync(new TestEvent());
        verifyFailure(AsyncFailingListener.class, listener.failure);
    }

    private void verifyFailure(Class<?> listener, Throwable cause) throws Exception {
        LogEvent error = errors.poll(5, TimeUnit.SECONDS);
        assertTrue(error != null, "Subscriber exception must be logged");
        assertEquals(Level.ERROR, error.getLevel());
        String text = error.getMessage().getFormattedMessage();
        assertTrue(text.contains(TestEvent.class.getName()));
        assertTrue(text.contains(listener.getName()));
        assertFalse(text.contains("unknown"));
        assertSame(cause, error.getThrown());
    }
}
