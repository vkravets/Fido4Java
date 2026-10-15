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
import org.fidonet.echobase.EchoMgr;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.NetmailStore;
import org.fidonet.echobase.QueuedNetmail;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.events.HasEventBus;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.exception.TosserException;
import org.fidonet.misc.PktTemp;
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
import java.util.Set;
import java.util.regex.Pattern;

public class Tosser extends HasEventBus {

    private static final Logger logger = LoggerFactory.getLogger(Tosser.class.getName());

    private EchoMgr areamgr;
    private Pattern bunlderegex;
    private JFtnConfig config;
    private final NetmailStore netmailStore;
    private final IBase base;

    public Tosser(JFtnConfig config, IBase base) throws IOException {
        this.config = config;
        this.base = base;
        this.netmailStore = base instanceof NetmailStore ? (NetmailStore) base : null;
        this.areamgr = new EchoMgr(base);
        this.bunlderegex = Pattern.compile(".*\\.[STFWMstfwm][ouaherOUAHER][0-9A-Za-z]");
    }

    private boolean isBunldeName(String str) {
        return bunlderegex.matcher(str).find();
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
        // TODO: We should make some checks!
        long startTime = System.currentTimeMillis();
        if (files.length != 0) {
            for (File file : files) {
                boolean packet = file.getName().toLowerCase(Locale.ROOT).endsWith(".pkt");
                if (file.isFile() && (packet || isBunldeName(file.getName()))) {
                    try {
                        LinkedList<PktTemp> pktlist = new LinkedList<>();
                        if (packet) {
                            try (InputStream input = Files.newInputStream(file.toPath())) {
                                byte[] bytes = input.readNBytes(Zipper.MAX_PACKET_BYTES + 1);
                                if (bytes.length > Zipper.MAX_PACKET_BYTES) {
                                    throw new IOException("Packet exceeds size limit");
                                }
                                pktlist.add(new PktTemp(file.getName(), ByteBuffer.wrap(bytes)));
                            }
                        } else {
                            pktlist = Zipper.unpackboundlfast(file.getAbsolutePath());
                        }
                        while (!pktlist.isEmpty()) {
                            PktTemp aPktlist = pktlist.pop();
                            if (!tosspkt(aPktlist.getPkt(), authenticatedPeer)) {
                                logger.info("Save to Tmp");
                                saveBad(aPktlist);
                            }
                        }
                        if (config.isDeleteTossedFiles() != 0) {
                            if (!file.delete()) {
                                logger.error("Error while deleting tossed boundle!");
                            }
                        }
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
            }
        }
        logger.debug("Tossing process takes {}ms", System.currentTimeMillis() - startTime);
    }

    private void saveBad(PktTemp pkt) throws IOException, TosserException {
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

        if (bad.exists()) {
            ByteBuffer copy = pkt.getPkt().duplicate();
            copy.position(0);
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            if (Arrays.equals(bytes, Files.readAllBytes(badPath))) {
                return;
            }
            throw new IOException("A different rejected packet already exists: " + badPath);
        }
        try (OutputStream out = Files.newOutputStream(badPath,
                                                      StandardOpenOption.CREATE_NEW,
                                                      StandardOpenOption.WRITE)) {
            pkt.getPkt().position(0);
            ByteBuffer buffer = pkt.getPkt().duplicate();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            out.write(bytes);
        }
    }

    private boolean tosspkt(ByteBuffer buf) throws IOException, EchoBaseException {
        return tosspkt(buf, null);
    }

    private boolean tosspkt(ByteBuffer buf, FTNAddr authenticatedPeer)
            throws IOException, EchoBaseException {
        final FtsPkt q;
        try {
            q = new FtsPkt(buf);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid FTN packet", e);
        }
        Link origlink = config.getLink(q.getOrigaddr());
        if (origlink == null && q.getOrigZone() == 0) {
            for (Link candidate : config.getConfiguredLinks()) {
                if (sameNode(candidate.getAddr(), q.getOrigaddr()) && (authenticatedPeer == null
                        || authenticatedPeer.equals(candidate.getAddr()))) {
                    if (origlink != null) {
                        logger.warn("Ambiguous zone-less packet origin");
                        return false;
                    }
                    origlink = candidate;
                }
            }
        }
        if (origlink == null) {
            logger.error("Unknown Link! Drop it.");
            return false;
        }
        FTNAddr origin = q.getOrigZone() == 0 ? origlink.getAddr() : q.getOrigaddr();
        if (authenticatedPeer != null && !authenticatedPeer.equals(origin)) {
            logger.warn("Packet origin does not match the receiving session");
            return false;
        }
        FTNAddr destination = new FTNAddr(q.getDestZone(),
                                          q.getDestNet(),
                                          q.getDestNode(),
                                          q.getDestPoint());
        Set<FTNAddr> localAddresses = new LinkedHashSet<>(config.getLocalAddresses());
        if (origlink.getMyaddr() != null) {
            localAddresses.addAll(origlink.getMyaddr());
        }
        if (destination.getZone() == 0 && !localAddresses.contains(destination)) {
            FTNAddr resolved = null;
            for (FTNAddr candidate : localAddresses) {
                if (sameNode(candidate, destination)) {
                    if (resolved != null) {
                        logger.warn("Ambiguous zone-less packet destination");
                        return false;
                    }
                    resolved = candidate;
                }
            }
            if (resolved != null) {
                destination = resolved;
            }
        }
        if (!config.getLocalAddresses().contains(destination)
                && (origlink.getMyaddr() == null || !origlink.getMyaddr().contains(destination))) {
            logger.error("Packet addressed to another station: {}", destination);
            return false;
        }
        String expectedPassword = config.getPacketPassword(origlink);
        if (!q.getPass().equals(expectedPassword)) {
            logger.error("Bad PASSWORD! Drop it.");
            return false;
        }

        final FtsPackMsg[] msgs = q.getMsgs();
        TossJournal journal = new TossJournal(new File(config.getTmpDir()).toPath(), buf);
        logger.debug("Find {} messages in the bundle", msgs.length);
        for (int index = 0; index < msgs.length; index++) {
            if (journal.contains(index)) {
                continue;
            }
            FtsPackMsg msg1 = msgs[index];
            final Message msg = new Message(msg1, origin.getZone(), destination.getZone());
            msg.setUpLink(origin);
            if (!msg.hasValidAddressing()) {
                return false;
            }
            if (msg.isEchomail()) {
                if ((!areamgr.isEchoExists(msg.getArea().toLowerCase(Locale.ROOT))
                        && !Boolean.parseBoolean(config.getValue("AutoCreateEcho", "false")))
                        || (Boolean.parseBoolean(config.getValue("EnforceEchoAccess", "true"))
                                && !base.canWriteEcho(origlink, msg.getArea()))) {
                    logger.warn("Echo import is not permitted: {} from {}",
                                msg.getArea(),
                                origlink.getAddr());
                    return false;
                }
                if (areamgr.isDupe(msg)) {
                    journal.complete(index);
                    continue;
                }
                // Call onTossEchoMsg hook
                getEventBus().publish(new TossEchoMailEvent(msg, areamgr));
                if (msg.isValid()) {
                    processEchoMail(msg);
                } else {
                    return false;
                }
            } else {
                // Call onTossNetMail hook
                getEventBus().publish(new TossNetmailEvent(msg));
                if (netmailStore == null || !msg.isValid()) {
                    return false;
                }
                boolean local = config.getLocalAddresses().contains(msg.getTAddr())
                        || origlink.getMyaddr() != null
                                && origlink.getMyaddr().contains(msg.getTAddr());
                Link route = local ? null : new NetmailRouter(config).route(msg);
                if (!local && (!Boolean.parseBoolean(config.getValue("AllowTransit", "false"))
                        || route == null || route.getAddr().equals(origlink.getAddr())
                        || hasRoutingLoop(msg) || (msg.getAttributes() & 0x8810) != 0)) {
                    logger.warn("Netmail has no permitted transit route; quarantining packet");
                    return false;
                }
                netmailStore.saveNetmail(msg,
                                         journal.key(index),
                                         local ? null : route.getAddr().toString());
            }
            journal.complete(index);
        }
        return true;
    }

    private void processEchoMail(Message msg) throws IOException, EchoBaseException {
        areamgr.addMessage(msg);
    }

    private static boolean sameNode(FTNAddr first, FTNAddr second) {
        return first.getNet().equals(second.getNet()) && first.getNode().equals(second.getNode())
                && first.getPnt().equals(second.getPnt());
    }

    private boolean hasRoutingLoop(Message message) {
        for (String via : message.getBody().getViaLines()) {
            String[] fields = via.trim().split("\\s+");
            if (fields.length > 1 && config.getLocalAddresses().contains(new FTNAddr(fields[1]))) {
                return true;
            }
        }
        return false;
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
        Link route = new NetmailRouter(config).route(message);
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
        for (QueuedNetmail queued : netmailStore.getQueuedNetmail()) {
            Link route = config.getLink(new FTNAddr(queued.getNextHop()));
            if (route == null || route.getMyaddr() == null || route.getMyaddr().isEmpty()) {
                throw new IOException("Missing configured route " + queued.getNextHop());
            }
            if (spool.isAcknowledged("netmail:" + queued.getKey(), route)) {
                netmailStore.markNetmailSent(queued.getKey());
                continue;
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
        }
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
            Link route = new NetmailRouter(config).route(message);
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
}
