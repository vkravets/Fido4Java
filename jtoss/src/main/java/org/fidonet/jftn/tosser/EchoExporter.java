package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.IBase;
import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Fan-out produces independent copies and never alters the stored original. */
final class EchoExporter {
    private final JFtnConfig config;
    private final IBase base;
    private final OutboundSpool spool;

    EchoExporter(JFtnConfig config, IBase base, OutboundSpool spool) {
        this.config = config;
        this.base = base;
        this.spool = spool;
    }

    void export() throws IOException {
        for (String area : base.getAreas()) {
            Iterator<Message> messages = base.getMessages(area);
            try {
                while (messages.hasNext()) {
                    export(messages.next());
                }
            } finally {
                if (messages instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) messages).close();
                    } catch (Exception e) {
                        throw new IOException("Unable to close message iterator", e);
                    }
                }
            }
        }
    }

    private void export(Message message) throws IOException {
        List<Link> recipients = new ArrayList<>();
        for (Link link : config.getConfiguredLinks()) {
            if (base.canReadEcho(link, message.getArea())
                    && !link.getAddr().equals(message.getUpLink())
                    && (link.getAddr().getPnt() != 0 || !message.getBody()
                                                                .getSeenBy()
                                                                .getAddresses()
                                                                .contains(link.getAddr()))) {
                recipients.add(link);
            }
        }
        for (Link link : recipients) {
            if (link.getMyaddr() == null || link.getMyaddr().isEmpty()) {
                throw new IOException("Missing local echo AKA");
            }
            Message copy = message.copy();
            FTNAddr local = link.getMyaddr().get(0);
            SeenBy seen = new SeenBy();
            FidoPath path = new FidoPath();
            for (FTNAddr address : message.getBody().getSeenBy().getAddresses()) {
                if (address.getZone().equals(link.getAddr().getZone()) && address.getPnt() == 0) {
                    seen.add(address);
                }
            }
            for (FTNAddr address : message.getBody().getPath().getAddresses()) {
                if (address.getZone().equals(local.getZone()) && address.getPnt() == 0) {
                    path.add(address);
                }
            }
            for (Link recipient : recipients) {
                if (recipient.getAddr().getZone().equals(link.getAddr().getZone())
                        && recipient.getAddr().getPnt() == 0) {
                    seen.add(recipient.getAddr());
                }
            }
            if (local.getPnt() == 0) {
                seen.add(local);
                path.add(local);
            }
            copy.getBody().setSeenBy(seen);
            copy.getBody().setPath(path);
            if (copy.getBody().getOrigin() == null && message.getUpLink() == null) {
                copy.getBody()
                    .setOrigin(" * Origin: "
                               + config.getValue("System", "JFtn")
                               + " ("
                               + local
                               + ")");
            }
            spool.enqueue("echo:"
                          + message.getArea()
                          + ":"
                          + MailIdentity.of(message)
                          + ":"
                          + link.getAddr(), link, copy);
        }
    }
}
