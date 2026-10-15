package org.fidonet.jftn.tosser;

import org.fidonet.types.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Persistent, monotonically increasing 32-bit serials for locally created messages. */
public final class MessageIds {
    private MessageIds() {
    }

    public static void ensure(Message message, Path directory) throws IOException {
        if (message.getMsgId() != null) {
            return;
        }
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("msgid.sequence"),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.READ,
                                                    StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            long previous = 0;
            if (channel.size() != 0) {
                if (channel.size() != 8) {
                    throw new IOException("Corrupt MSGID sequence");
                }
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        throw new IOException("Truncated MSGID sequence");
                    }
                }
                buffer.flip();
                previous = buffer.getLong();
            }
            long serial = Math.max(previous + 1, System.currentTimeMillis() / 1000);
            if (serial > 0xffffffffL) {
                throw new IOException("MSGID serial space exhausted");
            }
            buffer.clear();
            buffer.putLong(serial).flip();
            channel.position(0);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
            message.setMsgId(message.getFAddr() + " " + String.format(Locale.ROOT, "%08x", serial));
        }
    }
}
