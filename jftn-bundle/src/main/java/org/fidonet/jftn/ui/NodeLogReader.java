package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.LogPage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Reads bounded UTF-8 log pages without consuming incomplete lines. */
final class NodeLogReader {
    private NodeLogReader() {
    }

    static LogPage read(Path path, long offset, int limit) throws IOException {
        if (offset < -1 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Invalid log cursor or page size");
        }
        if (Files.notExists(path)) {
            return new LogPage(List.of(), 0, offset > 0);
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            boolean rotated = offset > file.length();
            long position = offset == -1
                    ? Math.max(0, file.length() - 65536)
                    : rotated ? 0 : offset;
            file.seek(position);
            if (offset == -1 && position > 0) {
                while (file.getFilePointer() < file.length() && file.read() != '\n') {
                    // Start the tail window on a complete UTF-8 line.
                }
            }
            long start = file.getFilePointer();
            byte[] bytes = new byte[(int) Math.min(65536, file.length() - start)];
            file.readFully(bytes);
            return decode(bytes, start, limit, rotated, offset == -1);
        } catch (FileNotFoundException failure) {
            if (Files.notExists(path)) {
                return new LogPage(List.of(), 0, offset > 0);
            }
            throw failure;
        }
    }

    private static LogPage decode(byte[] bytes,
                                  long start,
                                  int limit,
                                  boolean rotated,
                                  boolean tail) {
        Deque<String> lines = new ArrayDeque<>();
        int begin = 0;
        int consumed = 0;
        for (int i = 0; i < bytes.length && (tail || lines.size() < limit); i++) {
            if (bytes[i] != '\n') {
                continue;
            }
            lines.add(new String(bytes, begin, i - begin, StandardCharsets.UTF_8).replace("\r",
                                                                                          ""));
            if (lines.size() > limit) {
                lines.removeFirst();
            }
            begin = i + 1;
            consumed = begin;
        }
        if (consumed == 0 && bytes.length == 65536) {
            lines.add("[Oversized log line skipped]");
            consumed = bytes.length;
        }
        return new LogPage(new ArrayList<>(lines), start + consumed, rotated);
    }
}
