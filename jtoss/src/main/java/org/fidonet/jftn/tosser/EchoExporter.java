package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.IncrementalEchoStore;
import org.fidonet.echobase.MessageIteration;
import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;
import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.IOException;
import java.util.ArrayList;
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
            exportArea(area);
        }
    }

    private void exportArea(String area) throws IOException {
        List<Link> readers = readers(area);
        if (!(base instanceof IncrementalEchoStore)) {
            MessageIteration.forEach(base.getMessages(area), message -> export(message, readers));
            return;
        }
        for (Link link : readers) {
            ((IncrementalEchoStore) base).visitNewEcho(area,
                                                       "outbound:" + link.getAddr(),
                                                       message -> exportForLink(message,
                                                                                link,
                                                                                readers));
        }
    }

    private void exportForLink(Message message, Link link, List<Link> readers) throws IOException {
        if (base.isArchived(message)) {
            return;
        }
        List<Link> recipients = recipients(message, readers);
        if (recipients.contains(link)) {
            export(message, link, recipients);
        }
    }

    private void export(Message message, List<Link> readers) throws IOException {
        if (base.isArchived(message)) {
            return;
        }
        List<Link> recipients = recipients(message, readers);
        for (Link link : recipients) {
            export(message, link, recipients);
        }
    }

    private List<Link> readers(String area) {
        List<Link> readers = new ArrayList<>();
        for (Link link : config.getConfiguredLinks()) {
            if (base.canReadEcho(link, area)) {
                readers.add(link);
            }
        }
        return readers;
    }

    List<Link> recipients(Message message) {
        return recipients(message, readers(message.getArea()));
    }

    private List<Link> recipients(Message message, List<Link> readers) {
        List<Link> recipients = new ArrayList<>();
        for (Link link : readers) {
            if (!link.getAddr().equals(message.getUpLink()) && (link.getAddr().getPnt() != 0
                    || !message.getBody().getSeenBy().getAddresses().contains(link.getAddr()))) {
                recipients.add(link);
            }
        }
        return recipients;
    }
    private void export(Message message, Link link, List<Link> recipients) throws IOException {
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
            if (address.getZone().equals(link.getAddr().getZone()) && address.getPnt() == 0) {
                path.add(address);
            }
        }
        for (Link recipient : recipients) {
            if (recipient.getAddr().getZone().equals(link.getAddr().getZone())
                    && recipient.getAddr().getPnt() == 0) {
                seen.add(recipient.getAddr());
            }
        }
        if (local.getPnt() == 0 && local.getZone().equals(link.getAddr().getZone())) {
            seen.add(local);
            path.add(local);
        }
        copy.getBody().setSeenBy(seen);
        copy.getBody().setPath(path);
        if (copy.getBody().getOrigin() == null && message.getUpLink() == null) {
            copy.getBody()
                .setOrigin(" * Origin: " + config.getValue("System", "JFtn") + " (" + local + ")");
        }
        spool.enqueue("echo:"
                      + message.getArea()
                      + ":"
                      + MailIdentity.of(message)
                      + ":"
                      + link.getAddr(), link, copy);
    }
}
