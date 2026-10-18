package org.fidonet.binkp.common.io;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.commands.GOTCommand;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.protocol.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ReceiveTransfer {
    private ReceiveTransfer() {
    }

    public static FileData<OutputStream> open(SessionContext context, FileTransferInfo info)
            throws IOException {
        context.requireTransfer();
        Path directory = context.getStationConfig().getPartialDirectory();
        OutputStream stream;
        if (directory == null) {
            stream = new ByteArrayOutputStream();
        } else {
            Files.createDirectories(directory);
            String remote = context.getLinksInfo().getCurLink() == null
                    ? "unknown"
                    : context.getLinksInfo().getCurLink().getAddr().as5D();
            String identity = context.getStationConfig()
                                     .getAddress()
                              + "\0"
                              + remote
                              + "\0"
                              + info.getName()
                              + "\0"
                              + info.getSize()
                              + "\0"
                              + info.getTimestamp();
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                                             .digest(identity.getBytes(StandardCharsets.UTF_8));
                StringBuilder name = new StringBuilder();
                for (byte b : digest) {
                    name.append(String.format("%02x", b));
                }
                PartialFileOutputStream partial = new PartialFileOutputStream(directory.resolve(name
                        + ".part"), info.getSize());
                info.setCurSize(partial.length());
                stream = partial;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return new FileData<>(info, stream);
    }

    public static void accept(Session session, SessionContext context, byte[] bytes)
            throws Exception {
        context.requireTransfer();
        FileData<OutputStream> file = context.getRecvFiles().peek();
        if (file == null || file.getInfo().isFinished() || file.isWaitingForHeader()) {
            return;
        }
        FileTransferInfo info = file.getInfo();
        if (bytes.length > info.getSize() - info.getCurSize()) {
            throw new IOException("Data exceeds advertised file size");
        }
        file.getStream().write(bytes);
        info.setCurSize(info.getCurSize() + bytes.length);
        complete(session, context);
    }

    public static void complete(Session session, SessionContext context) throws Exception {
        context.requireTransfer();
        FileData<OutputStream> file = context.getRecvFiles().peek();
        if (file == null || file.getInfo().isFinished()
                || file.getInfo().getCurSize() != file.getInfo().getSize()) {
            return;
        }
        file.getStream().close();
        context.commitReceivedFile(file);
        file.getInfo().setFinished(true);
        context.receiveEvent(new FileReceivedEvent(context, file));
        new GOTCommand().send(session, context);
        context.getRecvFiles().remove(file);
        context.setRecvFilesSize(context.getRecvFilesSize() + file.getInfo().getSize());
    }
}
