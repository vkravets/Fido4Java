package org.fidonet.jftn.poll;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Netty callbacks enqueue bounded safe events; only the poll worker writes the journal. */
final class PollJournal {
    private final Path path;
    private final int maximumBytes;
    private final ArrayBlockingQueue<String> pending = new ArrayBlockingQueue<>(256);
    private final AtomicInteger dropped = new AtomicInteger();
    private boolean sealed;

    PollJournal(Path path, int maximumBytes) {
        this.path = path;
        this.maximumBytes = maximumBytes;
    }

    void record(String kind, String detail, long bytes) {
        String safe = detail.replaceAll("[\\p{Cntrl}]", " ");
        safe = safe.substring(0, Math.min(safe.length(), 2048));
        String line = Instant.now()
                      + " "
                      + kind
                      + " "
                      + safe
                      + (bytes > 0 ? " (" + bytes + " bytes)" : "")
                      + "\n";
        if (!pending.offer(line)) {
            dropped.incrementAndGet();
        }
    }

    synchronized void flush() {
        List<String> events = new ArrayList<>();
        pending.drainTo(events);
        if (sealed) {
            return;
        }
        int omitted = dropped.getAndSet(0);
        if (omitted > 0) {
            events.add(Instant.now() + " [" + omitted + " events omitted: buffer full]\n");
        }
        if (events.isEmpty()) {
            return;
        }
        try {
            append(events);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot write poll journal", failure);
        }
    }

    private void append(List<String> events) throws IOException {
        long size = Files.exists(path) ? Files.size(path) : 0;
        byte[] marker = "[Poll journal limit reached; further events omitted]\n".getBytes(StandardCharsets.UTF_8);
        for (String event : events) {
            byte[] bytes = event.getBytes(StandardCharsets.UTF_8);
            if (size + bytes.length + marker.length > maximumBytes) {
                if (size + marker.length <= maximumBytes) {
                    Files.write(path, marker, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                sealed = true;
                return;
            }
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            size += bytes.length;
        }
    }

    PollLogSlice read(long offset, int limit) throws IOException {
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Invalid journal cursor or page size");
        }
        if (!Files.exists(path)) {
            return new PollLogSlice(List.of(), 0, false);
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (offset > file.length()) {
                throw new IllegalArgumentException("Journal cursor exceeds file size");
            }
            file.seek(offset);
            byte[] bytes = new byte[(int) Math.min(65536, file.length() - offset)];
            file.readFully(bytes);
            return decode(bytes, offset, limit, file.length());
        }
    }

    private PollLogSlice decode(byte[] bytes, long offset, int limit, long length) {
        List<String> lines = new ArrayList<>();
        int begin = 0;
        for (int index = 0; index < bytes.length && lines.size() < limit; index++) {
            if (bytes[index] == '\n') {
                lines.add(new String(bytes, begin, index - begin, StandardCharsets.UTF_8));
                begin = index + 1;
            }
        }
        return new PollLogSlice(lines, offset + begin, offset + begin < length);
    }
}
