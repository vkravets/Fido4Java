package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileTransferInfo;
import org.fidonet.binkp.common.io.PartialFileOutputStream;
import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.script.FileDeliveryQueue;
import org.fidonet.jftn.script.ReceivedFile;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Adapts durable mail queues to binkp session callbacks and acknowledgements. */
final class BinkpMailTransferAdapter {
    @FunctionalInterface
    interface Preparation {
        void run() throws Exception;
    }

    private static final Logger logger = LoggerFactory.getLogger(BinkpMailTransferAdapter.class);
    private final JFtnConfig config;
    private final Tosser tosser;
    private final OutboundSpool spool;
    private final FileDeliveryQueue fileQueue;
    private final Map<Path, SessionContext> claims = new ConcurrentHashMap<>();
    private final Map<FileData<InputStream>, Path> outgoing = new ConcurrentHashMap<>();
    private final Runnable requestProcessing;
    private final Preparation prepareOutbound;
    private final BooleanSupplier closed;
    private final BooleanSupplier fileEchoEnabled;

    BinkpMailTransferAdapter(JFtnConfig config,
                             Tosser tosser,
                             Runnable requestProcessing,
                             Preparation prepareOutbound,
                             BooleanSupplier closed,
                             BooleanSupplier fileEchoEnabled) {
        this.config = config;
        this.tosser = tosser;
        this.spool = new OutboundSpool(config);
        this.fileQueue = new FileDeliveryQueue(config);
        this.requestProcessing = requestProcessing;
        this.prepareOutbound = prepareOutbound;
        this.closed = closed;
        this.fileEchoEnabled = fileEchoEnabled;
    }

    void attach(SessionContext context) {
        context.getStationConfig()
               .setPartialDirectory(Path.of(config.getInbound(), ".binkp-partials"));
        context.setReceivedFileHandler(this::received);
        context.setSentFileHandler(this::sent);
        context.setSessionClosedHandler(this::release);
        context.setOutboundLoader(this::loadOutbound);
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
        if (!extension.equalsIgnoreCase(".pkt") && ReceivedFile.isSafeName(original)) {
            ReceivedFile.record(destination, original);
        }
        partial.commitTo(destination);
        ReceivedFileArchive.preserve(config, destination, original);
        requestProcessing.run();
    }

    private void loadOutbound(SessionContext context) {
        if (closed.getAsBoolean()) {
            return;
        }
        try {
            prepareOutbound.run();
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
        if (fileEchoEnabled.getAsBoolean()) {
            for (Path path : fileQueue.pending(link)) {
                enqueueOutboundFile(context, path);
            }
        }
    }

    private void enqueueOutboundFile(SessionContext context, Path path) throws IOException {
        if (claims.putIfAbsent(path, context) != null) {
            return;
        }
        try {
            FileTransferInfo info = new FileTransferInfo(path.getFileName().toString(),
                                                         Files.size(path),
                                                         Files.getLastModifiedTime(path).toMillis()
                                                                 / 1000);
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
        if (closed.getAsBoolean()) {
            throw new IOException("Mail runtime has stopped");
        }
    }

    synchronized void close() {
        for (SessionContext context : claims.values()) {
            release(context);
        }
    }
}
