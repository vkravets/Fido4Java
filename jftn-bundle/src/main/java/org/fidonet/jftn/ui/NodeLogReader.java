package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.LogPage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads bounded UTF-8 log pages without consuming incomplete lines. */
final class NodeLogReader {
    private NodeLogReader() {
    }

    static LogPage read(Path path, long offset, int limit) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            boolean rotated = offset > file.length();
            file.seek(rotated ? 0 : offset);
            long start = file.getFilePointer();
            byte[] bytes = new byte[(int) Math.min(65536, file.length() - start)];
            file.readFully(bytes);
            return decode(bytes, start, limit, rotated);
        }
    }

    private static LogPage decode(byte[] bytes, long start, int limit, boolean rotated) {
        List<String> lines = new ArrayList<>();
        int begin = 0;
        int consumed = 0;
        for (int i = 0; i < bytes.length && lines.size() < limit; i++) {
            if (bytes[i] != '\n') {
                continue;
            }
            lines.add(new String(bytes, begin, i - begin, StandardCharsets.UTF_8).replace("\r",
                                                                                          ""));
            begin = i + 1;
            consumed = begin;
        }
        if (consumed == 0 && bytes.length == 65536) {
            lines.add("[Oversized log line skipped]");
            consumed = bytes.length;
        }
        return new LogPage(lines, start + consumed, rotated);
    }
}
