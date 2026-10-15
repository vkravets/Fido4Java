package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

public class ProcessingTriggerTest {
    @Test
    public void coalescesBurstsAndQueuesOneFollowupWhenRequestedDuringProcessing() {
        Deque<Runnable> jobs = new ArrayDeque<>();
        ProcessingTrigger[] trigger = new ProcessingTrigger[1];
        int[] runs = {0};
        trigger[0] = new ProcessingTrigger(jobs::add, () -> {
            if (++runs[0] == 1) {
                requestBurst(trigger[0]);
            }
        });
        requestBurst(trigger[0]);
        assertEquals(1, jobs.size());
        jobs.remove().run();
        assertEquals(1, runs[0]);
        assertEquals(1, jobs.size());
        jobs.remove().run();
        assertEquals(2, runs[0]);
        assertEquals(0, jobs.size());
    }

    private void requestBurst(ProcessingTrigger trigger) {
        for (int request = 0; request < 1000; request++) {
            trigger.request();
        }
    }
}
