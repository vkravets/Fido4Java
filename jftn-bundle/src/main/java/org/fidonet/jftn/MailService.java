package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.PartialFileOutputStream;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.jftn.tosser.OutboundSpool;
import org.fidonet.jftn.tosser.Tosser;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owns tossing and transfer queues; database lifetime belongs to the database plugin. */
public final class MailService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);
    private static final Map<Path, MailService> ACTIVE = new ConcurrentHashMap<>();
    private final JFtnConfig config;
    private final Tosser tosser;
    private final OutboundSpool spool;
    private final Path stateDirectory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ScheduledExecutorService worker;
    private final Map<Path, SessionContext> claims = new ConcurrentHashMap<>();
    private final Map<FileData<InputStream>, Path> outgoing = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public MailService(JFtnConfig config, DatabaseManager base) throws IOException {
        this.config = config;
        stateDirectory = Path.of(config.getTmpDir()).toAbsolutePath().normalize();
        Files.createDirectories(stateDirectory);
        Files.createDirectories(Path.of(config.getInbound()));
        lockChannel = FileChannel.open(stateDirectory.resolve("mail-service.lock"),
                                       StandardOpenOption.CREATE,
                                       StandardOpenOption.WRITE);
        FileLock acquired = null;
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
        } catch (IOException | RuntimeException e) {
            if (acquired != null) {
                acquired.release();
            }
            lockChannel.close();
            throw e;
        }
        lock = acquired;
        worker = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task,
                                                                               "jftn-tosser"));
        ACTIVE.put(stateDirectory, this);
    }

    public Tosser getTosser() {
        return tosser;
    }

    private void configure(DatabaseManager base) {
        for (Link link : config.getConfiguredLinks()) {
            base.configureLink(link, config.getPacketPassword(link));
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
        processPending();
        long seconds = Long.parseLong(config.getValue("TossIntervalSeconds", "5"));
        if (seconds < 1) {
            throw new IllegalArgumentException("TossIntervalSeconds must be positive");
        }
        worker.scheduleWithFixedDelay(this::processSafely, seconds, seconds, TimeUnit.SECONDS);
    }

    private void processSafely() {
        try {
            processPending();
        } catch (Exception e) {
            logger.error("Mail processing failed; source files were retained", e);
        }
    }

    public synchronized void processPending() throws Exception {
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
        tosser.exportPendingNetmail();
        tosser.exportEchomail();
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
        partial.commitTo(destination);
        worker.execute(this::processSafely);
    }

    private synchronized void loadOutbound(SessionContext context) {
        if (closed) {
            return;
        }
        try {
            processPending();
            Link link = requireLink(context);
            for (Path path : spool.pending(link, context.getServerRole() == ServerRole.SERVER)) {
                if (claims.putIfAbsent(path, context) != null) {
                    continue;
                }
                try {
                    FileInfo info = new FileInfo(path.getFileName().toString(),
                                                 Files.size(path),
                                                 Files.getLastModifiedTime(path).toMillis() / 1000);
                    FileData<InputStream> file = new FileData<>(info, Files.newInputStream(path));
                    outgoing.put(file, path);
                    context.getSendFiles().add(file);
                } catch (IOException e) {
                    claims.remove(path, context);
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare outbound", e);
        }
    }

    private synchronized void sent(SessionContext context, FileData<InputStream> file)
            throws IOException {
        Path path = outgoing.get(file);
        if (path != null) {
            // Close the sender's stream before removing its packet on Windows.
            file.getStream().close();
            tosser.acknowledgeOutbound(spool.acknowledge(path));
            outgoing.remove(file);
            claims.remove(path, context);
        }
    }

    private synchronized void release(SessionContext context) {
        for (Map.Entry<FileData<InputStream>, Path> entry : outgoing.entrySet()) {
            if (claims.get(entry.getValue()) == context) {
                try {
                    entry.getKey().getStream().close();
                } catch (IOException e) {
                    logger.warn("Unable to close outgoing packet", e);
                }
                outgoing.remove(entry.getKey());
                claims.remove(entry.getValue(), context);
            }
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
            if (closed) {
                return;
            }
            closed = true;
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                throw new IOException("Tosser worker did not stop");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
            throw new IOException("Interrupted while stopping tosser", e);
        } finally {
            synchronized (this) {
                for (SessionContext context : claims.values()) {
                    release(context);
                }
                ACTIVE.remove(stateDirectory, this);
                lock.release();
                lockChannel.close();
            }
        }
    }
}
