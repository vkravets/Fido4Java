package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.misc.AtomicFiles;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Durable per-service pauses; subscriptions and prepared deliveries remain intact. */
public final class RobotDelivery {
    private final Path root;

    public RobotDelivery(JFtnConfig config) {
        root = Path.of(config.getTmpDir(), "robot-delivery");
    }

    public boolean isPaused(String service, FTNAddr peer) {
        return Files.exists(marker(service, peer));
    }

    public synchronized void pause(String service, FTNAddr peer, boolean paused)
            throws IOException {
        Path marker = marker(service, peer);
        if (!paused) {
            Files.deleteIfExists(marker);
            return;
        }
        Files.createDirectories(marker.getParent());
        if (!Files.exists(marker)) {
            AtomicFiles.publish(marker, output -> output.write(1));
        }
    }

    private Path marker(String service, FTNAddr peer) {
        if (!"AreaFix".equals(service) && !"FileFix".equals(service)) {
            throw new IllegalArgumentException("Unknown mail robot service");
        }
        String address = peer.getZone()
                         + "-"
                         + peer.getNet()
                         + "-"
                         + peer.getNode()
                         + "-"
                         + peer.getPnt();
        return root.resolve(service).resolve(address + ".paused");
    }
}
