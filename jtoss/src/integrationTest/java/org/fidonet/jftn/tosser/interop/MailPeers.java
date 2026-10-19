package org.fidonet.jftn.tosser.interop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns one isolated container network and only the peers created by this test. */
final class MailPeers implements AutoCloseable {
    private final MailPeerCommand commands;
    private final String network = "jftn-mail-" + UUID.randomUUID();
    private final List<MailPeer> peers = new ArrayList<>();
    private final Path artifacts;

    MailPeers(String scenario) throws Exception {
        artifacts = Path.of(System.getProperty("mailInterop.artifactsDir"),
                            scenario,
                            UUID.randomUUID().toString());
        commands = new MailPeerCommand(artifacts);
        commands.execute("network", "create", network);
    }

    MailPeer start(String alias) throws Exception {
        MailPeer peer = new MailPeer(commands, network, alias, artifacts);
        peers.add(peer);
        peer.start();
        return peer;
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        for (MailPeer peer : peers) {
            try {
                peer.close();
            } catch (Exception error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        try {
            commands.execute("network", "rm", network);
        } catch (Exception error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
