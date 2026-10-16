package org.fidonet.nodelist;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** FTS-5000 A/C/D editor. Operates on original bytes and writes canonical CRLF. */
public final class DirectoryDiff {
    private static final long MAX_BYTES = 128L * 1024 * 1024;

    private DirectoryDiff() {
    }

    /** The caller validates the result's header CRC before publishing it. */
    public static void apply(Path original, Path diff, Path output) throws IOException {
        if (original.equals(output) || diff.equals(output)) {
            throw new IllegalArgumentException("Diff output must be a separate file");
        }
        try (Lines old = new Lines(Files.newInputStream(original), true);
                Lines edits = new Lines(Files.newInputStream(diff), false);
                OutputStream target = new BufferedOutputStream(Files.newOutputStream(output))) {
            byte[] header = old.next();
            if (header == null || !Arrays.equals(header, edits.next())) {
                throw new IOException("Diff does not match the base edition header");
            }
            old.pending = header;
            applyCommands(old, edits, new DiffWriter(target));
            if (old.next() != null) {
                throw new IOException("NODEDIFF leaves unconsumed base lines");
            }
            target.write(26);
        }
    }

    private static void applyCommands(Lines old, Lines edits, DiffWriter target)
            throws IOException {
        byte[] command;
        while ((command = edits.next()) != null) {
            applyCommand(new String(command, StandardCharsets.US_ASCII), old, edits, target);
        }
    }

    private static void applyCommand(String text, Lines old, Lines edits, DiffWriter target)
            throws IOException {
        int count = commandCount(text);
        char operation = text.charAt(0);
        for (int i = 0; i < count; i++) {
            byte[] line = requiredLine(operation == 'A' ? edits : old);
            if (operation == 'D') {
                continue;
            }
            target.write(line);
        }
    }

    private static int commandCount(String text) throws IOException {
        if (!text.matches("[ACD][1-9][0-9]{0,4}")) {
            throw new IOException("Invalid NODEDIFF command");
        }
        int count = Integer.parseInt(text.substring(1));
        if (count > 32767) {
            throw new IOException("NODEDIFF count exceeds 32767");
        }
        return count;
    }

    private static byte[] requiredLine(Lines input) throws IOException {
        byte[] line = input.next();
        if (line == null) {
            throw new IOException("NODEDIFF command exceeds available lines");
        }
        return line;
    }

    private static final class DiffWriter {
        private final OutputStream output;
        private long bytes;

        private DiffWriter(OutputStream output) {
            this.output = output;
        }

        private void write(byte[] line) throws IOException {
            bytes += line.length + 2;
            if (bytes >= MAX_BYTES) {
                throw new IOException("Patched directory exceeds 128 MiB");
            }
            output.write(line);
            output.write('\r');
            output.write('\n');
        }
    }

    private static final class Lines implements AutoCloseable {
        private final PushbackInputStream input;
        private final boolean allowEof;
        private long bytes;
        private byte[] pending;

        private Lines(InputStream input, boolean allowEof) {
            this.input = new PushbackInputStream(new BufferedInputStream(input), 1);
            this.allowEof = allowEof;
        }

        private int read() throws IOException {
            int value = input.read();
            if (value != -1 && ++bytes > MAX_BYTES) {
                throw new IOException("Directory input exceeds 128 MiB");
            }
            return value;
        }

        private byte[] next() throws IOException {
            if (pending != null) {
                byte[] line = pending;
                pending = null;
                return line;
            }
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int value;
            while ((value = read()) != -1) {
                if (value == 26) {
                    validateEof(line);
                    return null;
                }
                if (value == '\r' || value == '\n') {
                    consumeLineEnding(value);
                    return line.toByteArray();
                }
                if (line.size() == 65536) {
                    throw new IOException("Directory line exceeds 64 KiB");
                }
                line.write(value);
            }
            return line.size() == 0 ? null : line.toByteArray();
        }

        private void validateEof(ByteArrayOutputStream line) throws IOException {
            if (!allowEof || line.size() != 0 || read() != -1) {
                throw new IOException("Invalid DOS EOF in directory input");
            }
        }

        private void consumeLineEnding(int value) throws IOException {
            if (value != '\r') {
                return;
            }
            int next = read();
            if (next != '\n' && next != -1) {
                input.unread(next);
                bytes--;
            }
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
