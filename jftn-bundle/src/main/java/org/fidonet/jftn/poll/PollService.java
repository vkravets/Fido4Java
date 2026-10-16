package org.fidonet.jftn.poll;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** A bounded, cancellable queue for manual and scheduled polls; each owns a session context. */
public class PollService implements AutoCloseable {
    private final JFtnConfig config;
    private final SessionContext template;
    private final Function<Link, Client> clients;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,
                                                                     1,
                                                                     0,
                                                                     TimeUnit.SECONDS,
                                                                     new ArrayBlockingQueue<>(64),
                                                                     task -> new Thread(task,
                                                                                        "jftn-poll"));

    private static final class Job {
        private final PollAttempt info;
        private volatile Client client;
        private volatile boolean cancelled;
        private Future<?> future;

        private Job(String address) {
            info = new PollAttempt(UUID.randomUUID().toString(),
                                   address,
                                   "QUEUED",
                                   System.currentTimeMillis(),
                                   "");
        }
    }

    public PollService(JFtnConfig config, SessionContext template) {
        this(config, template, Client::new);
    }

    public PollService(JFtnConfig config, SessionContext template, Function<Link, Client> clients) {
        this.config = config;
        this.template = template;
        this.clients = clients;
    }

    public synchronized PollAttempt poll(String address) {
        Link link = config.getLink(new FTNAddr(address));
        if (link == null || config.getLocalAddresses().contains(link.getAddr())) {
            throw new IllegalArgumentException("Unknown remote link");
        }
        for (Job job : jobs.values()) {
            if (job.info.getAddress().equals(link.getAddr().toString()) && active(job)) {
                return job.info.copy();
            }
        }
        if (jobs.size() >= 128) {
            jobs.values().removeIf(job -> !active(job));
        }
        if (jobs.size() >= 128 || worker.getQueue().remainingCapacity() == 0
                || worker.isShutdown()) {
            throw new IllegalArgumentException("Poll queue is full or stopped");
        }
        Job job = new Job(link.getAddr().toString());
        jobs.put(job.info.getId(), job);
        job.future = worker.submit(() -> run(job, link));
        return job.info.copy();
    }

    public void pollAll() {
        for (Link link : config.getConfiguredLinks()) {
            enqueueScheduled(link);
        }
    }

    private void enqueueScheduled(Link link) {
        try {
            poll(link.getAddr().toString());
        } catch (IllegalArgumentException failure) {
            LoggerFactory.getLogger(PollService.class)
                         .warn("Scheduled poll could not be queued for {}", link.getAddr());
        }
    }

    private boolean active(Job job) {
        return List.of("QUEUED", "CONNECTING", "TRANSFERRING").contains(job.info.getState());
    }

    private synchronized void state(Job job, String state, String detail) {
        if (!job.cancelled) {
            job.info.setState(state).setDetail(detail);
        }
    }

    private void run(Job job, Link link) {
        SessionContext context = new SessionContext(template);
        Client client = clients.apply(link);
        job.client = client;
        try {
            if (job.cancelled) {
                return;
            }
            state(job, "CONNECTING", "");
            client.run(context);
            state(job, "TRANSFERRING", "");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (!context.isTransferComplete() && context.getState() != SessionState.STATE_END) {
                if (job.cancelled || context.getState() == SessionState.STATE_ERR
                        || context.getState() == SessionState.STATE_BSY
                        || System.nanoTime() >= deadline) {
                    throw new IOException("Session failed or timed out");
                }
                Thread.sleep(100);
            }
            state(job, "SUCCEEDED", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state(job, "FAILED", "Interrupted");
        } catch (Exception e) {
            state(job, "FAILED", "Connection or session failed");
        } finally {
            client.stop();
            context.closeReceiveStreams();
            job.client = null;
        }
    }

    public synchronized List<PollAttempt> list() {
        List<PollAttempt> result = new ArrayList<>();
        jobs.values().forEach(job -> result.add(job.info.copy()));
        return result;
    }

    public synchronized void cancel(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Unknown poll");
        }
        if (!active(job)) {
            return;
        }
        job.cancelled = true;
        job.info.setState("CANCELLED");
        job.future.cancel(true);
        if (job.client != null) {
            job.client.stop();
        }
        worker.purge();
    }

    @Override
    public void close() {
        synchronized (this) {
            for (String id : jobs.keySet()) {
                cancel(id);
            }
            worker.shutdownNow();
        }
        try {
            if (!worker.awaitTermination(35, TimeUnit.SECONDS)) {
                throw new IllegalStateException("UI poll worker did not stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("UI poll shutdown interrupted", e);
        }
    }
}
