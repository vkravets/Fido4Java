package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** A completed transport file with its original name and authenticated peer directory. */
public final class ReceivedFile {
    private final Path path;
    private final String name;
    private final FTNAddr peer;

    public ReceivedFile(Path path, String name, FTNAddr peer) {
        this.path = path;
        this.name = safeName(name);
        this.peer = new FTNAddr(peer.toString());
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public FTNAddr getPeer() {
        return new FTNAddr(peer.toString());
    }

    public static String safeName(String name) {
        if (!name.matches("[A-Za-z0-9_~-][A-Za-z0-9_.~-]{0,127}")) {
            throw new IllegalArgumentException("Unsupported fileecho filename");
        }
        return name;
    }

    /** Publish the name before the payload; readers ignore metadata without a complete file. */
    public static void record(Path destination, String original) throws IOException {
        safeName(original);
        Path temporary = Files.createTempFile(destination.getParent(), ".received-", ".tmp");
        try {
            Files.writeString(temporary, original);
            Files.move(temporary,
                       metadata(destination),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Path metadata(Path path) {
        return path.resolveSibling(path.getFileName() + ".received");
    }

    public static List<ReceivedFile> list(JFtnConfig config) throws IOException {
        List<ReceivedFile> result = new ArrayList<>();
        for (Link link : config.getConfiguredLinks()) {
            FTNAddr peer = link.getAddr();
            Path directory = Path.of(config.getInbound(), "peers", directoryName(peer));
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    Path metadata = metadata(path);
                    if (Files.isRegularFile(path) && Files.isRegularFile(metadata)
                            && Files.size(metadata) <= 128) {
                        result.add(new ReceivedFile(path, Files.readString(metadata), peer));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    public static String directoryName(FTNAddr peer) {
        return peer.getZone() + "-" + peer.getNet() + "-" + peer.getNode() + "-" + peer.getPnt();
    }
}
