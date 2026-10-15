package org.fidonet.echobase.jam;

import org.fidonet.echobase.jam.struct.FixedHeaderInfoStruct;
import org.fidonet.echobase.jam.struct.MessageHeader;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** A JAM area. Every operation reopens and locks the base, allowing external editors. */
public final class JamArea {
    public static final int LOCAL = 1;
    public static final int SENT = 16;
    public static final int TYPE_LOCAL = 0x00800000;
    public static final int ECHO = 0x01000000;
    public static final int NET = 0x02000000;
    public static final int LOCKED = 0x40000000;
    public static final int DELETED = 0x80000000;
    private final Path path;
    private final String area;
    private final FTNAddr aka;
    private final Charset charset;

    public JamArea(Path path, String area, FTNAddr aka) {
        this(path, area, aka, Charset.forName(CharsetTools.DEFAULT_ENCODING));
    }

    public JamArea(Path path, String area, FTNAddr aka, Charset charset) {
        this.path = path.toAbsolutePath().normalize();
        this.area = area;
        this.aka = aka;
        this.charset = charset;
    }

    public Path getPath() {
        return path;
    }

    private Path file(String extension) {
        return Path.of(path + extension);
    }

    /** Never truncates an existing area, including a partially created one. */
    public void create() throws IOException {
        for (String extension : new String[]{".jhr", ".jdt", ".jdx", ".jlr"}) {
            if (Files.exists(file(extension))) {
                throw new IOException("JAM area already exists: " + path);
            }
        }
        Files.createFile(file(".jhr"));
        try (RandomAccessFile header = new RandomAccessFile(file(".jhr").toFile(), "rw");
                FileLock lock = header.getChannel().lock(0, 1, false)) {
            FixedHeaderInfoStruct info = new FixedHeaderInfoStruct();
            info.setBasemsgnum(1);
            info.setDatecreated(JamCodec.timestamp(new Date()));
            info.setModcounter(1);
            info.setPasswordcrc(-1);
            header.write(info.toByteArray());
            for (String extension : new String[]{".jdt", ".jdx", ".jlr"}) {
                Files.createFile(file(extension));
            }
            header.getChannel().force(true);
        }
    }

    public void ensureExists() throws IOException {
        if (!Files.exists(file(".jhr"))) {
            create();
        }
    }

    public <T> T locked(Operation<T> operation) throws IOException {
        if (!Files.isRegularFile(file(".jhr")) || !Files.isRegularFile(file(".jdt"))
                || !Files.isRegularFile(file(".jdx"))) {
            throw new IOException("Incomplete JAM area: " + path);
        }
        try (RandomAccessFile rawHeader = new RandomAccessFile(file(".jhr").toFile(), "rw");
                FileLock lock = acquire(rawHeader);
                JHRFile header = new JHRFile(rawHeader);
                RandomAccessFile text = new RandomAccessFile(file(".jdt").toFile(), "rw");
                RandomAccessFile index = new RandomAccessFile(file(".jdx").toFile(), "rw")) {
            if (index.length() % 8 != 0) {
                throw new IOException("Truncated JAM index");
            }
            return operation.run(new Access(rawHeader, header, text, index));
        }
    }

    private FileLock acquire(RandomAccessFile header) throws IOException {
        try {
            FileLock lock = header.getChannel().tryLock(0, 1, false);
            if (lock == null) {
                throw new IOException("JAM area is busy: " + path);
            }
            return lock;
        } catch (OverlappingFileLockException e) {
            throw new IOException("JAM area is busy: " + path, e);
        }
    }

    public List<Entry> read() throws IOException {
        return locked(Access::read);
    }

    public long append(Message message, int attributes) throws IOException {
        return locked(access -> access.append(message, attributes));
    }

    public long append(Message message) throws IOException {
        return append(message, JamCodec.toJam(message));
    }

    public static int attributesOf(Message message) {
        return JamCodec.toJam(message);
    }

    public interface Operation<T> {
        T run(Access access) throws IOException;
    }

    public final class Access {
        private final RandomAccessFile rawHeader;
        private final JHRFile header;
        private final RandomAccessFile text;
        private final RandomAccessFile index;
        private final FixedHeaderInfoStruct info;

        private Access(RandomAccessFile rawHeader,
                       JHRFile header,
                       RandomAccessFile text,
                       RandomAccessFile index)
                throws IOException {
            this.rawHeader = rawHeader;
            this.header = header;
            this.text = text;
            this.index = index;
            info = header.getFixedHeader();
            if (info.getBasemsgnum() < 1) {
                throw new IOException("Invalid JAM base message number");
            }
        }

