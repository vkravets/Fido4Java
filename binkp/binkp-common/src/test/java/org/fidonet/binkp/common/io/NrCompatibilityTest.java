package org.fidonet.binkp.common.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.commands.EOBCommand;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.GETCommand;
import org.fidonet.binkp.common.commands.GOTCommand;
import org.fidonet.binkp.common.commands.OPTCommand;
import org.fidonet.binkp.common.commands.PWDCommand;
import org.fidonet.binkp.common.commands.SKIPCommand;
import org.fidonet.binkp.common.commands.TRFCommand;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.protocol.Session;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class NrCompatibilityTest {
    @TempDir
    public File temporary;

    private SessionContext context() {
        SessionContext context = handshakeContext();
        context.beginTransfer();
        return context;
    }

    private SessionContext handshakeContext() {
        StationConfig config = new StationConfig("station", "sysop", "city", "BINKP", "1:500/101");
        config.setPartialDirectory(null);
        SessionContext context = new SessionContext(config, new LinksInfo(new ArrayList<>()));
        context.setServerRole(ServerRole.SERVER);
        return context;
    }

    private FileTransferInfo info(long offset) {
        FileTransferInfo i = new FileTransferInfo("hello world.pkt", 6, 123);
        i.setOffset(offset);
        return i;
    }

    @Test
    public void checksNrPermissionAndNegotiationForBothRoles() throws Exception {
        for (ServerRole role : ServerRole.values()) {
            for (boolean enabled : new boolean[]{false, true}) {
                for (boolean requested : new boolean[]{false, true}) {
                    for (boolean peerRequested : new boolean[]{false, true}) {
                        SessionContext context = context();
                        context.setServerRole(role);
                        StationConfig config = context.getStationConfig();
                        config.setNREnabled(enabled);
                        config.setNRRequested(requested);
                        RecordingSession session = new RecordingSession();
                        String scenario = role
                                          + " enabled="
                                          + enabled
                                          + " request="
                                          + requested
                                          + " peer="
                                          + peerRequested;
                        new OPTCommand().send(session, context);
                        assertEquals(enabled && requested,
                                     Arrays.asList(session.arguments(0).trim().split("\\s+"))
                                           .contains("NR"),
                                     scenario);
                        new OPTCommand().handle(session, context, peerRequested ? "OPT NR" : "OPT");
                        assertEquals(enabled && peerRequested, context.isNRMode(), scenario);
                        context.getSendFiles()
                               .add(new FileData<>(info(0), new ByteArrayInputStream(new byte[6])));
                        assertEquals(enabled && peerRequested,
                                     new FILECommand().getCommandArguments(context).endsWith(" -1"),
                                     scenario);
                    }
                }
            }
        }
    }

    @Test
    public void negotiatesClientAndServerDirectionsIndependently() throws Exception {
        for (boolean clientRequests : new boolean[]{false, true}) {
            for (boolean serverRequests : new boolean[]{false, true}) {
                SessionContext client = context();
                SessionContext server = context();
                client.setServerRole(ServerRole.CLIENT);
                client.getStationConfig().setNRRequested(clientRequests);
                server.getStationConfig().setNRRequested(serverRequests);
                RecordingSession clientWire = new RecordingSession(),
                        serverWire = new RecordingSession();
                new OPTCommand().send(clientWire, client);
                new OPTCommand().send(serverWire, server);
                new OPTCommand().handle(serverWire, server, clientWire.arguments(0));
                new OPTCommand().handle(clientWire, client, serverWire.arguments(0));
                assertEquals(clientRequests, server.isNRMode());
                assertEquals(serverRequests, client.isNRMode());
            }
        }
    }

    @Test
    public void switchesFutureFilesToNrAfterALateRequestInEitherRole() throws Exception {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = context();
            context.setServerRole(role);
            context.getSendFiles()
                   .add(new FileData<>(new FileTransferInfo("first.pkt", 1, 11),
                                       new ByteArrayInputStream(new byte[]{1})));
            context.getSendFiles()
                   .add(new FileData<>(new FileTransferInfo("second.pkt", 1, 12),
                                       new ByteArrayInputStream(new byte[]{2})));
            RecordingSession session = new RecordingSession() {
                @Override
                public void write(Object message) {
                    super.write(message);
                    BinkFrame frame = (BinkFrame) message;
                    try {
                        if (frame.getDataInfo() >= 0) {
                            if (frame.getData()[0] == 1) {
                                new OPTCommand().handle(this, context, "OPT NR");
                                new GOTCommand().handle(this, context, "first.pkt 1 11");
                            } else {
                                new GOTCommand().handle(this, context, "second.pkt 1 12");
                            }
                        } else if (new String(frame.getData(),
                                              StandardCharsets.US_ASCII).endsWith(" -1")) {
                            new GETCommand().handle(this, context, "second.pkt 1 12 0");
                        }
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }
            };
            session.sender = new FilesSender<>(session, context.getSendFiles(), context);
            session.sender.run();
            assertEquals("first.pkt 1 11 0", session.arguments(0));
            assertEquals("second.pkt 1 12 -1", session.arguments(2));
            assertEquals("second.pkt 1 12 0", session.arguments(3));
            assertTrue(context.isSendingIsFinish());
            assertTrue(context.getPendingFiles().isEmpty());
        }
    }

    @Test
    public void resetsNegotiatedNrOnReconnectButKeepsLocalPolicyInBothRoles() throws Exception {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = context();
            context.setServerRole(role);
            context.getStationConfig().setNRRequested(true);
            new OPTCommand().handle(new RecordingSession(), context, "OPT NR");
            assertTrue(context.isNRMode());
            new OPTCommand().handle(new RecordingSession(), context, "OPT unknown");
            assertTrue(context.isNRMode());
            context.prepareTransfer();
            assertFalse(context.isNRMode());
            assertTrue(context.getStationConfig().isNRRequested());
            context.getStationConfig().setNREnabled(false);
            context.setNRMode(true);
            assertFalse(context.isNRMode());
        }
    }

    @Test
    public void receivesNrOffsetRequestsAndDataInBothRoles() throws Exception {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = context();
            context.setServerRole(role);
            context.getStationConfig().setNRRequested(true);
            RecordingSession session = new RecordingSession();
            new OPTCommand().send(session, context);
            new FILECommand().handle(session, context, "hello\\x20world.pkt 6 123 -1");
            assertEquals("hello\\x20world.pkt 6 123 0", session.arguments(1));
            assertFalse(context.isNRMode());
            new FILECommand().handle(session, context, "hello\\x20world.pkt 6 123 0");
            ReceiveTransfer.accept(session, context, "abcdef".getBytes(StandardCharsets.US_ASCII));
            assertEquals("hello\\x20world.pkt 6 123", session.arguments(2));
            assertEquals(6, context.getRecvFilesSize());
        }
    }

    @Test
    public void completesAccordingToRoleAndRejectsMissingRole() {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = context();
            context.setServerRole(role);
            RecordingSession session = new RecordingSession();
            context.setSendingIsFinish(true);
            context.setReceivingIsFinish(true);
            context.finishSessionIfComplete(session);
            assertEquals(SessionState.STATE_END, context.getState());
            assertEquals(role == ServerRole.SERVER, session.closed);
        }
        SessionContext unset = new SessionContext(new StationConfig("station",
                                                                    "sysop",
                                                                    "city",
                                                                    "BINKP",
                                                                    "1:500/101"),
                                                  new LinksInfo());
        unset.setSendingIsFinish(true);
        unset.setReceivingIsFinish(true);
        try {
            unset.finishSessionIfComplete(new RecordingSession());
            fail();
        } catch (NullPointerException expected) {
            assertNull(unset.getState());
        }
        try {
            unset.setServerRole(null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void trafficSummaryDoesNotDeclareTransmissionFinishedBeforeEob() {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        assertEquals("TRF 0 0", new TRFCommand().getCommandArguments(context));
        context.setReceivingIsFinish(true);
        context.finishSessionIfComplete(session);
        assertFalse(context.isSendingIsFinish());
        assertFalse(session.closed);
    }

    @Test
    public void honoursRemoteNrAndDoesNotConfuseLocalRequestWithRemoteRequest() throws Exception {
        SessionContext context = context();
        context.getStationConfig().setNRMode(true);
        context.getSendFiles().add(new FileData<>(info(0), new ByteArrayInputStream(new byte[6])));
        assertTrue(new FILECommand().getCommandArguments(context).endsWith(" 0"));
        new OPTCommand().handle(new RecordingSession(), context, "OPT NR");
        assertTrue(new FILECommand().getCommandArguments(context).endsWith(" -1"));
    }

    @Test
    public void answersGetWithFileHeaderBeforeDataAndDoesNotLoseGetDuringIncomingFile()
            throws Exception {
        SessionContext context = context();
        context.setNRMode(true);
        RecordingSession session = new RecordingSession();
        context.getSendFiles()
               .add(new FileData<>(info(0),
                                   new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.US_ASCII))));
        FilesSender<Session> sender = new FilesSender<>(session, context.getSendFiles(), context);
        session.sender = sender;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = executor.submit(sender);
            awaitFrames(session, 1);
            assertEquals("hello\\x20world.pkt 6 123 -1", session.arguments(0));
            new FILECommand().handle(session, context, "incoming.pkt 2 124 0");
            new GETCommand().handle(session, context, "hello\\x20world.pkt 6 123 3");
            awaitFrames(session, 3);
            assertEquals("hello\\x20world.pkt 6 123 3", session.arguments(1));
            assertArrayEquals("def".getBytes(StandardCharsets.US_ASCII),
                              session.frames.get(2).getData());
            assertFalse(context.isSendingIsFinish());
            assertEquals(1, context.getPendingFiles().size());
            new EOBCommand().handle(session, context, "");
            assertFalse(session.closed);
            new GOTCommand().handle(session, context, "hello\\x20world.pkt 6 123");
            run.get(2, TimeUnit.SECONDS);
            assertTrue(context.isTransferComplete());
            assertTrue(session.closed);
            assertEquals(6, context.getSendFilesSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void resumesFromDiskInANewContextAndAcknowledgesOnlyAfterCompletion() throws Exception {
        Path directory = Files.createTempDirectory(temporary.toPath(), "test-");
        SessionContext first = context();
        first.getStationConfig().setPartialDirectory(directory);
        RecordingSession session = new RecordingSession();
        new FILECommand().handle(session, first, "hello\\x20world.pkt 6 123 -1");
        assertEquals("hello\\x20world.pkt 6 123 0", session.arguments(0));
        new FILECommand().handle(session, first, "hello\\x20world.pkt 6 123 0");
        ReceiveTransfer.accept(session, first, "abc".getBytes(StandardCharsets.US_ASCII));
        assertEquals(1, session.frames.size());
        first.closeReceiveStreams();
        SessionContext second = context();
        second.getStationConfig().setPartialDirectory(directory);
        RecordingSession resumed = new RecordingSession();
        new FILECommand().handle(resumed, second, "hello\\x20world.pkt 6 123 -1");
        assertEquals("hello\\x20world.pkt 6 123 3", resumed.arguments(0));
        FileData<OutputStream> file = second.getRecvFiles().peek();
        new FILECommand().handle(resumed, second, "hello\\x20world.pkt 6 123 3");
        ReceiveTransfer.accept(resumed, second, "def".getBytes(StandardCharsets.US_ASCII));
        assertEquals("hello\\x20world.pkt 6 123", resumed.arguments(1));
        assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII),
                          ((ByteArrayOutputStream) file.getStream()).toByteArray());
        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        ((ByteArrayOutputStream) file.getStream()).writeTo(exported);
        assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII), exported.toByteArray());
        assertTrue(file.getInfo().isFinished());
        assertTrue(second.getRecvFiles().isEmpty());
    }

    @Test
    public void isolatesPartialsByRemoteAndAllFileMetadata() throws Exception {
        Path directory = Files.createTempDirectory(temporary.toPath(), "test-");
        SessionContext first = context();
        first.getStationConfig().setPartialDirectory(directory);
        FileData<OutputStream> file = ReceiveTransfer.open(first, info(0));
        file.getStream().write(new byte[]{1, 2, 3});
        file.getStream().close();
        FileData<OutputStream> different = ReceiveTransfer.open(first,
                                                                new FileTransferInfo("hello world.pkt",
                                                                                     6,
                                                                                     124));
        assertEquals(0, different.getInfo().getCurSize());
        different.getStream().close();
        Link remote = new Link(new FTNAddr(1, 500, 102, 0),
                               Collections.emptyList(),
                               "secret",
                               "localhost",
                               24554);
        first.getLinksInfo().getLinks().add(remote);
        first.getLinksInfo().setCurLink(remote.getAddr());
        different = ReceiveTransfer.open(first, info(0));
        assertEquals(0, different.getInfo().getCurSize());
        different.getStream().close();
    }

    @Test
    public void handlesEmptyFilesAndRejectsExcessDataWithoutGot() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        new FILECommand().handle(session, context, "empty.pkt 0 123 -1");
        assertEquals("empty.pkt 0 123", session.arguments(0));
        assertTrue(context.getRecvFiles().isEmpty());
        new FILECommand().handle(session, context, "small.pkt 1 123 0");
        try {
            ReceiveTransfer.accept(session, context, new byte[]{1, 2});
            fail();
        } catch (IOException expected) {
        }
        assertEquals(1, session.frames.size());
        assertFalse(context.getRecvFiles().peek().getInfo().isFinished());
    }

    @Test
    public void handlesGotOrSkipInsteadOfGetWithoutBlockingTheReceiver() throws Exception {
        for (boolean got : new boolean[]{true, false}) {
            SessionContext context = context();
            context.setNRMode(true);
            RecordingSession session = new RecordingSession();
            context.getSendFiles()
                   .add(new FileData<>(info(0), new ByteArrayInputStream(new byte[6])));
            session.sender = new FilesSender<>(session, context.getSendFiles(), context);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> run = executor.submit(session.sender);
                awaitFrames(session, 1);
                if (got) {
                    new GOTCommand().handle(session, context, "hello\\x20world.pkt 6 123");
                } else {
                    new SKIPCommand().handle(session, context, "hello\\x20world.pkt 6 123");
                }
                run.get(2, TimeUnit.SECONDS);
                assertEquals(2, session.frames.size());
                assertTrue(context.getPendingFiles().isEmpty());
                if (!got) {
                    assertEquals(1, context.getDeferredFiles().size());
                    context.prepareTransfer();
                    assertEquals(1, context.getSendFiles().size());
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    public void retransmitsPendingFileAtZeroWithoutReenteringNrHandshake() throws Exception {
        SessionContext context = context();
        context.setNRMode(true);
        RecordingSession session = new RecordingSession();
        context.getSendFiles()
               .add(new FileData<>(info(0),
                                   new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.US_ASCII))));
        session.sender = new FilesSender<>(session, context.getSendFiles(), context);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = executor.submit(session.sender);
            awaitFrames(session, 1);
            new GETCommand().handle(session, context, "hello\\x20world.pkt 6 123 3");
            awaitFrames(session, 3);
            new GETCommand().handle(session, context, "hello\\x20world.pkt 6 123 0");
            awaitFrames(session, 5);
            assertEquals("hello\\x20world.pkt 6 123 0", session.arguments(3));
            assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII),
                              session.frames.get(4).getData());
            new GOTCommand().handle(session, context, "hello\\x20world.pkt 6 123");
            run.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void escapesSpaceAndBackslashAndAcceptsLegacyEscapes() throws Exception {
        FileTransferInfo info = new FileTransferInfo("a b\\c", 3, 123);
        assertEquals("a\\x20b\\x5cc 3 123", info.arguments());
        assertEquals(info, FileTransferInfo.parseFileInfo(info.arguments()));
        assertEquals("a b", FileTransferInfo.parseFileInfo("a\\20b 3 123").getName());
    }

    @Test
    public void computesCramForLongPasswordsAndBothDigestAlgorithms() throws Exception {
        for (String algorithm : new String[]{"MD5", "SHA1"}) {
            String password = "p".repeat(65);
            byte[] challenge = {0, 17, 34, 51, 68, 85, 102, 119};
            String actual = new Password(password,
                                         true,
                                         MessageDigest.getInstance(algorithm),
                                         "0011223344556677").getText();
            Mac mac = Mac.getInstance("Hmac" + algorithm);
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.US_ASCII),
                                       "Hmac" + algorithm));
            StringBuilder expected = new StringBuilder("CRAM-" + algorithm + "-");
            for (byte b : mac.doFinal(challenge)) {
                expected.append(String.format("%02x", b));
            }
            assertEquals(expected.toString(), actual);
        }
    }

    @Test
    public void discardsDataUntilRequestedOffsetIsConfirmed() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FileData<OutputStream> partial = new FileData<>(info(0), new ByteArrayOutputStream());
        partial.getInfo().setCurSize(3);
        partial.getStream().write("abc".getBytes(StandardCharsets.US_ASCII));
        context.getRecvFiles().add(partial);
        new FILECommand().handle(session, context, "hello\\x20world.pkt 6 123 0");
        ReceiveTransfer.accept(session, context, "abcdef".getBytes(StandardCharsets.US_ASCII));
        assertEquals(3, partial.getInfo().getCurSize());
        new FILECommand().handle(session, context, "hello\\x20world.pkt 6 123 3");
        ReceiveTransfer.accept(session, context, "def".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII),
                          ((ByteArrayOutputStream) partial.getStream()).toByteArray());
    }

    @Test
    public void doesNotAcknowledgeIfClosingReceiveStreamFails() throws Exception {
        SessionContext context = context();
        RecordingSession session = new RecordingSession();
        FileData<OutputStream> file = new FileData<>(new FileTransferInfo("file.pkt", 1, 123),
                                                     new ByteArrayOutputStream() {
                                                         @Override
                                                         public void close() throws IOException {
                                                             throw new IOException("disk failure");
                                                         }
                                                     });
        context.getRecvFiles().add(file);
        try {
            ReceiveTransfer.accept(session, context, new byte[]{1});
            fail();
        } catch (IOException expected) {
        }
        assertTrue(session.frames.isEmpty());
        assertFalse(file.getInfo().isFinished());
    }

    @Test
    public void requeuesUnacknowledgedFilesForANewSession() {
        SessionContext context = context();
        FileData<InputStream> file = new FileData<>(info(3), new ByteArrayInputStream(new byte[6]));
        context.getPendingFiles().put(file.getInfo(), file);
        context.setSendingIsFinish(true);
        context.setReceivingIsFinish(true);
        context.prepareTransfer();
        context.setNRMode(true);
        assertSame(file, context.getSendFiles().peek());
        assertTrue(file.isRewindBeforeSend());
        assertTrue(context.getPendingFiles().isEmpty());
        assertFalse(context.isTransferComplete());
        assertTrue(new FILECommand().getCommandArguments(context).endsWith(" -1"));
    }

    @Test
    public void negotiatesCramIndependentlyOfTrafficEncryptionAndOtherOptions() throws Exception {
        SessionContext context = context();
        Link link = new Link(new FTNAddr(1, 500, 102, 0),
                             Collections.emptyList(),
                             "secret",
                             "localhost",
                             24554);
        link.setMD(true);
        context.getLinksInfo().getLinks().add(link);
        context.getLinksInfo().setCurLink(link.getAddr());
        new OPTCommand().handle(new RecordingSession(),
                                context,
                                "OPT NR CRAM-MD5/SHA1-0011223344556677");
        assertFalse(context.isCryptMode());
        assertTrue(context.getPassword().isCrypt());
        assertEquals("CRAM-MD5-", context.getPassword().getText().substring(0, 9));
    }

    @Test
    public void verifiesIncomingCramPasswordWithoutCryptMode() throws Exception {
        SessionContext context = handshakeContext();
        Link link = new Link(new FTNAddr(1, 500, 102, 0),
                             Collections.emptyList(),
                             "secret",
                             "localhost",
                             24554);
        context.getLinksInfo().getLinks().add(link);
        context.getLinksInfo().setCurLink(link.getAddr());
        context.setState(SessionState.STATE_WAITPWD);
        context.setMessageDigest(MessageDigest.getInstance("MD5"));
        context.setPasswordKey("0011223344556677");
        String response = new Password("secret",
                                       true,
                                       MessageDigest.getInstance("MD5"),
                                       context.getPasswordKey()).getText();
        RecordingSession session = new RecordingSession();
        new PWDCommand().handle(session, context, response);
        assertTrue(context.getPassword().isCrypt());
        assertFalse(context.isCryptMode());
        assertEquals(4, session.frames.get(0).getData()[0]);
        awaitFrames(session, 2);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!context.isSendingIsFinish() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(context.isSendingIsFinish());
    }

    private void awaitFrames(RecordingSession session, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (session.frames.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(session.frames.size() >= count,
                   "Expected " + count + " frames, got " + session.frames.size());
    }

    private static class RecordingSession implements Session {
        final List<BinkFrame> frames = new CopyOnWriteArrayList<>();
        FilesSender<Session> sender;
        boolean closed;
        TrafficCrypter crypter;

        public void write(Object message) {
            BinkFrame frame = (BinkFrame) message;
            frames.add(new BinkFrame(frame.getDataInfo(), frame.getData().clone()));
        }

        String arguments(int index) {
            return new String(frames.get(index).getData(), StandardCharsets.US_ASCII).substring(1);
        }

        public void close(boolean close) {
            closed = true;
        }

        public FilesSender<Session> getFileSender() {
            return sender;
        }

        public void setFileSender(FilesSender sender) {
            this.sender = sender;
        }

        public TrafficCrypter getTrafficCrypter() {
            return crypter;
        }

        public void setTrafficCrypter(TrafficCrypter crypter) {
            this.crypter = crypter;
        }
    }
}
