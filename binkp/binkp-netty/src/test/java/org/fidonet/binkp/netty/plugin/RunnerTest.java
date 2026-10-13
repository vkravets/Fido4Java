package org.fidonet.binkp.netty.plugin;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.types.Link;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RunnerTest {
    @Test(timeout = 15000)
    public void concurrentPollsStopOnlyTheirOwnClient() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        ControlledClient first = new ControlledClient(false);
        ControlledClient second = new ControlledClient(false);
        AtomicInteger created = new AtomicInteger();
        Runner runner = new Runner(executor) {
            @Override
            protected Client createClient(Link link) {
                return created.getAndIncrement() == 0 ? first : second;
            }
        };
        try {
            runner.poll(null, context());
            assertTrue(first.started.await(5, TimeUnit.SECONDS));
            runner.poll(null, context());
            assertTrue(second.started.await(5, TimeUnit.SECONDS));

            first.release.countDown();
            executor.tasks.get(0).get(5, TimeUnit.SECONDS);
            assertEquals("First poll must stop its own client", 1, first.stops.get());
            assertEquals("Second client must remain open", 0, second.stops.get());

            second.release.countDown();
            executor.tasks.get(1).get(5, TimeUnit.SECONDS);
            assertEquals(1, first.stops.get());
            assertEquals(1, second.stops.get());
        } finally {
            first.release.countDown();
            second.release.countDown();
            runner.shutdown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void failedConnectionStillStopsItsClient() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        ControlledClient client = new ControlledClient(true);
        Runner runner = new Runner(executor) {
            @Override
            protected Client createClient(Link link) {
                return client;
            }
        };
        try {
            client.release.countDown();
            runner.poll(null, context());
            executor.tasks.get(0).get(5, TimeUnit.SECONDS);
            assertEquals(1, client.stops.get());
        } finally {
            runner.shutdown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void clientCreationFailureDoesNotCauseCleanupNullPointerException() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        Runner runner = new Runner(executor) {
            @Override
            protected Client createClient(Link link) {
                throw new IllegalArgumentException("Invalid connection configuration");
            }
        };
        try {
            runner.poll(null, context());
            // An exception in finally would escape the task and fail Future.get().
            executor.tasks.get(0).get(5, TimeUnit.SECONDS);
        } finally {
            runner.shutdown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static SessionContext context() {
        return new SessionContext(null, new LinksInfo(new ArrayList<>()));
    }

    private static final class RecordingExecutor extends ThreadPoolExecutor {
        private final List<Future<?>> tasks = new ArrayList<>();

        private RecordingExecutor() {
            super(2, 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        public Future<?> submit(Runnable task) {
            Future<?> future = super.submit(task);
            tasks.add(future);
            return future;
        }
    }

    private static final class ControlledClient extends Client {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger stops = new AtomicInteger();
        private final boolean fail;

        private ControlledClient(boolean fail) {
            super(null);
            this.fail = fail;
        }

        @Override
        public void run(SessionContext context) throws Exception {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Test did not release the connection");
            }
            if (fail) {
                throw new IOException("Connection failed");
            }
        }

        @Override
        public boolean isConnect() {
            return false;
        }

        @Override
        public void stop() {
            stops.incrementAndGet();
        }
    }
}
