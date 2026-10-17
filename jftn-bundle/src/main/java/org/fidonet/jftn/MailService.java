package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.PartialFileOutputStream;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.jftn.script.FileDeliveryQueue;
import org.fidonet.jftn.script.MailScripts;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.tosser.EchoRouter;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.misc.LazyFileInputStream;
import org.fidonet.misc.ReceivedFileArchive;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public final class MailService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);
    private static final Map<Path, MailService> ACTIVE = new ConcurrentHashMap<>();
    private final JFtnConfig config;
    private final Tosser tosser;
    private final OutboundSpool spool;
    private final FileDeliveryQueue fileQueue;
    private final List<MailProcessingExtension> extensions;
    private final Path stateDirectory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ScheduledExecutorService worker;
    private final Map<Path, SessionContext> claims = new ConcurrentHashMap<>();
    private final Map<FileData<InputStream>, Path> outgoing = new ConcurrentHashMap<>();
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

    public MailService(JFtnConfig config, DatabaseManager base) throws IOException {
        this(config, base, MailService::createWorker);
    }

    MailService(JFtnConfig config,
                DatabaseManager base,
                Supplier<ScheduledExecutorService> workerFactory)
            throws IOException {
        this(config, base, ServiceLoader.load(MailExtensionFactory.class), workerFactory);
    }

    public MailService(JFtnConfig config,
                       DatabaseManager base,
                       Iterable<MailExtensionFactory> factories)
            throws IOException {
        this(config, base, factories, MailService::createWorker);
    }

    private static ScheduledExecutorService createWorker() {
        return Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "jftn-tosser"));
    }

    MailService(JFtnConfig config,
                DatabaseManager base,
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
            configure(base);
            tosser = new Tosser(config, base);
            spool = new OutboundSpool(config);
            fileQueue = new FileDeliveryQueue(config);
            for (MailExtensionFactory factory : factories) {
                factory.create(config, base).ifPresent(created::add);
            }
            if (config.getValuesAsList("MailScript") != null
                    && !config.getValuesAsList("MailScript").isEmpty()) {
                created.add(new MailScripts(config, base, tosser));
            }
            extensions = created;
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

    private void configure(DatabaseManager base) {
        boolean managed = EchoRouter.databaseSubscriptions(config);
        for (Link link : config.getConfiguredLinks()) {
            base.configureLink(link, config.getPacketPassword(link));
        }
        if (managed) {
            return;
        }
        List<String> areas = config.getValuesAsList("EchoArea");
        if (areas == null) {
            return;
        }
        for (String value : areas) {
            String[] fields = value.split(",");
            if (fields.length < 2 || fields[0].trim().isEmpty()) {
                throw new IllegalArgumentException("Expected EchoArea area,address[=READ|WRITE|BOTH],...");
            }
            for (int i = 1; i < fields.length; i++) {
                String[] recipient = fields[i].trim().split("=", 2);
                Link link = config.getLink(new FTNAddr(recipient[0].trim()));
                if (link == null) {
                    throw new IllegalArgumentException("Unknown echo link " + recipient[0]);
                }
                AccessLevel access = recipient.length == 1
                        ? AccessLevel.BOTH
                        : AccessLevel.valueOf(recipient[1].trim().toUpperCase(Locale.ROOT));
                base.configureSubscription(link, fields[0].trim(), access);
            }
        }
    }

    public void attach(SessionContext context) {
        context.getStationConfig()
               .setPartialDirectory(Path.of(config.getInbound(), ".binkp-partials"));
        context.setReceivedFileHandler(this::received);
        context.setSentFileHandler(this::sent);
        context.setSessionClosedHandler(this::release);
        context.setOutboundLoader(this::loadOutbound);
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
            processMail();
        } finally {
            processing.unlock();
        }
    }

    private void processMail() throws Exception {
        if (closed) {
            return;
        }
        tosser.runFast(config.getInbound());
        for (Link link : config.getConfiguredLinks()) {
            Path directory = peerDirectory(link);
            if (Files.isDirectory(directory)) {
                tosser.runFast(directory.toString(), link.getAddr());
            }
        }
        runExtensions(true);
        tosser.exportPendingNetmail();
        tosser.exportEchomail();
        runExtensions(false);
    }

    private void runExtensions(boolean beforeExport) {
        for (MailProcessingExtension extension : extensions) {
            runExtension(extension, beforeExport);
        }
    }

    private void runExtension(MailProcessingExtension extension, boolean beforeExport) {
        try {
            if (beforeExport) {
                extension.beforeExport(tosser);
            } else {
                extension.afterExport();
            }
        } catch (IOException e) {
            logger.warn("Mail extension {} deferred during {} export",
                        extension.getClass().getName(),
                        beforeExport ? "before" : "after",
                        e);
        }
    }

    private synchronized void received(SessionContext context, FileData<OutputStream> file)
            throws IOException {
        requireOpen();
        Link link = requireLink(context);
        if (!(file.getStream() instanceof PartialFileOutputStream)) {
            throw new IOException("Durable receive storage is required");
        }
        PartialFileOutputStream partial = (PartialFileOutputStream) file.getStream();
        Path directory = peerDirectory(link);
        Files.createDirectories(directory);
        String original = file.getInfo().getName().replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.'))
                : ".file";
        if (!extension.matches("\\.[A-Za-z0-9]{1,8}")) {
            extension = ".file";
        }
        Path destination = directory.resolve(partial.getPath()
                                                    .getFileName()
                                                    .toString()
                                                    .replaceFirst("\\.part$", extension));
        if (!extension.equalsIgnoreCase(".pkt")
                && original.matches("[A-Za-z0-9_~-][A-Za-z0-9_.~-]{0,127}")) {
            ReceivedFile.record(destination, original);
        }
        partial.commitTo(destination);
        ReceivedFileArchive.preserve(config, destination, original);
        trigger.request();
    }

    private void loadOutbound(SessionContext context) {
        if (closed) {
            return;
        }
        try {
            processPending();
            enqueuePending(context);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare outbound", e);
        }
    }

    private synchronized void enqueuePending(SessionContext context) throws IOException {
        requireOpen();
        Link link = requireLink(context);
        for (Path path : spool.pending(link, context.getServerRole() == ServerRole.SERVER)) {
            enqueueOutboundFile(context, path);
        }
        for (Path path : fileQueue.pending(link)) {
            enqueueOutboundFile(context, path);
        }
    }

    private void enqueueOutboundFile(SessionContext context, Path path) throws IOException {
        if (claims.putIfAbsent(path, context) != null) {
            return;
        }
        try {
            FileInfo info = new FileInfo(path.getFileName().toString(),
                                         Files.size(path),
                                         Files.getLastModifiedTime(path).toMillis() / 1000);
            LazyFileInputStream stream = new LazyFileInputStream(path);
            FileData<InputStream> file = new FileData<>(info, stream);
            file.setSendResource(stream::release);
            outgoing.put(file, path);
            context.getSendFiles().add(file);
        } catch (IOException e) {
            claims.remove(path, context);
            throw e;
        }
    }

    private synchronized void sent(SessionContext context, FileData<InputStream> file)
            throws IOException {
        Path path = outgoing.get(file);
        if (path != null) {
            // Close the sender's stream before removing its packet on Windows.
            file.getStream().close();
            if (fileQueue.owns(path)) {
                fileQueue.acknowledge(path);
            } else {
                tosser.acknowledgeOutbound(spool.acknowledge(path));
            }
            outgoing.remove(file);
            claims.remove(path, context);
        }
    }

    private synchronized void release(SessionContext context) {
        for (Map.Entry<FileData<InputStream>, Path> entry : outgoing.entrySet()) {
            if (claims.get(entry.getValue()) != context) {
                continue;
            }
            closeOutgoingFile(entry.getKey());
            outgoing.remove(entry.getKey());
            claims.remove(entry.getValue(), context);
        }
    }

    private void closeOutgoingFile(FileData<InputStream> file) {
        try {
            file.getStream().close();
        } catch (IOException e) {
            logger.warn("Unable to close outgoing packet", e);
        }
    }

    private Link requireLink(SessionContext context) throws IOException {
        Link current = context.getLinksInfo().getCurLink();
        Link configured = current == null ? null : config.getLink(current.getAddr());
        if (configured == null) {
            throw new IOException("Session peer is not configured");
        }
        return configured;
    }

    private Path peerDirectory(Link link) {
        FTNAddr address = link.getAddr();
        return Path.of(config.getInbound(),
                       "peers",
                       address.getZone()
                       + "-"
                       + address.getNet()
                       + "-"
                       + address.getNode()
                       + "-"
                       + address.getPnt());
    }

    private void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("Mail service has stopped");
        }
    }

    public static void runOnce(JFtnConfig config, DatabaseManager base) throws Exception {
        MailService active = ACTIVE.get(Path.of(config.getTmpDir()).toAbsolutePath().normalize());
        if (active != null) {
            active.processPending();
        } else {
            try (MailService service = new MailService(config, base)) {
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
        for (SessionContext context : claims.values()) {
            release(context);
        }
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