        public List<Entry> read() throws IOException {
            List<Entry> result = new ArrayList<>();
            for (long slot = 0; slot < index.length() / 8; slot++) {
                index.seek(slot * 8);
                int user = Integer.reverseBytes(index.readInt());
                long offset = Integer.toUnsignedLong(Integer.reverseBytes(index.readInt()));
                if (user == -1 || offset == 0xffffffffL) {
                    continue;
                }
                MessageHeader stored = header.getMsgHeaderByShift(offset);
                if (stored == null || stored.revision != 1
                        || Integer.toUnsignedLong(stored.MessageNumber) != Integer.toUnsignedLong(info.getBasemsgnum())
                                                                           + slot) {
                    throw new IOException("Invalid JAM message header/index mapping");
                }
                if ((stored.Attribute & DELETED) != 0) {
                    continue;
                }
                long start = Integer.toUnsignedLong(stored.Offset);
                if (stored.TxtLen < 0 || stored.TxtLen > 64 * 1024 * 1024
                        || start + stored.TxtLen > text.length()) {
                    throw new IOException("Invalid JAM text bounds");
                }
                byte[] bytes = new byte[stored.TxtLen];
                text.seek(start);
                text.readFully(bytes);
                result.add(new Entry(Integer.toUnsignedLong(stored.MessageNumber),
                                     stored.Attribute,
                                     JamCodec.decode(stored, bytes, area, charset, aka)));
            }
            return result;
        }

        public long append(Message message, int attributes) throws IOException {
            if (message.isEchomail() != (area != null)) {
                throw new IOException("Message kind does not match JAM area");
            }
            long headerSize = rawHeader.length();
            long textSize = text.length();
            long indexSize = index.length();
            MessageHeader stored = JamCodec.encode(message, attributes);
            byte[] bytes = JamCodec.text(message);
            long number = Integer.toUnsignedLong(info.getBasemsgnum()) + indexSize / 8;
            if (headerSize + 76 + stored.SubfieldLen > 0xffffffffL
                    || textSize + bytes.length > 0xffffffffL || number > 0xffffffffL) {
                throw new IOException("JAM 32-bit storage limit exceeded");
            }
            stored.MessageNumber = (int) number;
            stored.Offset = (int) textSize;
            stored.TxtLen = bytes.length;
            byte[] previous = info.toByteArray();
            try {
                text.seek(textSize);
                text.write(bytes);
                header.writeHeader(stored);
                index.seek(indexSize);
                index.writeInt(Integer.reverseBytes(JamCodec.crc(message.getTo()
                                                                        .getBytes(CharsetTools.charsetDetect(message.getMessageCharset())))));
                index.writeInt(Integer.reverseBytes((int) headerSize));
                info.setActivemsg(info.getActivemsg() + 1);
                header.setFixedHeader(info);
                force();
                return number;
            } catch (IOException e) {
                try {
                    rawHeader.setLength(headerSize);
                    text.setLength(textSize);
                    index.setLength(indexSize);
                    rawHeader.seek(0);
                    rawHeader.write(previous);
                    force();
                } catch (IOException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        }

        public void attributes(long number, int set, int clear) throws IOException {
            long slot = number - Integer.toUnsignedLong(info.getBasemsgnum());
            if (slot < 0 || slot >= index.length() / 8) {
                throw new IOException("JAM message no longer exists");
            }
            index.seek(slot * 8 + 4);
            long offset = Integer.toUnsignedLong(Integer.reverseBytes(index.readInt()));
            MessageHeader stored = header.getMsgHeaderByShift(offset);
            if (stored == null || (stored.Attribute & DELETED) != 0) {
                throw new IOException("JAM message was deleted");
            }
            rawHeader.seek(offset + 52);
            rawHeader.writeInt(Integer.reverseBytes((stored.Attribute | set) & ~clear));
            header.setFixedHeader(info);
            force();
        }

        private void force() throws IOException {
            text.getChannel().force(true);
            rawHeader.getChannel().force(true);
            index.getChannel().force(true);
        }
    }

    public static final class Entry {
        private final long number;
        private final int attributes;
        private final Message message;

        private Entry(long number, int attributes, Message message) {
            this.number = number;
            this.attributes = attributes;
            this.message = message;
        }

        public long getNumber() {
            return number;
        }
        public int getAttributes() {
            return attributes;
        }
        public Message getMessage() {
            return message;
        }
    }
}
