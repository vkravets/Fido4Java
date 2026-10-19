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
import java.util.Collections;
import java.util.List;

/** Fan-out produces independent copies and never alters the stored original. */
public final class EchoExporter {
    private final JFtnConfig config;
    private final IBase base;
    private final OutboundSpool spool;
    private final EchoRouter router;

    EchoExporter(JFtnConfig config, IBase base, OutboundSpool spool) {
        this.config = config;
        this.base = base;
        this.spool = spool;
        router = new EchoRouter(config, base);
    }

    void export() throws IOException {
        for (String area : base.getAreas()) {
            exportArea(area);
        }
    }

    /** File stores freeze fan-out in the durable spool before accepting the incoming message. */
    public static void prepare(JFtnConfig config, IBase base, Message message) throws IOException {
        EchoExporter exporter = new EchoExporter(config, base, new OutboundSpool(config));
        exporter.export(message, exporter.readers(message.getArea()));
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
        return router.recipients(message, readers);
    }
    private void export(Message message, Link link, List<Link> recipients) throws IOException {
        export(message, link, recipients, "echo:");
    }

    void rescan(Message message, Link link, String requestId) throws IOException {
        if (!base.isArchived(message)) {
            export(message,
                   link,
                   Collections.singletonList(link),
                   "rescan:" + requestId + ":echo:");
        }
    }

    private void export(Message message, Link link, List<Link> recipients, String prefix)
            throws IOException {
        Message copy = message.copy();
        FTNAddr local = router.localAddress(message.getArea(), link);
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
        spool.enqueue(prefix
                      + message.getArea()
                      + ":"
                      + MailIdentity.of(message)
                      + ":"
                      + link.getAddr(), link, copy, local);
    }
}
