package org.fidonet.jftn.poll;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.config.JFtnConfig;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

class PollDiagnosticsTest {
    private PollDiagnostics diagnostics() throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(("SysOp=Test\nLink=1:500/101,1:500/100,linkSecret\n"
                + "Inbound=in\nOutbound=out\nTmp=tmp\nEchoPath=echo\n").getBytes(StandardCharsets.UTF_8)));
        SessionContext context = new SessionContext(new StationConfig("Test",
                                                                      "Sysop",
                                                                      "",
                                                                      "",
                                                                      "1:500/100"),
                                                    new LinksInfo(new ArrayList<>(config.getConfiguredLinks())));
        context.setPasswordKey("traffic-key");
        return new PollDiagnostics(config, context);
    }

    @Test
    void removedKnownCredentialsAndAuthenticationTokensWithoutRemovingTheReason() throws Exception {
        String digest = "0123456789abcdef0123456789abcdef";
        String text = diagnostics().summary(new IOException("Connection refused linkSecret traffic-key "
                + "CRAM-MD5-" + digest
                + " password=anotherSecret authorization: Bearer bearerValue\nFORGED"));
        assertTrue(text.contains("Connection refused"));
        for (String secret : new String[]{"linkSecret", "traffic-key", digest, "anotherSecret",
                "bearerValue", "\n"}) {
            assertFalse(text.contains(secret), secret);
        }
    }

    @Test
    void boundedCyclicCauseAndSuppressedExceptionGraphs() throws Exception {
        IOException first = new IOException("first");
        IOException second = new IOException("second");
        first.initCause(second);
        second.initCause(first);
        first.addSuppressed(new IllegalStateException("suppressed"));
        PollDiagnostics diagnostics = diagnostics();
        assertTrue(diagnostics.trace(first).size() <= 120);
        assertTrue(String.join("\n", diagnostics.trace(first)).contains("Suppressed:"));
        assertTrue(diagnostics.summary(first).contains("second"));
    }
}
