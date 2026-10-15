package org.fidonet.binkp.interop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.events.FileReceivedEventHandler;
import org.fidonet.events.EventBus;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Shared session configuration, receive checks and protocol assertions. */
public abstract class AbstractJftnPeer implements JftnPeer {
    protected final ExchangeFiles files;
    protected final SessionContext template;
    protected final EventBus bus = new EventBus();
    protected final ProtocolTrace trace = new ProtocolTrace();
    protected final CountDownLatch accepted = new CountDownLatch(1);
    protected volatile SessionContext session;
    private final boolean requestNr;
    private final ReceiveProbe receiver = new ReceiveProbe();

    protected AbstractJftnPeer(ServerRole role, boolean requestNr, ExchangeFiles files) {
        this.files = files;
        this.requestNr = requestNr;
        template = createContext(role, requestNr);
        template.setOutboundLoader(context -> {
            if (context.getBatchNumber() == 1) {
                context.getSendFiles().add(files.outgoingInNextBatch());
            }
        });
        bus.subscribe(receiver);
    }

    protected abstract boolean awaitClosed() throws Exception;

    protected abstract void closeTransport() throws Exception;

    public void awaitConnection() throws InterruptedException {
        assertTrue(accepted.await(30, TimeUnit.SECONDS), "binkd must connect to JFtn");
    }

    public void assertReceivedFile() throws InterruptedException {
        assertTrue(receiver.received.await(30, TimeUnit.SECONDS),
                   "binkd must send its file within 30 seconds");
        assertNull(receiver.failure.get(), "Receive event failed");
        assertArrayEquals(files.toJftn, receiver.bytes.get());
    }

    public void assertSessionFinished() throws Exception {
        assertTrue(awaitClosed(), "TCP session must close normally");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!session.isTransferComplete() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(session.isTransferComplete(), "EOB and all file acknowledgements are required");
        assertTrue(session.isBinkp11(), "binkp 1.1 must be negotiated");
        assertTrue(session.getBatchNumber() >= 2,
                   "Both file batches must complete before the empty batch");
    }

    public void assertProtocol() throws Exception {
        trace.assertExchange(requestNr);
    }

    public void saveDiagnostics(Path artifacts) {
        try {
            Files.writeString(artifacts.resolve("protocol.log"), trace.log());
        } catch (Exception failure) {
            System.err.println("Could not save JFtn diagnostics: " + failure);
        }
    }

    private static SessionContext createContext(ServerRole role, boolean requestNr) {
        StationConfig config = new StationConfig("JFtn integration test",
                                                 "Test",
                                                 "localhost",
                                                 "BINKP",
                                                 "1:500/101");
        config.setPartialDirectory(null);
        config.setNREnabled(true);
        config.setNRRequested(requestNr);
        Link link = new Link(new FTNAddr("1:500/102"),
                             Collections.singletonList(new FTNAddr("1:500/101")),
                             "docker-secret",
                             "127.0.0.1",
                             0);
        LinksInfo links = new LinksInfo(new ArrayList<>(Collections.singletonList(link)));
        links.setCurLink(link.getAddr());
        SessionContext context = new SessionContext(config, links);
        context.setServerRole(role);
        return context;
    }

    @Override
    public final void close() throws Exception {
        try {
            closeTransport();
        } finally {
            bus.unsubscribe(receiver);
            bus.shutdown();
        }
    }

    private static final class ReceiveProbe extends FileReceivedEventHandler {
        final CountDownLatch received = new CountDownLatch(1);
        final AtomicReference<byte[]> bytes = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void onEventHandle(FileReceivedEvent event) {
            try {
                assertEquals(ExchangeFiles.FROM_BINKD, event.getFile().getInfo().getName());
                bytes.set(((ByteArrayOutputStream) event.getFile().getStream()).toByteArray());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                received.countDown();
            }
        }
    }
}
