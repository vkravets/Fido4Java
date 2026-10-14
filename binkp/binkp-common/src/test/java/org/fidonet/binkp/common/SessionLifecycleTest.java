package org.fidonet.binkp.common;

import static org.fidonet.binkp.common.SessionTestFixtures.context;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.types.Link;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SessionLifecycleTest {
    @Test
    public void localNrDisableOverridesPreviouslyReceivedRequest() {
        SessionContext context = context();
        context.setNRMode(true);
        assertTrue(context.isNRMode());
        context.getStationConfig().setNREnabled(false);
        assertFalse(context.isNRMode());
        context.setNRMode(false);
        assertFalse(context.isNRMode());
        context.getStationConfig().setNREnabled(true);
        assertFalse(context.isNRMode());
    }

    private FileData<InputStream> file(String name) {
        return new FileData<>(new FileInfo(name, 1, 123), new ByteArrayInputStream(new byte[]{1}));
    }

    @Test
    public void reconnectQueuesUnacknowledgedAndDeferredFilesExactlyOnceAndResetsNegotiation() {
        SessionContext context = context();
        FileData<InputStream> pending = file("pending");
        pending.getInfo().setFinished(true);
        pending.getInfo().setSkip(true);
        pending.getInfo().setOffset(1);
        pending.getInfo().setCurSize(1);
        pending.setResumeResponse(true);
        FileData<InputStream> deferred = file("deferred");
        context.getPendingFiles().put(pending.getInfo(), pending);
        context.getSendFiles().add(pending);
        context.getDeferredFiles().add(deferred);
        context.setNRMode(true);
        context.setCryptMode(true);
        context.setPasswordKey("abcd");
        context.setSendingIsFinish(true);
        context.setReceivingIsFinish(true);
        context.prepareTransfer();
        assertEquals(2, context.getSendFiles().size());
        assertTrue(context.getPendingFiles().isEmpty());
        assertTrue(context.getDeferredFiles().isEmpty());
        for (FileData<InputStream> file : context.getSendFiles()) {
            assertFalse(file.getInfo().isFinished());
            assertFalse(file.getInfo().isShouldSkip());
            assertEquals(0, file.getInfo().getOffset());
            assertEquals(0, file.getInfo().getCurSize());
            assertTrue(file.isRewindBeforeSend());
            assertFalse(file.isResumeResponse());
        }
        assertFalse(context.isSendingIsFinish());
        assertFalse(context.isReceivingIsFinish());
        assertFalse(context.isNRMode());
        assertFalse(context.isCryptMode());
        assertNull(context.getPasswordKey());
        context.prepareTransfer();
        assertEquals(2, context.getSendFiles().size());
    }

    @Test
    public void completionRequiresBothEobsAndAllFilesAcknowledgedForEitherRole() {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = context();
            context.setServerRole(role);
            RecordingSession session = new RecordingSession();
            context.finishSessionIfComplete(session);
            assertFalse(context.isTransferComplete());
            context.setSendingIsFinish(true);
            assertFalse(context.isTransferComplete());
            context.setReceivingIsFinish(true);
            FileData<InputStream> file = file("pending");
            context.getPendingFiles().put(file.getInfo(), file);
            assertFalse(context.isTransferComplete());
            context.getPendingFiles().clear();
            context.getSendFiles().add(file);
            assertFalse(context.isTransferComplete());
            context.getSendFiles().clear();
            assertTrue(context.isTransferComplete());
            context.finishSessionIfComplete(session);
            assertEquals(SessionState.STATE_END, context.getState());
            assertEquals(role == ServerRole.SERVER, session.closed);
            context.finishSessionIfComplete(session);
            assertEquals(SessionState.STATE_END, context.getState());
        }
    }

    @Test
    public void closesAllReceiveStreamsEvenWhenOneCloseFails() {
        SessionContext context = context();
        List<String> closed = new ArrayList<>();
        for (String name : new String[]{"first", "second"}) {
            OutputStream stream = new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    closed.add(name);
                    if (name.equals("first")) {
                        throw new IOException("failure");
                    }
                }
            };
            FileData<OutputStream> file = new FileData<>(new FileInfo(name, 1, 1), stream);
            if (name.equals("first")) {
                context.getRecvFiles().add(file);
            } else {
                context.setNotFinishedFiles(Collections.singletonList(file));
            }
        }
        context.closeReceiveStreams();
        assertEquals(Arrays.asList("first", "second"), closed);
        assertTrue(context.getRecvFiles().isEmpty());
        assertNull(context.getNotFinishedFileData(new FileInfo("second", 1, 1)));
        context.closeReceiveStreams();
        assertEquals(2, closed.size());
    }

    @Test
    public void copiesSelectedLinkButKeepsSessionQueuesAndStateIndependent() {
        Link link = new Link("2:500/2, 2:500/1, secret, localhost");
        SessionContext original = context();
        original.getLinksInfo().getLinks().add(link);
        assertTrue(original.getLinksInfo().setCurLink(link.getAddr()));
        SessionContext copy = new SessionContext(original);
        assertSame(link, copy.getLinksInfo().getCurLink());
        copy.getSendFiles().add(file("copy"));
        assertTrue(original.getSendFiles().isEmpty());
        copy.getLinksInfo().getLinks().clear();
        assertEquals(1, original.getLinksInfo().getLinks().size());
        assertEquals("secret", original.getPassword().getText());
        assertSame(original.getPassword(), original.getPassword());
        assertNull(context().getPassword());
        assertNull(context().getPassword((Link) null));
        assertEquals("secret", context().getPassword(link).getText());
        SessionContext noLink = new SessionContext(context());
        assertNull(noLink.getLinksInfo().getCurLink());
    }

    @Test
    public void lookupIgnoresFinishedAndNonmatchingReceiveFiles() {
        SessionContext context = context();
        FileInfo info = new FileInfo("wanted", 1, 123);
        FileData<OutputStream> finished = new FileData<>(new FileInfo("wanted", 1, 123),
                                                         new ByteArrayOutputStream());
        finished.getInfo().setFinished(true);
        context.setNotFinishedFiles(Arrays.asList(finished,
                                                  new FileData<>(new FileInfo("other", 1, 123),
                                                                 new ByteArrayOutputStream())));
        context.getRecvFiles().add(finished);
        assertNull(context.getNotFinishedFileData(info));
        FileData<OutputStream> active = new FileData<>(info, new ByteArrayOutputStream());
        context.getRecvFiles().add(active);
        assertSame(active, context.getNotFinishedFileData(info));
    }
}
