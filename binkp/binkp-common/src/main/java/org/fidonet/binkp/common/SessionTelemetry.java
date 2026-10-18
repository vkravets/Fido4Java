package org.fidonet.binkp.common;

import org.fidonet.binkp.common.codec.BinkpLog;
import org.fidonet.binkp.common.events.ConnectedEvent;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.events.FileEvent;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.events.FileSendEvent;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileTransferInfo;
import org.fidonet.events.Event;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/** Read-only session diagnostics. Observer failures never change protocol behavior. */
final class SessionTelemetry {
    private static final Logger LOG = LoggerFactory.getLogger(SessionContext.class);
    private final SessionContext context;
    private volatile Consumer<String> protocolListener;
    private volatile SessionActivityListener activityListener;

    SessionTelemetry(SessionContext context) {
        this.context = context;
    }

    void setProtocolListener(Consumer<String> listener) {
        protocolListener = listener;
    }

    void setActivityListener(SessionActivityListener listener) {
        activityListener = listener;
    }

    void recordProtocolLog(String line) {
        Consumer<String> listener = protocolListener;
        if (listener == null)
            return;
        try {
            listener.accept(line);
        } catch (RuntimeException ignored) {
            // Diagnostics must not interrupt the protocol.
        }
    }

    void activity(SessionActivityListener.Kind kind, String name, long bytes) {
        SessionActivityListener listener = activityListener;
        if (listener == null)
            return;
        try {
            listener.record(kind, name, bytes);
        } catch (RuntimeException ignored) {
            // Diagnostics must not change acknowledgement behavior.
        }
    }

    private void info(String line) {
        LOG.info(line);
        recordProtocolLog(line);
    }

    private Object peer() {
        Link link = context.getLinksInfo() == null ? null : context.getLinksInfo().getCurLink();
        return link == null ? "unidentified" : link.getAddr();
    }

    void event(Event event) {
        if (event instanceof ConnectedEvent) {
            info(String.format("binkp session ready: peer=%s, role=%s, NR=%s",
                               peer(),
                               context.getServerRole(),
                               context.isNRMode()));
        } else if (event instanceof DisconnectedEvent) {
            info(String.format("binkp transfer ended: peer=%s, role=%s, state=%s",
                               peer(),
                               context.getServerRole(),
                               context.getState()));
        } else if (event instanceof FileReceivedEvent) {
            file("received", (FileEvent<?>) event);
        } else if (event instanceof FileSendEvent) {
            file("sent and acknowledged", (FileEvent<?>) event);
        }
    }

    private void file(String action, FileEvent<?> event) {
        FileTransferInfo info = event.getFile().getInfo();
        long elapsed = event.getFile().getTransferMillis();
        long transferred = event.getFile().getTransferredBytes();
        long cps = (long) (transferred * 1000.0 / Math.max(1, elapsed));
        info(String.format("binkp file %s: peer=%s, name=%s, bytes=%s, transferred=%s, elapsedMs=%s, CPS=%s",
                           action,
                           peer(),
                           BinkpLog.text(info.getName()),
                           info.getSize(),
                           transferred,
                           elapsed,
                           cps));
    }

    void startFileTransfer(String action, FileData<?> file, long offset) {
        if (file.startTransfer(offset)) {
            info(String.format("binkp %s: peer=%s, name=%s, bytes=%s, offset=%s",
                               action,
                               peer(),
                               BinkpLog.text(file.getInfo().getName()),
                               file.getInfo().getSize(),
                               offset));
        }
    }

    void fileDataWritten(FileData<?> file, long offset) {
        info(String.format("binkp file data written: name=%s, bytes=%s, offset=%s",
                           BinkpLog.text(file.getInfo().getName()),
                           file.getInfo().getCurSize() - offset,
                           offset));
    }
}
