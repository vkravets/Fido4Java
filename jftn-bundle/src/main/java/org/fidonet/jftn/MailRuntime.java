package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.script.DirectoryImportService;
import org.fidonet.jftn.script.MailAutomationRuntime;
import org.fidonet.jftn.script.MailScriptExtension;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Owns tossing and transfer queues; database lifetime belongs to the database plugin. */
public final class MailRuntime implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MailRuntime.class);
    private static final Map<Path, MailRuntime> ACTIVE = new ConcurrentHashMap<>();
    private final JFtnConfig config;
    private final Tosser tosser;
    private final BinkpMailTransferAdapter transfers;
    private final MailProcessingCoordinator coordinator;
    private final List<MailProcessingExtension> extensions;
    private final Path stateDirectory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ScheduledExecutorService worker;
    private volatile boolean closed;
    private boolean resourcesClosed;
    private final ReentrantLock processing = new ReentrantLock();
    private final ProcessingTrigger trigger;
    private ScheduledFuture<?> periodic;
    private volatile long scheduleGeneration;

    /** Stages a timer before publication. Inactive generations cannot process mail. */
    public Runnable prepareInterval(long seconds, List<Runnable> abort) {
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException("TossIntervalSeconds must be in 1..86400");
        }
        if (periodic == null)
            return () -> {
            };
        long generation = scheduleGeneration + 1;
        ScheduledFuture<?> next = worker.scheduleWithFixedDelay(() -> {
            if (scheduleGeneration == generation)
                processSafely();
        }, seconds, seconds, TimeUnit.SECONDS);
        abort.add(() -> next.cancel(false));
        return () -> {
            scheduleGeneration = generation;
            periodic.cancel(false);
            periodic = next;
        };
    }

    public MailRuntime(JFtnConfig config, MailStorage base) throws IOException {
        this(config, base, MailRuntime::createWorker);
    }

    MailRuntime(JFtnConfig config,
                MailStorage base,
                Supplier<ScheduledExecutorService> workerFactory)
            throws IOException {
        this(config, base, ServiceLoader.load(MailExtensionFactory.class), workerFactory);
    }

    public MailRuntime(JFtnConfig config,
                       MailStorage base,
                       Iterable<MailExtensionFactory> factories)
            throws IOException {
        this(config, base, factories, MailRuntime::createWorker);
    }

    private static ScheduledExecutorService createWorker() {
        return Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "jftn-tosser"));
    }

    MailRuntime(JFtnConfig config,
                MailStorage base,
                Iterable<MailExtensionFactory> factories,
                Supplier<ScheduledExecutorService> workerFactory)
            throws IOException {
        this.config = config;
        stateDirectory = Path.of(config.getTmpDir()).toAbsolutePath().normalize();
        Files.createDirectories(stateDirectory);
        Files.createDirectories(Path.of(config.getInbound()));
        lockChannel = FileChannel.open(stateDirectory.resolve("mail-service.lock"),
                                       StandardOpenOption.CREATE,
                                       StandardOpenOption.WRITE);
        FileLock acquired = null;
        List<MailProcessingExtension> created = new ArrayList<>();
        try {
            try {
                acquired = lockChannel.tryLock();
            } catch (OverlappingFileLockException e) {
                throw new IOException("A mail service is already using " + stateDirectory, e);
            }
            if (acquired == null) {
                throw new IOException("A mail service is already using " + stateDirectory);
            }
            MailDatabaseConfiguration.apply(config, base);
            tosser = new Tosser(config, base);
            for (MailExtensionFactory factory : factories) {
                factory.create(config, base).ifPresent(created::add);
            }
            List<DirectoryImportService> directories = new ArrayList<>();
            for (MailProcessingExtension extension : created) {
                if (extension instanceof DirectoryImportService) {
                    directories.add((DirectoryImportService) extension);
                }
            }
            if (directories.size() > 1) {
                throw new IOException("Multiple directory catalog owners");
            }
            created.add(new MailAutomationRuntime(config,
                                                  base,
                                                  tosser,
                                                  ServiceLoader.load(MailScriptExtension.class),
                                                  directories.isEmpty()
                                                          ? null
                                                          : directories.get(0)));
            extensions = created;
            coordinator = new MailProcessingCoordinator(config, tosser, extensions);
            transfers = new BinkpMailTransferAdapter(config,
                                                     tosser,
                                                     this::requestProcessing,
                                                     this::processPending,
                                                     () -> closed,
                                                     () -> extensions.stream()
                                                                     .filter(MailAutomationRuntime.class::isInstance)
                                                                     .map(MailAutomationRuntime.class::cast)
                                                                     .anyMatch(api -> api.getFileEcho() != null));
            worker = workerFactory.get();
        } catch (IOException | RuntimeException e) {
            IOException extensionFailure = closeExtensions(created);
            if (extensionFailure != null) {
                e.addSuppressed(extensionFailure);
            }
            if (acquired != null) {
                acquired.release();
            }
            lockChannel.close();
            throw e;
        }
        lock = acquired;
        trigger = new ProcessingTrigger(worker, this::processSafely);
        ACTIVE.put(stateDirectory, this);
    }

    public Tosser getTosser() {
        return tosser;
    }

    /** Call under operate(), so settings changes and mail scans share one lifecycle lock. */
    public List<SettingsExtension> getSettingsExtensions() {
        List<SettingsExtension> result = new ArrayList<>();
        for (MailProcessingExtension extension : extensions) {
            if (extension instanceof MailAutomationRuntime) {
                result.addAll(((MailAutomationRuntime) extension).getSettingsExtensions());
            }
        }
        return List.copyOf(result);
    }

    @FunctionalInterface
    public interface MailOperation<T> {
        T run(Tosser tosser) throws Exception;
    }

    /** UI mutations share the same lock as scans, scripts and outbound preparation. */
    public <T> T operate(MailOperation<T> operation) throws Exception {
        processing.lockInterruptibly();
        try {
            requireOpen();
            return operation.run(tosser);
        } finally {
            processing.unlock();
        }
    }

    public void attach(SessionContext context) {
        transfers.attach(context);
    }

    private void requestProcessing() {
        trigger.request();
    }

    public void start() throws Exception {
        logger.info("Starting mail processing: inbound={}, outbound={}, preserveReceivedFiles={}",
                    config.getInbound(),
                    config.getOutbound(),
                    config.isPreserveReceivedFiles());
        processPending();
        long seconds = Long.parseLong(config.getValue("TossIntervalSeconds", "5"));
        if (seconds < 1) {
            throw new IllegalArgumentException("TossIntervalSeconds must be positive");
        }
        periodic = worker.scheduleWithFixedDelay(this::processSafely,
                                                 seconds,
                                                 seconds,
                                                 TimeUnit.SECONDS);
        logger.info("Mail processing started: interval={}s", seconds);
    }

    private void processSafely() {
        try {
            processPending();
        } catch (Exception e) {
            logger.error("Mail processing failed; source files were retained", e);
        }
    }

    public void processPending() throws Exception {
        processing.lockInterruptibly();
        try {
            if (!closed) {
                coordinator.process();
            }
        } finally {
            processing.unlock();
        }
    }

    private void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("Mail service has stopped");
        }
    }

    public static void runOnce(JFtnConfig config, MailStorage base) throws Exception {
        MailRuntime active = ACTIVE.get(Path.of(config.getTmpDir()).toAbsolutePath().normalize());
        if (active != null) {
            active.processPending();
        } else {
            try (MailRuntime service = new MailRuntime(config, base)) {
                service.processPending();
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (resourcesClosed) {
                return;
            }
            closed = true;
        }
        worker.shutdown();
        awaitWorker();
        acquireShutdownLock();
        try {
            closeResources();
        } finally {
            processing.unlock();
        }
    }

    private void awaitWorker() throws IOException {
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                throw new IOException("Tosser worker did not stop; service lock was retained");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
            throw new IOException("Interrupted while stopping tosser", e);
        }
    }

    private void acquireShutdownLock() throws IOException {
        try {
            if (!processing.tryLock(10, TimeUnit.SECONDS)) {
                throw new IOException("Mail processing did not stop; service lock was retained");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting mail processing", e);
        }
    }

    private synchronized void closeResources() throws IOException {
        if (resourcesClosed) {
            return;
        }
        transfers.close();
        IOException extensionFailure = closeExtensions(extensions);
        lock.release();
        lockChannel.close();
        ACTIVE.remove(stateDirectory, this);
        resourcesClosed = true;
        if (extensionFailure != null) {
            throw extensionFailure;
        }
    }

    private static IOException closeExtensions(List<MailProcessingExtension> extensions) {
        IOException failure = null;
        for (int index = extensions.size() - 1; index >= 0; index--) {
            try {
                extensions.get(index).close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }
}
