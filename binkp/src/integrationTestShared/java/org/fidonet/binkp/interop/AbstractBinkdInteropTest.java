package org.fidonet.binkp.interop;

import org.fidonet.binkp.common.config.ServerRole;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

/** Exchanges real files with an independent binkd process over TCP. */
@RunWith(Parameterized.class)
public abstract class AbstractBinkdInteropTest {
    @Parameterized.Parameters(name = "JFtn role={0}, requests NR={1}")
    public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][]{{ServerRole.CLIENT, false}, {ServerRole.CLIENT, true},
                {ServerRole.SERVER, false}, {ServerRole.SERVER, true}});
    }

    private final ServerRole role;
    private final boolean requestNr;

    protected AbstractBinkdInteropTest(ServerRole role, boolean requestNr) {
        this.role = role;
        this.requestNr = requestNr;
    }

    protected abstract JftnPeer createPeer(ServerRole role, boolean requestNr, ExchangeFiles files);

    @Test
    public void exchangesFilesWithExternalBinkdPeer() throws Exception {
        Path artifacts = artifactDirectory();
        ExchangeFiles files = new ExchangeFiles();
        try (JftnPeer jftn = createPeer(role, requestNr, files);
                BinkdTestPeer binkd = new BinkdTestPeer(files)) {
            try {
                if (role == ServerRole.SERVER) {
                    binkd.startClient(jftn.listen(), artifacts);
                    jftn.awaitConnection();
                } else {
                    binkd.startServer(artifacts);
                    jftn.connect(binkd.host(), binkd.port());
                }

                jftn.assertReceivedFile();
                jftn.assertSessionFinished();
                binkd.assertClientFinished(artifacts);
                binkd.assertTransferredFiles();
                jftn.assertProtocol();
            } finally {
                jftn.saveDiagnostics(artifacts);
                binkd.saveDiagnostics(artifacts);
            }
        }
    }

    private Path artifactDirectory() throws Exception {
        Path path = Paths.get(System.getProperty("binkd.artifactsDir"),
                              role == ServerRole.SERVER ? "jftn-server" : "jftn-client",
                              requestNr ? "nr" : "ordinary");
        return Files.createDirectories(path);
    }
}
