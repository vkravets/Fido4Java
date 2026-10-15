package org.fidonet.binkp.common.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.RecordingSession;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.SessionTestFixtures;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.FilesSender;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;

public class TransferControlTest {
    @Test
    public void trafficMetadataUsesCountsAndRejectsWrongArgumentCount() throws Exception {
        SessionContext context = context();
        TRFCommand traffic = new TRFCommand();
        traffic.handle(new RecordingSession(), context, "TRF 12 34");
        assertEquals(12, context.getRecvMailSize());
        assertEquals(34, context.getRecvFilesSize());
        try {
            traffic.handle(new RecordingSession(), context, "TRF 12");
            fail("Missing size accepted");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void remoteErrorPreservesFailureInBothRoles() throws Exception {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = context();
            context.setServerRole(role);
            RecordingSession session = new RecordingSession();
            new ERRCommand().handle(session, context, "remote failure");
            assertEquals(SessionState.STATE_ERR, context.getState());
            assertEquals("remote failure", context.getLastErrorMessage());
            assertEquals(role == ServerRole.SERVER, session.closed);
        }
    }

    private SessionContext context() {
        SessionContext context = SessionTestFixtures.context();
        context.getStationConfig().setPartialDirectory(null);
        return context;
    }

    private FileData<InputStream> file(String name) {
        return new FileData<>(new FileInfo(name, 3, 123),
                              new ByteArrayInputStream(new byte[]{1, 2, 3}));
    }

    @Test
    public void skipDefersFileAndReconnectMakesItAvailableAgain() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FileData<InputStream> file = file("deferred");
        context.getPendingFiles().put(file.getInfo(), file);
        session.setFileSender(new FilesSender<>(session, context.getSendFiles(), context));
        new SKIPCommand().handle(session, context, file.getInfo().arguments());
        assertTrue(file.getInfo().isShouldSkip());
        assertTrue(context.getPendingFiles().isEmpty());
        assertSame(file, context.getDeferredFiles().peek());
        new SKIPCommand().handle(session, context, "unknown 3 123");
        assertEquals(1, context.getDeferredFiles().size());
        context.prepareTransfer();
        assertSame(file, context.getSendFiles().peek());
        assertFalse(file.getInfo().isShouldSkip());
    }

    @Test
    public void duplicateOrUnknownAcknowledgementDoesNotCountBytesTwice() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FileData<InputStream> file = file("sent");
        FileData<InputStream> other = file("other");
        context.getPendingFiles().put(file.getInfo(), file);
        context.getSendFiles().add(file);
        context.getSendFiles().add(other);
        GOTCommand got = new GOTCommand();
        got.handle(session, context, file.getInfo().arguments());
        assertTrue(file.getInfo().isFinished());
        assertEquals(3, context.getSendFilesSize());
        assertSame(other, context.getSendFiles().peek());
        got.handle(session, context, file.getInfo().arguments());
        got.handle(session, context, "unknown 3 123");
        assertEquals(3, context.getSendFilesSize());
    }

    @Test
    public void receiveControlArgumentsUseExactMetadataAndExistingLength() throws Exception {
        SessionContext context = context();
        assertNull(new GETCommand().getCommandArguments(context));
        assertNull(new GOTCommand().getCommandArguments(context));
        assertNull(new FILECommand().getCommandArguments(context));
        try {
            new SKIPCommand().getCommandArguments(context);
            fail("SKIP without metadata");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof ProtocolException);
        }
        FileInfo info = new FileInfo("with space", 6, 123);
        info.setCurSize(3);
        context.getRecvFiles().add(new FileData<>(info, new ByteArrayOutputStream()));
        assertEquals("with\\x20space 6 123 3", new GETCommand().getCommandArguments(context));
        assertEquals("with\\x20space 6 123", new GOTCommand().getCommandArguments(context));
        assertEquals("with\\x20space 6 123", new SKIPCommand().getCommandArguments(context));
        new GETCommand().handle(new RecordingSession(), context, "ignored without sender");
    }

    @Test
    public void fileOfferRejectsOffsetsOutsideAdvertisedSize() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        for (String args : new String[]{"file 3 123 -2", "file 3 123 4"}) {
            try {
                new FILECommand().handle(session, context, args);
                fail("Invalid offer accepted");
            } catch (IOException expected) {
            }
        }
        assertTrue(context.getRecvFiles().isEmpty());
        assertTrue(session.frames.isEmpty());
    }

    @Test
    public void serverSelectsKnownAliasOrRejectsUnknownPeerAndClientSendsPassword()
            throws Exception {
        SessionContext context = context();
        Link link = new Link("2:500/2, 2:500/1, secret, localhost");
        context.getLinksInfo().getLinks().add(link);
        context.setServerRole(ServerRole.SERVER);
        RecordingSession session = new RecordingSession();
        new ADRCommand().handle(session, context, "2:500/999 2:500/2");
        assertSame(link, context.getLinksInfo().getCurLink());
        assertEquals(SessionState.STATE_WAITPWD, context.getState());
        assertFalse(session.closed);
        SessionContext unknown = context();
        unknown.setServerRole(ServerRole.SERVER);
        RecordingSession rejected = new RecordingSession();
        new ADRCommand().handle(rejected, unknown, "2:500/999");
        assertEquals(SessionState.STATE_ERR, unknown.getState());
        assertTrue(rejected.closed);
        assertTrue(unknown.getLastErrorMessage().contains("2:500/999"));
        context.setServerRole(ServerRole.CLIENT);
        new ADRCommand().handle(session, context, "2:500/2");
        assertEquals(SessionState.STATE_WAITOK, context.getState());
    }
}
