package org.fidonet.jftn;

import java.util.concurrent.Executor;

/** Coalesces requests into at most one waiting task, including requests during a run. */
final class ProcessingTrigger implements Runnable {
    private final Executor executor;
    private final Runnable action;
    private boolean queued;

    ProcessingTrigger(Executor executor, Runnable action) {
        this.executor = executor;
        this.action = action;
    }

    synchronized void request() {
        if (queued) {
            return;
        }
        queued = true;
        try {
            executor.execute(this);
        } catch (RuntimeException failure) {
            queued = false;
            throw failure;
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            queued = false;
        }
        action.run();
    }
}
