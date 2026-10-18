package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.GETCommand;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.protocol.Session;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FileTransferRegressionTest {
    private SessionContext context() {
        StationConfig config = new StationConfig("station", "sysop", "city", "BINKP", "2:467/1");
        config.setPartialDirectory(null);
        SessionContext context = new SessionContext(config, new LinksInfo(new ArrayList<>()));
        context.beginTransfer();
        return context;
    }

    private FileInfo info(long offset) {
        FileInfo info = new FileInfo("file.pkt", 6, 123);
        info.setOffset(offset);
        return info;
    }

    @Test
    public void advertisesMinusOneInNrMode() {
        SessionContext context = context();
        context.setNRMode(true);
        FileInfo info = info(0);
        context.getSendFiles().add(new FileData<>(info, new ByteArrayInputStream(new byte[6])));
        assertEquals("file.pkt 6 123 -1", new FILECommand().getCommandArguments(context));
        assertEquals(-1, info.getOffset());
        assertEquals(SessionState.STATE_WAITGET, context.getState());
    }

    @Test
    public void advertisesEmptyFileBeforeTheNextQueuedFile() {
        SessionContext context = context();
        context.getSendFiles()
               .add(new FileData<>(new FileInfo("empty.pkt", 0, 123),
                                   new ByteArrayInputStream(new byte[0])));
        context.getSendFiles().add(new FileData<>(info(0), new ByteArrayInputStream(new byte[6])));
        assertEquals("empty.pkt 0 123 0", new FILECommand().getCommandArguments(context));
    }

    @Test
    public void resumesNrTransferAtRequestedOffsetAndReturnsToTransferState() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FilesSender<Session> sender = new FilesSender<>(session, context.getSendFiles(), context);
        session.sender = sender;
        FileData<InputStream> file = new FileData<>(info(-1),
                                                    new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.US_ASCII)));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> sent = executor.submit(() -> sender.send(file));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (context.getState() != SessionState.STATE_WAITGET
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertEquals(SessionState.STATE_WAITGET, context.getState());
            new GETCommand().handle(session, context, "file.pkt 6 123 3");
            assertTrue(sent.get(2, TimeUnit.SECONDS));
            assertArrayEquals("def".getBytes(StandardCharsets.US_ASCII), session.bytes());
            assertEquals(6, file.getInfo().getCurSize());
            assertEquals(SessionState.STATE_TRANSFER, context.getState());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void handlesPartialSkipAndZeroSkip() throws Exception {
        for (boolean zero : new boolean[]{false, true}) {
            RecordingSession session = new RecordingSession();
            InputStream stream = new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.US_ASCII)) {
                @Override
                public long skip(long count) {
                    return zero ? 0 : super.skip(Math.min(count, 1));
                }
            };
            FileData<InputStream> file = new FileData<>(info(3), stream);
            assertTrue(new FilesSender<>(session, new ArrayDeque<>(), context()).send(file));
            assertArrayEquals("def".getBytes(StandardCharsets.US_ASCII), session.bytes());
            assertEquals(6, file.getInfo().getCurSize());
        }
    }

    @Test
    public void rejectsOffsetsOutsideFileAndPrematureEnd() throws Exception {
        for (long offset : new long[]{7, 3}) {
            RecordingSession session = new RecordingSession();
            try {
                new FilesSender<>(session,
                                  new ArrayDeque<>(),
                                  context()).send(new FileData<>(info(offset), new ByteArrayInputStream(new byte[2])));
                fail("Expected invalid or unreachable offset");
            } catch (IOException expected) {
                assertTrue(session.frames.isEmpty());
            }
        }
    }

    @Test
    public void abortsFailedTransferWithoutReportingSuccessfulFinish() {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        context.getSendFiles().add(new FileData<>(info(7), new ByteArrayInputStream(new byte[6])));
        new FilesSender<>(session, context.getSendFiles(), context).run();
        assertTrue(session.closed);
        assertFalse(context.isSendingIsFinish());
        assertNotNull(context.getLastErrorMessage());
        assertEquals(2, session.frames.size());
    }

    @Test
    public void findsPartialFileByMetadataAndRequestsExistingLength() throws Exception {
        SessionContext context = context();
        FileData<OutputStream> partial = new FileData<>(info(0), new ByteArrayOutputStream());
        partial.getInfo().setCurSize(3);
        context.setNotFinishedFiles(Collections.singletonList(partial));
        context.getRecvFiles().add(partial);
        assertSame(partial, context.getNotFinishedFileData(info(0)));
        assertNull(context.getNotFinishedFileData(new FileInfo("other.pkt", 6, 123)));
        RecordingSession session = new RecordingSession();
        new FILECommand().handle(session, context, "file.pkt 6 123 -1");
        assertSame(partial, context.getRecvFiles().peek());
        assertEquals(1, context.getRecvFiles().size());
        assertEquals(1, session.frames.size());
        String arguments = new String(session.frames.get(0).getData(),
                                      StandardCharsets.US_ASCII).substring(1);
        assertEquals("file.pkt 6 123 3", arguments);
    }

    @Test
    public void reusesCurrentPartialFileWithoutRestartingItsOutput() throws Exception {
        SessionContext context = context();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[]{1, 2, 3});
        FileData<OutputStream> partial = new FileData<>(info(0), output);
        partial.getInfo().setCurSize(3);
        context.getRecvFiles().add(partial);
        RecordingSession session = new RecordingSession();
        new FILECommand().handle(session, context, "file.pkt 6 123 3");
        assertSame(partial, context.getRecvFiles().peek());
        assertEquals(1, context.getRecvFiles().size());
        assertArrayEquals(new byte[]{1, 2, 3}, output.toByteArray());
        assertTrue(session.frames.isEmpty());
    }

    private static class RecordingSession implements Session {
        final List<BinkFrame> frames = new ArrayList<>();
        FilesSender<Session> sender;
        TrafficCrypter crypter;
        boolean closed;

        @Override
        public void write(Object message) {
            BinkFrame frame = (BinkFrame) message;
            frames.add(new BinkFrame(frame.getDataInfo(), frame.getData().clone()));
        }

        byte[] bytes() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (BinkFrame frame : frames) {
                if (frame.getDataInfo() >= 0) {
                    bytes.write(frame.getData());
                }
            }
            return bytes.toByteArray();
        }

        @Override
        public void close(boolean close) {
            closed = true;
        }

        @Override
        public FilesSender<Session> getFileSender() {
            return sender;
        }

        @Override
        public void setFileSender(FilesSender sender) {
            this.sender = sender;
        }

        @Override
        public TrafficCrypter getTrafficCrypter() {
            return crypter;
        }

        @Override
        public void setTrafficCrypter(TrafficCrypter crypter) {
            this.crypter = crypter;
        }
    }
}
