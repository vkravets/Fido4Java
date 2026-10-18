package org.fidonet.binkp.common.protocol;

import org.fidonet.binkp.common.ClientConnector;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.BinkpLog;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One external duplex process per poll. Its stderr is drained separately from protocol bytes. */
public final class ProcessConnector extends ClientConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessConnector.class);
    private final Link link;
    private final List<String> command;
    private final Duration timeout;
    private final String description;
    private volatile Process process;
    private volatile StreamSession session;
    private volatile SessionContext context;
    private volatile Thread errors;
    private volatile Thread exitObserver;
    private volatile boolean stopped;

    public ProcessConnector(Link link, List<String> command, Duration timeout) {
        this(link, command, timeout, "process peer=" + link.getAddr());
    }

    public ProcessConnector(Link link, List<String> command, Duration timeout, String description) {
        this.link = link;
        this.description = description;
        this.command = List.copyOf(command);
        if (command.isEmpty() || command.stream().anyMatch(value -> value.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("Invalid process command");
        }
        this.timeout = timeout;
        if (timeout.toMillis() < 1) {
            throw new IllegalArgumentException("Process timeout must be positive");
        }
    }

    @Override
    public String describe(Link link) {
        return description;
    }

    @Override
    public synchronized void run(SessionContext context) throws Exception {
        if (stopped || process != null) {
            throw new IOException("Process connector cannot be restarted");
        }
        this.context = context;
        context.setServerRole(ServerRole.CLIENT);
        if (!context.getLinksInfo().setCurLink(link.getAddr())) {
            throw new IOException("Remote link is not configured");
        }
        context.setReceiveEventBus(getEventBus());
        process = new ProcessBuilder(command).redirectErrorStream(false).start();
        session = new StreamSession(process.getInputStream(),
                                    process.getOutputStream(),
                                    context,
                                    description,
                                    timeout,
                                    this::abortProcess);
        errors = daemon(this::readErrors, "binkp-process-stderr");
        errors.start();
        try {
            session.start();
            exitObserver = daemon(this::observeExit, "binkp-process-exit");
            exitObserver.start();
        } catch (Exception failure) {
            stop();
            throw failure;
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private void readErrors() {
        try (InputStreamReader reader = new InputStreamReader(process.getErrorStream(),
                                                              StandardCharsets.UTF_8)) {
            char[] buffer = new char[256];
            StringBuilder line = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                for (int index = 0; index < count; index++) {
                    char value = buffer[index];
                    if (value == '\n') {
                        diagnostic(line.toString());
                        line.setLength(0);
                    } else if (line.length() < 2048) {
                        line.append(value);
                    }
                }
            }
            if (line.length() > 0) {
                diagnostic(line.toString());
            }
        } catch (IOException failure) {
            if (!stopped) {
                diagnostic("Unable to read process stderr: " + failure.getMessage());
            }
        }
    }

    private void diagnostic(String text) {
        String secret = link.getPass();
        String safe = secret == null || secret.isEmpty()
                ? text
                : text.replace(secret, "[redacted]");
        safe = BinkpLog.text(safe.replaceAll("(?i)CRAM-[A-Z0-9]+-[0-9a-f]+", "[redacted]"));
        String line = "binkp process [peer=" + link.getAddr() + "] " + safe;
        LOGGER.info(line);
        context.recordProtocolLog(line);
    }

    private void observeExit() {
        try {
            int code = process.waitFor();
            diagnostic("Process exited with code " + code);
            if (!stopped && !context.isTransferComplete()) {
                session.fail(new IOException("Binkp process exited before completion (code "
                                             + code
                                             + ")"));
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            session.fail(failure);
        }
    }

    @Override
    public void awaitCompletion() throws Exception {
        session.close(false);
        if (!session.awaitClosed(Duration.ofSeconds(5))) {
            throw new IOException("Timed out flushing the binkp process stream");
        }
        if (context.getState() == SessionState.STATE_ERR) {
            throw new IOException("Binkp process stream failed", context.getLastFailure());
        }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            throw new IOException("Binkp process did not exit after exchange completion");
        }
        errors.join(1000);
        joinObserver();
        if (process.exitValue() != 0) {
            throw new IOException("Binkp process failed with exit code " + process.exitValue());
        }
    }

    private void abortProcess() {
        Process running = process;
        if (running != null && running.isAlive()) {
            running.destroyForcibly();
        }
    }

    @Override
    public void stop() {
        StreamSession stream;
        synchronized (this) {
            stopped = true;
            stream = session;
        }
        if (stream != null) {
            stream.close(true);
        }
        abortProcess();
        Process running = process;
        if (running == null) {
            return;
        }
        try {
            running.waitFor(5, TimeUnit.SECONDS);
            if (errors != null) {
                errors.join(1000);
            }
            joinObserver();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    private void joinObserver() throws InterruptedException {
        Thread observer = exitObserver;
        if (observer != null && observer != Thread.currentThread()) {
            observer.join(1000);
        }
    }
}
