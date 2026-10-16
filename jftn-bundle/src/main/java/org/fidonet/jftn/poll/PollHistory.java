package org.fidonet.jftn.poll;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Small durable metadata records, independent of UI accounts and message storage. */
final class PollHistory implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    PollHistory(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        channel = FileChannel.open(directory.resolve(".lock"),
                                   StandardOpenOption.CREATE,
                                   StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("Poll history is already in use");
            }
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    List<PollAttempt> load() throws IOException {
        List<PollAttempt> records = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName()
                                                      .toString()
                                                      .endsWith(".properties"))
                                  .collect(Collectors.toList())) {
                records.add(read(path));
            }
        }
        records.sort(Comparator.comparingLong(PollAttempt::getCreated)
                               .thenComparing(PollAttempt::getId));
        return records;
    }

    private PollAttempt read(Path path) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        try {
            String id = values.getProperty("id");
            if (!path.getFileName().toString().equals(UUID.fromString(id) + ".properties")
                    || !"1".equals(values.getProperty("version"))) {
                throw new IllegalArgumentException();
            }
            return new PollAttempt(id,
                                   values.getProperty("address"),
                                   values.getProperty("state"),
                                   Long.parseLong(values.getProperty("created")),
                                   values.getProperty("detail", ""))
                                                                    .source(values.getProperty("source"))
                                                                    .started(Long.parseLong(values.getProperty("started")))
                                                                    .finished(Long.parseLong(values.getProperty("finished")));
        } catch (RuntimeException failure) {
            throw new IOException("Invalid poll history record", failure);
        }
    }

    void save(PollAttempt attempt) {
        Properties values = new Properties();
        values.setProperty("version", "1");
        values.setProperty("id", attempt.getId());
        values.setProperty("address", attempt.getAddress());
        values.setProperty("state", attempt.getState());
        values.setProperty("detail", attempt.getDetail());
        values.setProperty("source", attempt.getSource());
        values.setProperty("created", Long.toString(attempt.getCreated()));
        values.setProperty("started", Long.toString(attempt.getStarted()));
        values.setProperty("finished", Long.toString(attempt.getFinished()));
        try {
            publish(attempt.getId(), values);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot persist poll history", failure);
        }
    }

    private void publish(String id, Properties values) throws IOException {
        Path temporary = Files.createTempFile(directory, ".attempt-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "JFtn poll attempt");
            }
            Path target = directory.resolve(id + ".properties");
            try {
                Files.move(temporary,
                           target,
                           StandardCopyOption.ATOMIC_MOVE,
                           StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void delete(String id) {
        try {
            Files.deleteIfExists(directory.resolve(id + ".properties"));
            Files.deleteIfExists(directory.resolve(id + ".log"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public void close() throws IOException {
        try (channel) {
            lock.release();
        }
    }
}
