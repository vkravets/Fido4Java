package org.fidonet.binkp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.commands.EOBCommand;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.OPTCommand;
import org.fidonet.binkp.common.commands.SKIPCommand;
import org.fidonet.binkp.common.commands.VERCommand;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.commands.share.CommandFactory;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.BinkData;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.ReceiveTransfer;
import org.fidonet.types.FTNAddr;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Binkp11Test {
    @Test
    public void negotiatesVersionAndFreezesItAtLogin() throws Exception {
        SessionContext context = SessionTestFixtures.context();
        RecordingSession session = new RecordingSession();
        VERCommand version = new VERCommand();
        assertTrue(version.getCommandArguments(context).endsWith("binkp/1.1"));
        assertFalse(context.isBinkp11());
        version.handle(session, context, "VER other/8 BiNkP/1.1");
        assertTrue(context.isBinkp11());
        context.beginTransfer();
        version.handle(session, context, "VER other/8 binkp/1.0");
        assertTrue(context.isBinkp11());
    }

    @Test
    public void missingLateAndMalformedVersionsFallBackTo10() throws Exception {
        for (String identification : new String[]{"VER other/8", "VER other binkp/x.y",
                "VER other binkp/999999999999999.1"}) {
            SessionContext context = SessionTestFixtures.context();
            RecordingSession session = new RecordingSession();
            new VERCommand().handle(session, context, identification);
            assertFalse(context.isBinkp11());
            context.beginTransfer();
            new VERCommand().handle(session, context, "VER other binkp/1.1");
            assertFalse(context.isBinkp11());
        }
    }

    @Test
    public void disabledNrAndExplicitLegacyModeAdvertise10() throws Exception {
        SessionContext context = SessionTestFixtures.context();
        context.getStationConfig().setNREnabled(false);
        context.setRemoteProtocol(1, 1);
        new OPTCommand().handle(new RecordingSession(), context, "OPT NR");
        assertFalse(context.isNRMode());
        assertFalse(context.isBinkp11());
        assertTrue(new VERCommand().getCommandArguments(context).endsWith("binkp/1.0"));
        context.getStationConfig().setNREnabled(true);
        context.getStationConfig().setBinkp11Enabled(false);
        assertFalse(context.isBinkp11());
        assertTrue(new VERCommand().getCommandArguments(context).endsWith("binkp/1.0"));
    }

    @Test
    public void emptyBatchEndsWithoutAnExtraBatch() throws Exception {
        SessionContext context = authenticated11();
        RecordingSession session = new RecordingSession();
        context.setSendingIsFinish(true);
        new EOBCommand().handle(session, context, "");
        assertEquals(SessionState.STATE_END, context.getState());
        assertTrue(session.closed);
        assertEquals(0, context.getBatchNumber());
    }

    @Test
    public void commandOnlyBatchRestartsAndPreservesAuthenticationAndOptions() throws Exception {
        SessionContext context = authenticated11();
        RecordingSession session = new RecordingSession();
        Password password = context.getPassword();
        context.setNRMode(true);
        context.setCryptMode(true);
        CountDownLatch rescanned = new CountDownLatch(1);
        AtomicReference<SessionContext> loaded = new AtomicReference<>();
        context.setOutboundLoader(current -> {
            loaded.set(current);
            rescanned.countDown();
        });
        new CommandFactory().createCommand(context, BinkCommand.M_NUL, "TRF 0 0");
        context.setSendingIsFinish(true);
        new EOBCommand().handle(session, context, "");
        assertTrue(rescanned.await(2, TimeUnit.SECONDS));
        assertSame(context, loaded.get());
        assertEquals(1, context.getBatchNumber());
        assertSame(password, context.getPassword());
        assertTrue(context.isNRMode());
        assertTrue(context.isCryptMode());
        assertFalse(session.closed);
        finishEmptyBatch(context, session);
    }

    @Test
    public void pendingAcknowledgementsBlockRolloverAndSkippedFilesAreNotRetried()
            throws Exception {
        SessionContext context = authenticated11();
        RecordingSession session = new RecordingSession();
        FileData<InputStream> file = new FileData<>(new FileInfo("deferred", 1, 123),
                                                    new ByteArrayInputStream(new byte[]{1}));
        context.getPendingFiles().put(file.getInfo(), file);
        context.recordCommand(BinkCommand.M_FILE);
        context.setSendingIsFinish(true);
        context.setReceivingIsFinish(true);
        context.finishSessionIfComplete(session);
        assertEquals(0, context.getBatchNumber());
        assertFalse(session.closed);
        new SKIPCommand().handle(session, context, "deferred 1 123");
        assertEquals(1, context.getBatchNumber());
        assertTrue(context.getSendFiles().isEmpty());
        assertTrue(context.getDeferredFiles().contains(file));
        finishEmptyBatch(context, session);
    }

    @Test
    public void partialFileRemainsResumableAtTheNextBatch() throws Exception {
        SessionContext context = authenticated11();
        RecordingSession session = new RecordingSession();
        try {
            FILECommand offer = new FILECommand();
            offer.handle(session, context, "partial 4 123 0");
            ReceiveTransfer.accept(session, context, new byte[]{1, 2});
            FileData<?> partial = context.getRecvFiles().peek();
            new EOBCommand().handle(session, context, "");
            assertTrue(context.getRecvFiles().isEmpty());
            assertSame(partial, context.getNotFinishedFileData(new FileInfo("partial", 4, 123)));
            ReceiveTransfer.accept(session, context, new byte[]{99});
            assertEquals(2, partial.getInfo().getCurSize());
            offer.handle(session, context, "partial 4 123 -1");
            BinkData reply = BinkFrame.toBinkData(session.frames.get(session.frames.size() - 1));
            assertEquals(BinkCommand.M_GET.getCmd(), reply.getCommand());
            String arguments = new String(reply.getData(), StandardCharsets.UTF_8);
            assertEquals(2, FileInfo.parseFileInfo(arguments).getOffset());
        } finally {
            context.closeReceiveStreams();
        }
    }

    private void finishEmptyBatch(SessionContext context, RecordingSession session)
            throws Exception {
        new EOBCommand().handle(session, context, "");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((context.getState() != SessionState.STATE_END || !session.closed)
                && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(SessionState.STATE_END, context.getState());
        assertTrue(session.closed);
    }

    @Test
    public void legacyPeerEndsAfterTheFirstNonemptyBatch() throws Exception {
        SessionContext context = SessionTestFixtures.context();
        context.setServerRole(ServerRole.SERVER);
        context.beginTransfer();
        new CommandFactory().createCommand(context, BinkCommand.M_NUL, "TRF 0 0");
        context.setSendingIsFinish(true);
        RecordingSession session = new RecordingSession();
        new EOBCommand().handle(session, context, "");
        assertEquals(SessionState.STATE_END, context.getState());
        assertEquals(0, context.getBatchNumber());
    }

    @Test
    public void reconnectResetsVersionAndBatchStateButKeepsOutboundLoader() {
        SessionContext context = authenticated11();
        AtomicReference<SessionContext> loaded = new AtomicReference<>();
        context.setOutboundLoader(loaded::set);
        SessionContext copy = new SessionContext(context);
        copy.loadOutbound();
        assertSame(copy, loaded.get());
        context.prepareTransfer();
        assertFalse(context.isBinkp11());
        assertEquals(0, context.getBatchNumber());
        context.setRemoteProtocol(1, 1);
        assertTrue(context.isBinkp11());
    }

    private SessionContext authenticated11() {
        SessionContext context = SessionTestFixtures.loopbackContext("1:500/1",
                                                                     "1:500/2",
                                                                     "secret",
                                                                     0);
        context.getLinksInfo().setCurLink(new FTNAddr("1:500/2"));
        context.setServerRole(ServerRole.SERVER);
        context.setRemoteProtocol(1, 1);
        context.beginTransfer();
        return context;
    }
}
