package org.fidonet.jftn.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SchedulerLifecycleTest {
    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void supportsImmediateAndDeferredTasksAndRepeatedStop() throws Exception {
        Scheduler scheduler = new Scheduler();
        scheduler.stop();
        scheduler.start();
        try {
            CountDownLatch immediate = new CountDownLatch(1), deferred = new CountDownLatch(1);
            String id = scheduler.schedule("0 0 1 1 *", immediate::countDown, true);
            assertTrue(immediate.await(5, TimeUnit.SECONDS));
            String later = scheduler.schedule("0 0 1 1 *", deferred::countDown, false);
            assertEquals(1L, deferred.getCount());
            scheduler.reschedule(later, "0 0 2 1 *");
            scheduler.deschedule(later);
            scheduler.deschedule(id);
        } finally {
            scheduler.stop();
            scheduler.stop();
        }
    }
}
