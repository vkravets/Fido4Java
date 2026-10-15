package org.fidonet.jftn.tosser.jam;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.CRC32;

/** Durable receipt records. An incomplete final record is discarded before the next append. */
final class JamState {
    enum Stage {
        PREPARED, ACCEPTED, DELIVERED
    }
    private static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;
    private final Path log;
    private final Properties values = new Properties();
    private long validLength;
    private final RecordWriter writer;

    JamState(Path path) throws IOException {
        this(path, JamState::writeRecord);
    }

    JamState(Path path, RecordWriter writer) throws IOException {
        this.writer = writer;
        log = Path.of(path + ".log");
        loadLegacy(path);
        if (Files.exists(log)) {
            loadRecords();
        }
    }

    private void loadLegacy(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
    }

    private void loadRecords() throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(log.toFile(), "r")) {
            while (readRecord(input)) {
                validLength = input.getFilePointer();
            }
        }
    }

    private boolean readRecord(RandomAccessFile input) throws IOException {
        if (input.length() - input.getFilePointer() < 4) {
            return false;
        }
        int length = input.readInt();
        if (length < 0 || length > MAX_RECORD_BYTES) {
            throw new IOException("Invalid JAM receipt record length");
        }
        if (input.length() - input.getFilePointer() < (long) length + 4) {
            return false;
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        if (input.readInt() != checksum(bytes)) {
            throw new IOException("Corrupt JAM receipt record");
        }
        Properties record = new Properties();
        record.load(new ByteArrayInputStream(bytes));
        values.putAll(record);
        return true;
    }

    String get(String key) {
        return values.getProperty(key);
    }

    Stage stage(String identity) throws IOException {
        String stored = get("stage." + identity);
        try {
            return stored == null ? null : Stage.valueOf(stored);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid JAM submission stage: " + stored, failure);
        }
    }

    void stage(String identity, Stage stage) throws IOException {
        put("stage." + identity, stage.name());
    }

    void put(String key, String value) throws IOException {
        if (value.equals(values.getProperty(key))) {
            return;
        }
        Properties changes = new Properties();
        changes.setProperty(key, value);
        append(changes);
    }

    void prepare(String identity, String fingerprint, String targets) throws IOException {
        Properties changes = new Properties();
        changes.setProperty("import." + identity, fingerprint);
        changes.setProperty("targets." + identity, targets);
        changes.setProperty("stage." + identity, Stage.PREPARED.name());
        append(changes);
    }

    private void append(Properties changes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        changes.store(output, null);
        byte[] bytes = output.toByteArray();
        if (bytes.length > MAX_RECORD_BYTES) {
            throw new IOException("JAM receipt record exceeds size limit");
        }
        Files.createDirectories(log.getParent());
        validLength = writer.write(log, validLength, bytes);
        values.putAll(changes);
    }

    interface RecordWriter {
        long write(Path log, long validLength, byte[] bytes) throws IOException;
    }

    private static long writeRecord(Path log, long validLength, byte[] bytes) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "rw")) {
            file.setLength(validLength);
            file.seek(validLength);
            file.writeInt(bytes.length);
            file.write(bytes);
            file.writeInt(checksum(bytes));
            file.getChannel().force(true);
            return file.getFilePointer();
        }
    }

    private static int checksum(byte[] bytes) {
        CRC32 checksum = new CRC32();
        checksum.update(bytes);
        return (int) checksum.getValue();
    }
}
