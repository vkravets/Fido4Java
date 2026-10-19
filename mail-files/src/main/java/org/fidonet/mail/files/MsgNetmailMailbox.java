package org.fidonet.mail.files;

import org.fidonet.packet.MailIdentity;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Numbered FTS-0001 MSG files. Editor-owned headers and lastread files are preserved. */
public final class MsgNetmailMailbox {
    public static final int LOCAL = 0x0100;
    public static final int SENT = 0x0008;
    private final Path path;
    private final Charset charset;
    private final FTNAddr aka;

    public MsgNetmailMailbox(Path path, Charset charset, FTNAddr aka) {
        this.path = path.toAbsolutePath().normalize();
        this.charset = charset;
        this.aka = aka;
    }

    public Path getPath() {
        return path;
    }

    public void ensureExists() throws IOException {
        Files.createDirectories(path);
    }

    public void visit(Handler handler) throws IOException {
        for (Path file : files()) {
            withEntry(file, false, handler);
        }
    }

    public void scan(Handler handler) throws IOException {
        for (Path file : files()) {
            if (Files.isWritable(file)) {
                withEntry(file, true, handler);
            }
        }
    }

    public List<Message> read() throws IOException {
        List<Message> result = new ArrayList<>();
        visit(entry -> result.add(entry.getMessage()));
        return result;
    }

    void appendIfAbsent(Message message) throws IOException {
        // Date spelling/implicit charset can be normalized by an external format.
        String identity = MailIdentity.of(MsgCodec.decode(MsgCodec.encode(message,
                                                                          charset,
                                                                          message.getAttributes()),
                                                          charset,
                                                          aka));
        boolean[] found = {false};
        visit(entry -> found[0] |= MailIdentity.of(entry.getMessage()).equals(identity));
        if (!found[0]) {
            int attributes = message.getAttributes();
            attributes = message.getUpLink() == null
                    ? attributes | LOCAL | SENT
                    : attributes & ~LOCAL;
            append(message, attributes);
        }
    }

    public Path append(Message message, int attributes) throws IOException {
        ensureExists();
        byte[] bytes = MsgCodec.encode(message, charset, attributes);
        List<Path> existing = files();
        long number = existing.isEmpty() ? 1 : number(existing.get(existing.size() - 1)) + 1;
        Path temporary = Files.createTempFile(path, "jftn-msg-", ".tmp");
        try {
            writeTemporary(temporary, bytes);
            return publish(temporary, number);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeTemporary(Path temporary, byte[] bytes) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) {
            file.write(bytes);
            file.getChannel().force(true);
        }
    }

    private Path publish(Path temporary, long first) throws IOException {
        for (long number = first; number <= Integer.MAX_VALUE; number++) {
            Path target = path.resolve(number + ".msg");
            try {
                // A hard link publishes complete bytes and NEVER replaces an editor's file.
                Files.createLink(target, temporary);
                return target;
            } catch (FileAlreadyExistsException collision) {
                // An external editor allocated this number after our directory scan.
            }
        }
        throw new IOException("Stored MSG message numbers exhausted: " + path);
    }

    private List<Path> files() throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IOException("Missing MSG mailbox: " + path);
        }
        List<Path> result;
        try (var files = Files.list(path)) {
            result = files.filter(file -> file.getFileName()
                                              .toString()
                                              .toLowerCase(Locale.ROOT)
                                              .matches("[0-9]+\\.msg"))
                          .collect(Collectors.toList());
        }
        Set<Long> numbers = new HashSet<>();
        for (Path file : result) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !numbers.add(number(file))) {
                throw new IOException("Ambiguous or unsafe MSG filename: " + file);
            }
        }
        result.sort(Comparator.comparingLong(MsgNetmailMailbox::number));
        return result;
    }

    private static long number(Path file) {
        String name = file.getFileName().toString();
        try {
            long result = Long.parseLong(name.substring(0, name.length() - 4));
            if (result > 0 && result <= Integer.MAX_VALUE) {
                return result;
            }
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid MSG number: " + file, invalid);
        }
        throw new IllegalArgumentException("Invalid MSG number: " + file);
    }

    private void withEntry(Path path, boolean writable, Handler handler) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), writable ? "rw" : "r");
                FileLock lock = acquire(file, !writable)) {
            if (writable && !pending(file)) {
                return;
            }
            file.seek(0);
            handler.accept(new Entry(file, writable));
        }
    }

    private boolean pending(RandomAccessFile file) throws IOException {
        if (file.length() <= MsgCodec.HEADER_SIZE || file.length() > MsgCodec.MAX_SIZE) {
            throw new IOException("Truncated or oversized stored MSG");
        }
        file.seek(MsgCodec.ATTR_OFFSET);
        int attributes = file.readUnsignedByte() | file.readUnsignedByte() << 8;
        return (attributes & (LOCAL | SENT)) == LOCAL;
    }

    private FileLock acquire(RandomAccessFile file, boolean shared) throws IOException {
        try {
            FileLock lock = file.getChannel().tryLock(0, Long.MAX_VALUE, shared);
            if (lock == null) {
                throw new IOException("MSG is busy: " + path);
            }
            return lock;
        } catch (OverlappingFileLockException busy) {
            throw new IOException("MSG is busy: " + path, busy);
        }
    }

    public interface Handler {
        void accept(Entry entry) throws IOException;
    }

    public final class Entry {
        private final RandomAccessFile file;
        private final boolean writable;
        private Message message;

        private Entry(RandomAccessFile file, boolean writable) throws IOException {
            this.file = file;
            this.writable = writable;
            if (file.length() > MsgCodec.MAX_SIZE) {
                throw new IOException("Stored MSG exceeds size limit");
            }
            byte[] bytes = new byte[(int) file.length()];
            file.readFully(bytes);
            message = MsgCodec.decode(bytes, charset, aka);
        }

        public Message getMessage() {
            return message;
        }

        public void assignMessageId(String id) throws IOException {
            requireWritable();
            if (id == null || id.indexOf('\r') >= 0 || id.indexOf('\n') >= 0
                    || id.indexOf('\0') >= 0
                    || !StandardCharsets.US_ASCII.newEncoder().canEncode(id)) {
                throw new IOException("Invalid stored MSGID");
            }
            file.seek(file.length() - 2);
            String separator = file.length() == MsgCodec.HEADER_SIZE + 1 || file.read() == '\r'
                    ? ""
                    : "\r";
            byte[] suffix = (separator + "\1MSGID: " + id
                    + "\r\0").getBytes(StandardCharsets.US_ASCII);
            if (file.length() + suffix.length - 1 > MsgCodec.MAX_SIZE) {
                throw new IOException("Stored MSG exceeds size limit");
            }
            file.seek(file.length() - 1);
            file.write(suffix);
            file.getChannel().force(true);
            file.seek(0);
            byte[] bytes = new byte[(int) file.length()];
            file.readFully(bytes);
            message = MsgCodec.decode(bytes, charset, aka);
        }

        public void markSent() throws IOException {
            requireWritable();
            int attributes = message.getAttributes() | SENT;
            file.seek(MsgCodec.ATTR_OFFSET);
            file.write(attributes & 255);
            file.write(attributes >>> 8);
            file.getChannel().force(true);
            message.setAttributes(attributes);
        }

        private void requireWritable() throws IOException {
            if (!writable) {
                throw new IOException("Read-only MSG operation");
            }
        }
    }
}
