package org.fidonet.binkp.common.io;

import static org.fidonet.binkp.common.SessionTestFixtures.context;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fidonet.binkp.common.RecordingSession;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.protocol.Session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FilesSenderBoundaryTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private FileData<InputStream> file(InputStream input, long size, long offset) {
        FileInfo info = new FileInfo("file", size, 123);
        info.setOffset(offset);
        return new FileData<>(info, input);
    }

    @Test
    public void rejectsBothShortAndOversizedInputWithoutSuccessfulFinish() throws Exception {
        for (int length : new int[]{2, 4}) {
            RecordingSession session = new RecordingSession();
            FileData<InputStream> data = file(new ByteArrayInputStream(new byte[length]), 3, 0);
            try {
                new FilesSender<>(session, new ArrayDeque<>(), context()).send(data);
                fail("Size mismatch accepted");
            } catch (IOException expected) {
                assertFalse(data.getInfo().isFinished());
            }
        }
    }

    @Test
    public void resumesFromFileChannelAndRewindsForRetransmission() throws Exception {
        File path = temporary.newFile();
        Files.write(path.toPath(), new byte[]{1, 2, 3});
        RecordingSession session = new RecordingSession();
        try (FileInputStream input = new FileInputStream(path)) {
            input.read();
            input.read();
            FileData<InputStream> data = file(input, 3, 1);
            data.setRewindBeforeSend(true);
            assertTrue(new FilesSender<>(session, new ArrayDeque<>(), context()).send(data));
            assertArrayEquals(new byte[]{2, 3}, session.frames.get(0).getData());
        }
    }

    @Test
    public void rewindsMarkedStreamsAndRejectsNonseekableRetransmission() throws Exception {
        ByteArrayInputStream marked = new ByteArrayInputStream(new byte[]{1, 2, 3});
        marked.mark(3);
        marked.read();
        FileData<InputStream> data = file(marked, 3, 0);
        data.setRewindBeforeSend(true);
        RecordingSession session = new RecordingSession();
        assertTrue(new FilesSender<>(session, new ArrayDeque<>(), context()).send(data));
        assertArrayEquals(new byte[]{1, 2, 3}, session.frames.get(0).getData());
        InputStream nonseekable = new FilterInputStream(new ByteArrayInputStream(new byte[]{1})) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };
        FileData<InputStream> invalid = file(nonseekable, 1, 0);
        invalid.setRewindBeforeSend(true);
        try {
            new FilesSender<>(session, new ArrayDeque<>(), context()).send(invalid);
            fail("Nonseekable rewind accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("seek"));
        }
    }

    @Test
    public void interruptedSkippedOrAcknowledgedNrWaitExitsPromptly() throws Exception {
        for (String action : new String[]{"cancel", "skip", "acknowledge"}) {
            SessionContext context = context();
            RecordingSession session = new RecordingSession();
            FilesSender<Session> sender = new FilesSender<>(session,
                                                            context.getSendFiles(),
                                                            context);
            FileData<InputStream> data = file(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, -1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> sent = executor.submit(() -> sender.send(data));
                awaitWaitGet(context);
                if (action.equals("cancel")) {
                    sender.cancel();
                } else if (action.equals("skip")) {
                    sender.skip(data.getInfo(), false);
                } else {
                    data.getInfo().setFinished(true);
                    sender.acknowledge(data.getInfo());
                }
                assertFalse(sent.get(2, TimeUnit.SECONDS));
                assertTrue(session.frames.isEmpty());
            } finally {
                stopSender(sender, executor);
            }
        }
    }

    @Test
    public void invalidResumeDoesNotUnblockTransferAndValidResumeStillWorks() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FilesSender<Session> sender = new FilesSender<>(session, context.getSendFiles(), context);
        FileData<InputStream> data = file(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, -1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> sent = executor.submit(() -> sender.send(data));
            awaitWaitGet(context);
            for (long offset : new long[]{-1, 4}) {
                FileInfo invalid = new FileInfo("file", 3, 123);
                invalid.setOffset(offset);
                try {
                    sender.resume(invalid);
                    fail("Invalid GET accepted");
                } catch (IOException expected) {
                }
            }
            FileInfo valid = new FileInfo("file", 3, 123);
            valid.setOffset(3);
            sender.resume(valid);
            assertTrue(sent.get(2, TimeUnit.SECONDS));
            assertEquals(1, session.frames.size()); // M_FILE, no data for an EOF resume.
            assertEquals(3, data.getInfo().getCurSize());
        } finally {
            stopSender(sender, executor);
        }
    }

    @Test
    public void skipAndResumeFindQueuedFilesAndPreserveRewindFlags() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FilesSender<Session> sender = new FilesSender<>(session, context.getSendFiles(), context);
        FileData<InputStream> data = file(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, 0);
        context.getSendFiles().add(data);
        sender.skip(new FileInfo("unknown", 1, 123), false);
        assertFalse(data.getInfo().isShouldSkip());
        FileInfo invalid = new FileInfo("file", 3, 123);
        invalid.setOffset(4);
        try {
            sender.skip(invalid, true);
            fail("Invalid resend offset accepted");
        } catch (IOException expected) {
            assertFalse(data.getInfo().isShouldSkip());
        }
        FileInfo valid = new FileInfo("file", 3, 123);
        valid.setOffset(1);
        sender.resume(valid);
        FileData<InputStream> retry = context.getSendFiles().peek();
        assertNotSame(data, retry);
        assertSame(data.getStream(), retry.getStream());
        assertTrue(retry.isRewindBeforeSend());
        assertTrue(retry.isResumeResponse());
        assertEquals(1, retry.getInfo().getOffset());
        assertTrue(data.getInfo().isShouldSkip());
    }

    @Test
    public void cancellationStopsQueuedTransferAndTransportFailureProducesError() {
        SessionContext cancelled = context();
        RecordingSession session = new RecordingSession();
        cancelled.getSendFiles().add(file(new ByteArrayInputStream(new byte[]{1}), 1, 0));
        FilesSender<Session> sender = new FilesSender<>(session,
                                                        cancelled.getSendFiles(),
                                                        cancelled);
        sender.cancel();
        sender.run();
        assertTrue(session.frames.isEmpty());
        assertFalse(cancelled.isSendingIsFinish());
        SessionContext failed = context();
        RecordingSession broken = new RecordingSession() {
            @Override
            public void write(Object frame) {
                throw new IllegalStateException("transport failed");
            }
        };
        failed.getSendFiles().add(file(new ByteArrayInputStream(new byte[]{1}), 1, 0));
        new FilesSender<>(broken, failed.getSendFiles(), failed).run();
        assertEquals(SessionState.STATE_ERR, failed.getState());
        assertEquals("transport failed", failed.getLastErrorMessage());
        assertTrue(broken.closed);
        assertFalse(failed.isSendingIsFinish());
    }

    private void awaitWaitGet(SessionContext context) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (context.getState() != SessionState.STATE_WAITGET && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(SessionState.STATE_WAITGET, context.getState());
    }

    private void stopSender(FilesSender<Session> sender, ExecutorService executor)
            throws InterruptedException {
        sender.cancel();
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }
}
