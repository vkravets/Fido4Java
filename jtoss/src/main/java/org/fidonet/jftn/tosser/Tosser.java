/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.EchoHistory;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.echobase.EchoRoutingStore;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.NetmailRoutingStore;
import org.fidonet.echobase.NetmailStore;
import org.fidonet.echobase.QueuedNetmail;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.events.HasEventBus;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.exception.TosserException;
import org.fidonet.misc.PacketArchive;
import org.fidonet.misc.PktTemp;
import org.fidonet.misc.ReceivedFileArchive;
import org.fidonet.misc.Zipper;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class Tosser extends HasEventBus {

    private static final Logger logger = LoggerFactory.getLogger(Tosser.class.getName());

    private EchoMgr areamgr;
    private Pattern bunlderegex;
    private JFtnConfig config;
    private final NetmailStore netmailStore;
    private final IBase base;
    private volatile NetmailRouter netmailRouter;
    private volatile EchoRouter echoRouter;
    private final PacketQuarantine quarantine;
    private String packetRejection = "Packet import was rejected; inspect the node log.";
    private final List<NetmailHandler> netmailHandlers = new ArrayList<>();

    public synchronized void addNetmailHandler(NetmailHandler handler) {
        netmailHandlers.add(Objects.requireNonNull(handler));
    }

    public synchronized void removeNetmailHandler(NetmailHandler handler) {
        netmailHandlers.remove(handler);
    }

    public Tosser(JFtnConfig config, IBase base) throws IOException {
        this.config = config;
        this.base = base;
        this.netmailStore = base instanceof NetmailStore ? (NetmailStore) base : null;
        this.netmailRouter = new NetmailRouter(config,
                                               base instanceof NetmailRoutingStore
                                                       ? (NetmailRoutingStore) base
                                                       : null);
        this.areamgr = new EchoMgr(base);
        this.echoRouter = new EchoRouter(config, base);
        this.quarantine = new PacketQuarantine(Path.of(config.getTmpDir()));
        if (EchoRouter.databaseSubscriptions(config) && !(base instanceof EchoRoutingStore)) {
            throw new IllegalArgumentException("Database echo subscriptions require an EchoRoutingStore");
        }
        this.bunlderegex = Pattern.compile(".*\\.[STFWMstfwm][ouaherOUAHER][0-9A-Za-z]");
    }

    private boolean isBunldeName(String str) {
        return bunlderegex.matcher(str).matches();
    }

    public synchronized void runFast(String dirname)
            throws TosserException, IOException, EchoBaseException {
        runFast(dirname, null);
    }

    public synchronized void runFast(String dirname, FTNAddr authenticatedPeer)
            throws TosserException, IOException, EchoBaseException {
        if (dirname == null) {
            throw new TosserException("Error opening directory as inbound. Dirname is null.");
        }
        final File dir = new File(dirname);
        final File[] files = dir.listFiles();
        if (files == null) {
            throw new TosserException("Directory " + dirname + " not found!");
        }
        long startTime = System.currentTimeMillis();
        for (File file : files) {
            if (isSupportedMailFile(file)) {
                processInboundFileSafely(file, authenticatedPeer);
            }
        }
        logger.debug("Tossing process takes {}ms", System.currentTimeMillis() - startTime);
    }

    private boolean isPacket(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".pkt");
    }

    private boolean isSupportedMailFile(File file) {
        return file.isFile() && (isPacket(file) || isBunldeName(file.getName()));
    }

    private void processInboundFileSafely(File file, FTNAddr authenticatedPeer) {
        try {
            long started = System.currentTimeMillis();
            logger.info("Processing inbound mail: file={}, sessionPeer={}",
                        file.getAbsolutePath(),
                        authenticatedPeer);
            processInboundFile(file, authenticatedPeer);
            logger.info("Finished processing inbound mail: file={}, elapsed={}ms",
                        file.getAbsolutePath(),
                        System.currentTimeMillis() - started);
        } catch (IOException | RuntimeException | EchoBaseException e) {
            logger.error("Failed to unpack {}. Details: {}",
                         file.getAbsolutePath(),
                         e.getMessage(),
                         e);
        } catch (TosserException e) {
            logger.error("Failed to toss {}. Details: {}",
                         file.getAbsolutePath(),
                         e.getMessage(),
                         e);
        }
    }

    private void processInboundFile(File file, FTNAddr authenticatedPeer)
            throws IOException, TosserException, EchoBaseException {
        ReceivedFileArchive.preserve(config, file.toPath(), file.getName());
        if (!isPacket(file)) {
            processBundle(file, authenticatedPeer);
            deleteTossedFile(file);
            return;
        }
        LinkedList<PktTemp> packets = readPackets(file);
        while (!packets.isEmpty()) {
            processPacket(packets.pop(), authenticatedPeer);
        }
        deleteTossedFile(file);
    }

    private void processBundle(File file, FTNAddr authenticatedPeer)
            throws IOException, TosserException, EchoBaseException {
        try (PacketArchive archive = Zipper.unpackToDisk(file.getAbsolutePath())) {
            processArchivePackets(archive, authenticatedPeer);
        }
    }

    private void processArchivePackets(PacketArchive archive, FTNAddr authenticatedPeer)
            throws IOException, TosserException, EchoBaseException {
        PktTemp packet;
        while ((packet = archive.readNext()) != null) {
            processPacket(packet, authenticatedPeer);
        }
    }

    private LinkedList<PktTemp> readPackets(File file) throws IOException {
        LinkedList<PktTemp> packets = new LinkedList<>();
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] bytes = input.readNBytes(Zipper.MAX_PACKET_BYTES + 1);
            if (bytes.length > Zipper.MAX_PACKET_BYTES) {
                throw new IOException("Packet exceeds size limit");
            }
            packets.add(new PktTemp(file.getName(), ByteBuffer.wrap(bytes)));
        }
        return packets;
    }

    private void processPacket(PktTemp packet, FTNAddr authenticatedPeer)
            throws IOException, TosserException, EchoBaseException {
        boolean accepted;
        try {
            accepted = tosspkt(packet.getPkt(), authenticatedPeer);
        } catch (IOException | EchoBaseException | RuntimeException failure) {
            retainFailedPacket(packet, authenticatedPeer, failure);
            throw failure;
        }
        if (!accepted) {
            saveBad(packet, authenticatedPeer);
            logger.warn("Rejected packet retained in temporary directory: packet={}, directory={}",
                        packet.getName(),
                        config.getTmpDir());
        }
    }

    private void retainFailedPacket(PktTemp packet, FTNAddr peer, Exception failure) {
        packetRejection = "Packet processing failed; inspect the node log.";
        try {
            saveBad(packet, peer);
        } catch (IOException | TosserException retentionFailure) {
            failure.addSuppressed(retentionFailure);
        }
    }

    public synchronized PacketQuarantine.Page quarantine(String after, int limit)
            throws IOException {
        return quarantine.list(after, limit);
    }

    public synchronized Runnable prepareConfiguration(JFtnConfig candidate) {
        NetmailRouter nextNetmail = new NetmailRouter(candidate,
                                                      netmailStore instanceof NetmailRoutingStore
                                                              ? (NetmailRoutingStore) netmailStore
                                                              : null);
        EchoRouter nextEcho = new EchoRouter(candidate, base);
        return () -> {
            netmailRouter = nextNetmail;
            echoRouter = nextEcho;
        };
    }

    public synchronized PacketQuarantine.Retry retryQuarantine(String id,
                                                               String checksum,
                                                               FTNAddr peer)
            throws IOException, EchoBaseException {
        PacketQuarantine.Snapshot snapshot = quarantine.snapshot(id, checksum);
        FTNAddr expected = snapshot.item.peer.isEmpty() ? peer : new FTNAddr(snapshot.item.peer);
        if (expected == null || config.getLink(expected) == null) {
            throw new IllegalArgumentException("Select the expected configured source link.");
        }
        if (peer != null && !peer.equals(expected)) {
            throw new IllegalArgumentException("The original session peer cannot be changed.");
        }
        quarantine.preserve(snapshot);
        quarantine.attempted(snapshot);
        boolean accepted;
        try {
            accepted = tosspkt(ByteBuffer.wrap(snapshot.bytes), expected);
        } catch (IOException | EchoBaseException | RuntimeException failure) {
            retainRetryFailure(snapshot, failure);
            throw failure;
        }
        if (accepted) {
            quarantine.complete(snapshot);
        } else {
            quarantine.rejected(snapshot, packetRejection);
        }
        logger.info("Quarantine retry completed: id={}, peer={}, accepted={}",
                    id,
                    expected,
                    accepted);
        return new PacketQuarantine.Retry(accepted,
                                          accepted
                                                  ? "Packet processed; original retained in recovery archive."
                                                  : packetRejection);
    }

    public synchronized void archiveQuarantine(String id, String checksum) throws IOException {
        PacketQuarantine.Snapshot snapshot = quarantine.snapshot(id, checksum);
        quarantine.preserve(snapshot);
        quarantine.complete(snapshot);
        logger.info("Packet removed from active quarantine and retained in recovery archive: id={}",
                    id);
    }

    private void retainRetryFailure(PacketQuarantine.Snapshot snapshot, Exception failure) {
        try {
            quarantine.rejected(snapshot, "Packet processing failed; inspect the node log.");
        } catch (IOException retentionFailure) {
            failure.addSuppressed(retentionFailure);
        }
    }

    private boolean rejectPacket(String reason) {
        packetRejection = reason.replaceAll("\\p{Cntrl}", " ");
        if (packetRejection.length() > 512) {
            packetRejection = packetRejection.substring(0, 512);
        }
        return false;
    }

    private void deleteTossedFile(File file) {
        if (config.isDeleteTossedFiles() != 0 && !file.delete()) {
            logger.error("Cannot delete processed inbound mail: {}", file.getAbsolutePath());
        }
    }
    private void saveBad(PktTemp pkt) throws IOException, TosserException {
        saveBad(pkt, null);
    }

    private void saveBad(PktTemp pkt, FTNAddr peer) throws IOException, TosserException {
        Path root = new File(config.getTmpDir()).toPath().toRealPath();
        String name = pkt.getName().replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new TosserException("Absolute packet path is not allowed");
        }
        String badName = name.toLowerCase(Locale.ROOT).endsWith(".pkt")
                ? name.substring(0, name.length() - 4) + ".bad"
                : name + ".bad";
        Path badPath = root.resolve(badName).normalize();
        if (!badPath.startsWith(root) || badPath.equals(root)
                || !badPath.getParent().toRealPath().startsWith(root)) {
            throw new TosserException("Packet path escapes the temporary directory");
        }
        File bad = badPath.toFile();
        if (Files.isSymbolicLink(badPath)) {
            throw new TosserException("Quarantine packet cannot be a symbolic link");
        }
        ByteBuffer copy = pkt.getPkt().duplicate();
        copy.position(0);
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        if (bad.exists()) {
            if (Files.size(badPath) != bytes.length
                    || !Arrays.equals(bytes, Files.readAllBytes(badPath))) {
                throw new IOException("A different rejected packet already exists: " + badPath);
            }
        }
        quarantine.record(badPath, bytes, peer, packetRejection);
        if (bad.exists()) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(badPath,
                                                      StandardOpenOption.CREATE_NEW,
                                                      StandardOpenOption.WRITE)) {
            out.write(bytes);
        }
    }

    private boolean tosspkt(ByteBuffer buf) throws IOException, EchoBaseException {
        return tosspkt(buf, null);
    }

    private boolean tosspkt(ByteBuffer buf, FTNAddr authenticatedPeer)
            throws IOException, EchoBaseException {
        packetRejection = "Packet import was rejected; inspect the node log.";
        FtsPkt packet = readPacket(buf);
        Link link = packetOriginLink(packet, authenticatedPeer);
        if (link == null) {
            return false;
        }
        FTNAddr origin = packet.getOrigZone() == 0 ? link.getAddr() : packet.getOrigaddr();
        if (authenticatedPeer != null && !authenticatedPeer.equals(origin)) {
            logger.warn("Packet origin does not match the receiving session");
            return rejectPacket("Packet origin does not match the receiving session.");
        }
        FTNAddr destination = packetDestination(packet, link);
        if (destination == null) {
            return rejectPacket("Packet destination is not a local AKA.");
        }
        if (!packet.getPass().equals(config.getPacketPassword(link))) {
            logger.error("PKT password mismatch: peer={}, destination={}; packet will be quarantined",
                         origin,
                         destination);
            return rejectPacket("PKT password mismatch. Check the link packet password.");
        }
        return importPacketMessages(packet.getMsgs(), buf, link, origin, destination);
    }

    private static FtsPkt readPacket(ByteBuffer buffer) throws IOException {
        try {
            return new FtsPkt(buffer);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid FTN packet", failure);
        }
    }

    private Link packetOriginLink(FtsPkt packet, FTNAddr authenticatedPeer) {
        Link link = config.getLink(packet.getOrigaddr());
        if (link == null && packet.getOrigZone() == 0) {
            return zoneLessOriginLink(packet.getOrigaddr(), authenticatedPeer);
        }
        if (link == null) {
            logger.error("Unknown packet origin link: {}", packet.getOrigaddr());
            rejectPacket("Unknown packet origin link.");
        }
        return link;
    }

    private Link zoneLessOriginLink(FTNAddr origin, FTNAddr authenticatedPeer) {
        Link resolved = null;
        for (Link candidate : config.getConfiguredLinks()) {
            if (!sameNode(candidate.getAddr(), origin) || authenticatedPeer != null
                    && !authenticatedPeer.equals(candidate.getAddr())) {
                continue;
            }
            if (resolved != null) {
                logger.warn("Ambiguous zone-less packet origin");
                rejectPacket("Ambiguous zone-less packet origin.");
                return null;
            }
            resolved = candidate;
        }
        if (resolved == null) {
            logger.error("Unknown zone-less packet origin link: {}", origin);
            rejectPacket("Unknown zone-less packet origin link.");
        }
        return resolved;
    }

    private FTNAddr packetDestination(FtsPkt packet, Link link) {
        FTNAddr destination = new FTNAddr(packet.getDestZone(),
                                          packet.getDestNet(),
                                          packet.getDestNode(),
                                          packet.getDestPoint());
        Set<FTNAddr> localAddresses = new LinkedHashSet<>(config.getLocalAddresses());
        if (link.getMyaddr() != null) {
            localAddresses.addAll(link.getMyaddr());
        }
        if (destination.getZone() == 0 && !localAddresses.contains(destination)) {
            destination = zoneLessDestination(destination, localAddresses);
        }
        if (destination == null) {
            return null;
        }
        if (!localAddresses.contains(destination)) {
            logger.error("Packet addressed to another station: {}", destination);
            return null;
        }
        return destination;
    }

    private static FTNAddr zoneLessDestination(FTNAddr destination, Set<FTNAddr> localAddresses) {
        FTNAddr resolved = null;
        for (FTNAddr candidate : localAddresses) {
            if (!sameNode(candidate, destination)) {
                continue;
            }
            if (resolved != null) {
                logger.warn("Ambiguous zone-less packet destination");
                return null;
            }
            resolved = candidate;
        }
        return resolved == null ? destination : resolved;
    }

    private boolean importPacketMessages(FtsPackMsg[] messages,
                                         ByteBuffer buffer,
                                         Link link,
                                         FTNAddr origin,
                                         FTNAddr destination)
            throws IOException, EchoBaseException {
        TossJournal journal = new TossJournal(new File(config.getTmpDir()).toPath(), buffer);
        logger.info("Importing packet: origin={}, destination={}, messages={}",
                    origin,
                    destination,
                    messages.length);
        int resumed = 0;
        for (int index = 0; index < messages.length; index++) {
            if (journal.contains(index)) {
                resumed++;
                continue;
            }
            Message message = new Message(messages[index], origin.getZone(), destination.getZone());
            message.setUpLink(origin);
            if (!importPacketMessage(message, link, journal.key(index))) {
                return false;
            }
            journal.complete(index);
        }
        logger.info("Packet processed: origin={}, destination={}, handled={}, alreadyProcessed={}",
                    origin,
                    destination,
                    messages.length - resumed,
                    resumed);
        return true;
    }

    private boolean importPacketMessage(Message message, Link link, String deliveryKey)
            throws IOException, EchoBaseException {
        if (!message.hasValidAddressing()) {
            return rejectPacket("Message has invalid FTN addressing.");
        }
        return message.isEchomail()
                ? importEchoMessage(message, link)
                : importNetmailMessage(message, link, deliveryKey);
    }

    private boolean importEchoMessage(Message message, Link link)
            throws IOException, EchoBaseException {
        String rejection = echoImportFailure(message, link);
        if (rejection != null) {
            logger.warn("Echo import is not permitted: {} from {}",
                        message.getArea(),
                        link.getAddr());
            return rejectPacket(rejection);
        }
        if (areamgr.isDupe(message)) {
            return true;
        }
        getEventBus().publish(new TossEchoMailEvent(message, areamgr));
        if (!message.isValid()) {
            return rejectPacket("Echomail rejected by a processing hook.");
        }
        processEchoMail(message);
        return true;
    }

    private String echoImportFailure(Message message, Link link) {
        if (!areamgr.isEchoExists(message.getArea().toLowerCase(Locale.ROOT))
                && !Boolean.parseBoolean(config.getValue("AutoCreateEcho", "false"))) {
            return "Unknown echo area: " + message.getArea();
        }
        if (Boolean.parseBoolean(config.getValue("EnforceEchoAccess", "true"))
                && !base.canWriteEcho(link, message.getArea())) {
            return "WRITE access denied for echo area: " + message.getArea();
        }
        return null;
    }

    private boolean importNetmailMessage(Message message, Link link, String deliveryKey)
            throws IOException {
        getEventBus().publish(new TossNetmailEvent(message));
        if (netmailStore == null || !message.isValid()) {
            return rejectPacket("Netmail storage is unavailable or a processing hook rejected the message.");
        }
        boolean local = config.getLocalAddresses().contains(message.getTAddr())
                || link.getMyaddr() != null && link.getMyaddr().contains(message.getTAddr());
        NetmailDecision decision = netmailRouter.explainTransit(message, link.getAddr());
        Link route = decision.getNextHop();
        if (!local && (!decision.isPermitted() || (message.getAttributes() & 0x8810) != 0)) {
            logger.warn("Netmail transit refused ({}); quarantining packet", decision.getReason());
            return rejectPacket("Netmail transit refused: " + decision.getReason());
        }
        netmailStore.saveNetmail(message, deliveryKey, local ? null : route.getAddr().toString());
        if (local) {
            dispatchLocalNetmail(message, link, deliveryKey);
        }
        return true;
    }

    private void dispatchLocalNetmail(Message message, Link link, String deliveryKey)
            throws IOException {
        for (NetmailHandler handler : netmailHandlers) {
            if (handler.handle(message.copy(), link, deliveryKey)) {
                return;
            }
        }
    }
    private void processEchoMail(Message msg) throws IOException, EchoBaseException {
        areamgr.addMessage(msg);
    }

    private static boolean sameNode(FTNAddr first, FTNAddr second) {
        return first.getNet().equals(second.getNet()) && first.getNode().equals(second.getNode())
                && first.getPnt().equals(second.getPnt());
    }

    public synchronized void submitNetmail(Message message, String deliveryKey) throws IOException {
        if ((message.getAttributes() & 0x8810) != 0) {
            throw new UnsupportedOperationException("File attaches and file requests require a configured file processor");
        }
        if (message.isEchomail() || netmailStore == null
                || !config.getLocalAddresses().contains(message.getFAddr())) {
            throw new IllegalArgumentException("Outgoing netmail must originate from a local AKA");
        }
        MessageIds.ensure(message, Path.of(config.getTmpDir()));
        if (config.getLocalAddresses().contains(message.getTAddr())) {
            netmailStore.saveNetmail(message, deliveryKey, null);
            return;
        }
        Link route = netmailRouter.route(message);
        if (route == null) {
            throw new IllegalStateException("No route for " + message.getTAddr());
        }
        netmailStore.saveNetmail(message, deliveryKey, route.getAddr().toString());
    }

    public synchronized void exportPendingNetmail() throws IOException {
        if (netmailStore == null) {
            return;
        }
        OutboundSpool spool = new OutboundSpool(config);
        netmailStore.visitQueuedNetmail(queued -> {
            Link route = config.getLink(new FTNAddr(queued.getNextHop()));
            if (route == null || route.getMyaddr() == null || route.getMyaddr().isEmpty()) {
                throw new IOException("Missing configured route " + queued.getNextHop());
            }
            if (spool.isAcknowledged("netmail:" + queued.getKey(), route)) {
                netmailStore.markNetmailSent(queued.getKey());
                return;
            }
            Message copy = queued.getMessage().copy();
            copy.getBody()
                .addVia("\1Via "
                        + route.getMyaddr()
                               .get(0)
                        + " @"
                        + DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
                                           .withZone(ZoneOffset.UTC)
                                           .format(Instant.now())
                        + ".UTC JFtn 1.0");
            spool.enqueue("netmail:" + queued.getKey(), route, copy);
        });
    }

    public void acknowledgeOutbound(String key) {
        if (key.startsWith("netmail:") && netmailStore != null) {
            netmailStore.markNetmailSent(key.substring("netmail:".length()));
        }
    }

    public synchronized void exportEchomail() throws IOException {
        new EchoExporter(config, base, new OutboundSpool(config)).export();
    }

    public synchronized void submitEchomail(Message message) throws IOException {
        if (!message.isEchomail() || !config.getLocalAddresses().contains(message.getFAddr())
                || !base.isAreaExists(message.getArea().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Local echomail requires a local AKA and an existing area");
        }
        MessageIds.ensure(message, Path.of(config.getTmpDir()));
        base.addMessage(message, message.getArea().toLowerCase(Locale.ROOT));
    }

    public List<FTNAddr> submissionTargets(Message message) throws IOException {
        List<FTNAddr> result = new ArrayList<>();
        if (message.isEchomail()) {
            for (Link link : new EchoExporter(config,
                                              base,
                                              new OutboundSpool(config)).recipients(message)) {
                result.add(link.getAddr());
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("No configured recipients for local echomail");
            }
        } else if (!config.getLocalAddresses().contains(message.getTAddr())) {
            Link route = netmailRouter.route(message);
            if (route == null) {
                throw new IllegalStateException("No route for " + message.getTAddr());
            }
            result.add(route.getAddr());
        }
        return result;
    }

    public EchoMgr getAreamgr() {
        return areamgr;
    }

    public NetmailRouter getNetmailRouter() {
        return netmailRouter;
    }

    public EchoRouter getEchoRouter() {
        return echoRouter;
    }

    public synchronized void configureEchoSubscription(FTNAddr peer,
                                                       String area,
                                                       AccessLevel access,
                                                       EchoHistory history) {
        requireManagedEcho();
        echoStore().configureEchoSubscription(echoLink(peer), echoArea(area), access, history);
    }

    public synchronized void removeEchoSubscription(FTNAddr peer, String area) {
        requireManagedEcho();
        echoStore().removeEchoSubscription(echoLink(peer), echoArea(area));
    }

    public synchronized void rescanEcho(String area,
                                        FTNAddr peer,
                                        long firstId,
                                        long lastId,
                                        String requestId)
            throws IOException {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("A stable rescan request ID is required");
        }
        Link link = echoLink(peer);
        String name = echoArea(area);
        if (!base.isAreaExists(name) || !base.canReadEcho(link, name)) {
            throw new IllegalArgumentException("Rescan requires an existing readable subscription");
        }
        EchoExporter exporter = new EchoExporter(config, base, new OutboundSpool(config));
        echoStore().visitEchoRange(name,
                                   firstId,
                                   lastId,
                                   message -> exporter.rescan(message, link, requestId));
    }

    private void requireManagedEcho() {
        if (!EchoRouter.databaseSubscriptions(config)) {
            throw new IllegalStateException("Enable EchoSubscriptions=database to administer subscriptions");
        }
    }

    public synchronized void rescanRecentEcho(String area,
                                              FTNAddr peer,
                                              int count,
                                              String requestId)
            throws IOException {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("A stable rescan request ID is required");
        }
        Link link = echoLink(peer);
        String name = echoArea(area);
        if (!base.isAreaExists(name) || !base.canReadEcho(link, name)) {
            throw new IllegalArgumentException("Rescan requires a readable subscription");
        }
        EchoExporter exporter = new EchoExporter(config, base, new OutboundSpool(config));
        echoStore().visitRecentEcho(name,
                                    count,
                                    message -> exporter.rescan(message, link, requestId));
    }

    private EchoRoutingStore echoStore() {
        if (!(base instanceof EchoRoutingStore)) {
            throw new UnsupportedOperationException("This store does not support managed echo routing");
        }
        return (EchoRoutingStore) base;
    }

    private Link echoLink(FTNAddr peer) {
        Link link = config.getLink(peer);
        if (link == null || config.getLocalAddresses().contains(peer)) {
            throw new IllegalArgumentException("Echo peer must be a configured remote link");
        }
        return link;
    }

    private String echoArea(String area) {
        if (area == null || area.trim().isEmpty()) {
            throw new IllegalArgumentException("Echo area is required");
        }
        return area.trim().toLowerCase(Locale.ROOT);
    }
}
