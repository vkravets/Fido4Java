package org.fidonet.binkp.common;

import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileTransferInfo;
import org.fidonet.binkp.common.protocol.StreamSession;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Separate JVM fixture: stdout is exclusively binkp bytes. */
public final class ProcessTestPeer {
    private ProcessTestPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("exit")) {
            System.err.println("Permission denied: test-password");
            System.exit(17);
        }
        if (args[0].equals("idle")) {
            Thread.sleep(60000);
            return;
        }
        // A diagnostic larger than the native pipe buffer must not block the handshake.
        System.err.println("x".repeat(262144));
        SessionContext context = SessionTestFixtures.loopbackContext("1:500/101",
                                                                     "1:500/100",
                                                                     "test-password",
                                                                     0);
        context.setServerRole(ServerRole.SERVER);
        byte[] data = "Remote process payload".getBytes(StandardCharsets.US_ASCII);
        context.getSendFiles()
               .add(new FileData<>(new FileTransferInfo("REMOTE.PKT", data.length, 123),
                                   new ByteArrayInputStream(data)));
        try (StreamSession session = new StreamSession(System.in,
                                                       System.out,
                                                       context,
                                                       "child",
                                                       Duration.ofSeconds(10),
                                                       () -> {
                                                       })) {
            session.start();
            if (!session.awaitClosed(Duration.ofSeconds(15)) || !context.isTransferComplete()) {
                System.exit(4);
            }
        }
    }
}
