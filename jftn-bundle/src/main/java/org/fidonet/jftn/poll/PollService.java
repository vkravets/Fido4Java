package org.fidonet.jftn.poll;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/** One bounded outgoing queue for the scheduler and every administrative transport. */
public class PollService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollService.class);
    private final JFtnConfig config;
    private final SessionContext template;
    private final Function<Link, Client> clients;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final PollHistory history;
    private final int historyLimit;
    private final long timeoutSeconds;
    private final int logLimit;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,
                                                                     1,
                                                                     0,
                                                                     TimeUnit.SECONDS,
                                                                     new ArrayBlockingQueue<>(64),
                                                                     task -> new Thread(task,
                                                                                        "jftn-poll"));
    private boolean closed;

    private static final class Job {
        private final PollAttempt info;
        private volatile Client client;
        private volatile boolean cancelled;
        private boolean running;
        private Future<?> future;
        private final PollJournal journal;

        private Job(PollAttempt info, PollJournal journal) {
            this.info = info;
            this.journal = journal;
        }
    }

    public PollService(JFtnConfig config, SessionContext template) {
        this(config, template, Client::new);
    }

    public PollService(JFtnConfig config, SessionContext template, Function<Link, Client> clients) {
        this.config = config;
        this.template = template;
        this.clients = clients;
        historyLimit = setting(config, "PollHistoryLimit", 256, 1, 10000);
        timeoutSeconds = setting(config, "PollTimeoutSeconds", 120, 1, 86400);
        logLimit = setting(config, "PollLogLimitBytes", 262144, 4096, 16777216);
        try {
            history = new PollHistory(Path.of(config.getTmpDir(), "polls"));
            restore();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static int setting(JFtnConfig config, String key, int fallback, int min, int max) {
        int value = Integer.parseInt(config.getValue(key, Integer.toString(fallback)));
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " is outside its allowed range");
        }
        return value;
    }

    private void restore() throws IOException {
        try {
            for (PollAttempt attempt : history.load()) {
                if (attempt.isActive()) {
                    attempt.setState("INTERRUPTED")
                           .setDetail("Node stopped before this attempt finished")
                           .finished(System.currentTimeMillis());
                    history.save(attempt);
                }
                jobs.put(attempt.getId(), job(attempt));
            }
            prune();
        } catch (IOException | RuntimeException failure) {
            history.close();
            throw failure;
        }
    }

    public PollAttempt poll(String address) {
        return enqueue(address, "MANUAL");
    }

    private synchronized PollAttempt enqueue(String address, String source) {
        if (closed || worker.isShutdown()) {
            throw new IllegalArgumentException("Poll service is stopped");
        }
        Link link = config.getLink(new FTNAddr(address));
        if (link == null || config.getLocalAddresses().contains(link.getAddr())) {
            throw new IllegalArgumentException("Unknown remote link");
        }
        for (Job job : jobs.values()) {
            if (job.info.getAddress().equals(link.getAddr().toString()) && occupied(job)) {
                return job.info.copy();
            }
        }
        if (worker.getQueue().remainingCapacity() == 0) {
            throw new IllegalArgumentException("Poll queue is full");
        }
        PollAttempt attempt = new PollAttempt(UUID.randomUUID().toString(),
                                              link.getAddr().toString(),
                                              "QUEUED",
                                              System.currentTimeMillis(),
                                              "").source(source);
        history.save(attempt);
        Job job = job(attempt);
        job.journal.record("QUEUED", source + " " + attempt.getAddress(), 0);
        job.journal.flush();
        jobs.put(attempt.getId(), job);
        SessionContext context = new SessionContext(template);
        job.future = worker.submit(() -> run(job, link, context));
        prune();
        return attempt.copy();
    }

    public void pollAll() {
        config.getConfiguredLinks().forEach(this::enqueueScheduled);
    }

    private Job job(PollAttempt info) {
        return new Job(info,
                       new PollJournal(Path.of(config.getTmpDir(), "polls", info.getId() + ".log"),
                                       logLimit));
    }

    private void enqueueScheduled(Link link) {
        try {
            enqueue(link.getAddr().toString(), "SCHEDULED");
        } catch (RuntimeException failure) {
            LOGGER.warn("Scheduled poll could not be queued for {}: {}",
                        link.getAddr(),
                        new PollDiagnostics(config, template).summary(failure));
        }
    }

    private boolean occupied(Job job) {
        return job.running || job.info.isActive();
    }

    private synchronized void state(Job job, String state, String detail) {
        if (job.cancelled || job.info.getState().equals(state)) {
            return;
        }
        job.info.setState(state).setDetail(detail);
        if (!job.info.isActive()) {
            job.info.finished(System.currentTimeMillis());
        }
        history.save(job.info);
        job.journal.record("RESULT", state + " " + detail, 0);
        job.journal.flush();
    }

    private void run(Job job, Link link, SessionContext context) {
        PollDiagnostics diagnostics = new PollDiagnostics(config, context);
        context.setProtocolLogListener(line -> job.journal.record("INFO",
                                                                  diagnostics.clean(line),
                                                                  0));
        context.setActivityListener((kind, name, bytes) -> job.journal.record(kind.name(),
                                                                              name,
                                                                              bytes));
        synchronized (this) {
            if (job.cancelled) {
                return;
            }
            job.running = true;
            job.info.started(System.currentTimeMillis());
        }
        try {
            state(job, "CONNECTING", "");
            job.journal.record("CONNECT",
                               new PollDiagnostics(config, context).clean(link.getHostAddress()
                                       + ":" + (link.getPort() == 0 ? 24554 : link.getPort())),
                               0);
            job.client = clients.apply(link);
            if (job.cancelled) {
                return;
            }
            job.client.run(context);
            awaitSession(job, context);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            state(job, "INTERRUPTED", "Poll worker interrupted");
        } catch (Exception failure) {
            failed(job, context, failure);
        } finally {
            cleanup(job, context);
        }
    }

    private void failed(Job job, SessionContext context, Throwable failure) {
        if (job.cancelled) {
            return;
        }
        PollDiagnostics diagnostics = new PollDiagnostics(config, context);
        job.journal.flush();
        for (String line : diagnostics.trace(failure)) {
            job.journal.record("ERROR", line, 0);
            LOGGER.error("Poll {} ({}) {}", job.info.getId(), job.info.getAddress(), line);
        }
        state(job, "FAILED", diagnostics.summary(failure));
    }

    private String remoteReason(SessionContext context, String fallback) {
        String reason = context.getLastErrorMessage();
        return reason == null || reason.isBlank()
                ? fallback
                : new PollDiagnostics(config, context).clean(reason);
    }

    private void awaitSession(Job job, SessionContext context) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (!job.cancelled) {
            if (finished(job, context)) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                state(job, "TIMED_OUT", "Session deadline exceeded");
                return;
            }
            SessionState current = context.getState();
            boolean transferring = current == SessionState.STATE_TRANSFER
                    || current == SessionState.STATE_WAITGET;
            state(job, transferring ? "TRANSFERRING" : "AUTHENTICATING", "");
            job.journal.flush();
            Thread.sleep(100);
        }
    }

    private boolean finished(Job job, SessionContext context) {
        SessionState current = context.getState();
        if (current == SessionState.STATE_BSY) {
            state(job, "BUSY", remoteReason(context, "Remote node is busy"));
        } else if (current == SessionState.STATE_ERR) {
            if (context.getLastFailure() != null) {
                failed(job, context, context.getLastFailure());
            } else {
                state(job, "FAILED", remoteReason(context, "Authentication or protocol error"));
            }
        } else if (context.isTransferComplete()) {
            state(job, "SUCCEEDED", "Exchange completed");
        } else if (current == SessionState.STATE_END) {
            state(job, "FAILED", "Connection closed before the exchange completed");
        } else {
            return false;
        }
        return true;
    }

    private void cleanup(Job job, SessionContext context) {
        try {
            if (job.client != null) {
                job.client.stop();
            }
        } finally {
            finishDiagnostics(job, context);
        }
    }

    private void finishDiagnostics(Job job, SessionContext context) {
        try {
            context.setActivityListener(null);
            context.closeReceiveStreams();
            context.setProtocolLogListener(null);
            job.journal.flush();
        } finally {
            synchronized (this) {
                job.client = null;
                job.running = false;
                prune();
            }
        }
    }

    private void prune() {
        List<Job> terminal = new ArrayList<>();
        jobs.values().stream().filter(job -> !occupied(job)).forEach(terminal::add);
        for (int index = 0; index < terminal.size() - historyLimit; index++) {
            Job job = terminal.get(index);
            history.delete(job.info.getId());
            jobs.remove(job.info.getId());
        }
    }

    public synchronized List<PollAttempt> list() {
        List<PollAttempt> result = new ArrayList<>();
        jobs.values().forEach(job -> result.add(job.info.copy()));
        return result;
    }

    public synchronized PollAttempt get(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Unknown poll");
        }
        return job.info.copy();
    }

    public static String cursor(PollAttempt attempt) {
        return attempt.getCreated() + ":" + attempt.getId();
    }

    public synchronized List<PollAttempt> page(String before, int limit) {
        if (limit < 1 || limit > 201 || before == null || before.length() > 100) {
            throw new IllegalArgumentException("Invalid history cursor or page size");
        }
        long timestamp = Long.MAX_VALUE;
        String id = "~";
        if (!before.isEmpty()) {
            String[] parts = before.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid history cursor");
            }
            timestamp = Long.parseLong(parts[0]);
            id = UUID.fromString(parts[1]).toString();
            if (timestamp < 0 || !id.equals(parts[1])) {
                throw new IllegalArgumentException("Invalid history cursor");
            }
        }
        long boundary = timestamp;
        String boundaryId = id;
        return jobs.values()
                   .stream()
                   .map(job -> job.info)
                   .filter(info -> info.getCreated() < boundary || info.getCreated() == boundary
                           && info.getId().compareTo(boundaryId) < 0)
                   .sorted(Comparator.comparingLong(PollAttempt::getCreated)
                                     .thenComparing(PollAttempt::getId)
                                     .reversed())
                   .limit(limit)
                   .map(PollAttempt::copy)
                   .collect(Collectors.toList());
    }

    public synchronized PollLogSlice logs(String id, long offset, int limit) throws IOException {
        get(id);
        return jobs.get(id).journal.read(offset, limit);
    }

    public synchronized void cancel(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Unknown poll");
        }
        stop(job, "CANCELLED");
    }

    private void stop(Job job, String state) {
        if (!job.info.isActive()) {
            return;
        }
        job.cancelled = true;
        job.info.setState(state)
                .setDetail(state.equals("CANCELLED")
                        ? "Cancelled by administrator"
                        : "Node stopped")
                .finished(System.currentTimeMillis());
        if (job.future != null) {
            job.future.cancel(true);
        }
        if (job.client != null) {
            job.client.stop();
        }
        worker.purge();
        history.save(job.info);
        job.journal.record("RESULT", state, 0);
        job.journal.flush();
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            jobs.values().forEach(job -> stop(job, "INTERRUPTED"));
            worker.shutdownNow();
        }
        try {
            awaitStopped();
            history.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void awaitStopped() {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        try {
            while (!worker.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("Poll worker did not stop");
                }
                try {
                    worker.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
