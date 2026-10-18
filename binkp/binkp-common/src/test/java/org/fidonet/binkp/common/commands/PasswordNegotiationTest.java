package org.fidonet.binkp.common.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.RecordingSession;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PasswordNegotiationTest {
    @Test
    public void authenticatedSha1SessionSendsEobAndInsecureGreetingDoesNotClaimPassword()
            throws Exception {
        SessionContext context = context(true);
        context.setServerRole(ServerRole.SERVER);
        context.setState(SessionState.STATE_WAITPWD);
        context.setMessageDigest(MessageDigest.getInstance("SHA1"));
        context.setPasswordKey("01020304");
        Password response = new Password("secret",
                                         true,
                                         MessageDigest.getInstance("SHA1"),
                                         "01020304");
        RecordingSession session = new RecordingSession();
        new PWDCommand().handle(session, context, response.getText());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!context.isSendingIsFinish() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(context.isSendingIsFinish());
        assertFalse(session.closed);
        assertEquals("secure (SHA1)", new OKCommand().getCommandArguments(context));
        assertEquals(2, session.frames.size()); // OK followed by EOB for an empty batch.
        SessionContext noLink = new SessionContext(context.getStationConfig(), new LinksInfo());
        assertEquals("insecure (none)", new OKCommand().getCommandArguments(noLink));
        new OPTCommand().handle(new RecordingSession(), noLink, "OPT CRAM-MD5-01020304");
        assertNull(noLink.getMessageDigest());
    }

    @Test
    public void authenticationRejectsMissingOrMismatchedCramStateAndOutOfSequencePwd()
            throws Exception {
        SessionContext outOfSequence = context(true);
        RecordingSession ignored = new RecordingSession();
        assertThrows(IllegalStateException.class,
                     () -> new PWDCommand().handle(ignored, outOfSequence, "secret"));
        assertTrue(ignored.frames.isEmpty());
        for (int variant = 0; variant < 4; variant++) {
            SessionContext context = context(true);
            context.setServerRole(ServerRole.SERVER);
            context.setState(SessionState.STATE_WAITPWD);
            if (variant > 0) {
                context.setPasswordKey("0102");
            }
            if (variant > 1) {
                context.setMessageDigest(MessageDigest.getInstance("MD5"));
            }
            if (variant == 3) {
                context.getLinksInfo().setLinks(Collections.emptyList());
            }
            // Clear the selected link by using a separate context in the no-link scenario.
            if (variant == 3) {
                context = new SessionContext(context.getStationConfig(), new LinksInfo());
                context.setServerRole(ServerRole.SERVER);
                context.setState(SessionState.STATE_WAITPWD);
            }
            RecordingSession session = new RecordingSession();
            new PWDCommand().handle(session, context, "CRAM-SHA1-wrong");
            assertTrue(session.closed);
            assertEquals(SessionState.STATE_ERR, context.getState());
            assertEquals("Incorrect password", context.getLastErrorMessage());
        }
    }

    private SessionContext context(boolean md) {
        Link link = new Link("2:500/2, 2:500/1, secret, localhost").withMD(md);
        SessionContext context = new SessionContext(new StationConfig("station",
                                                                      "sysop",
                                                                      "city",
                                                                      "BINKP",
                                                                      "2:500/1"),
                                                    new LinksInfo(new ArrayList<>(Collections.singletonList(link))));
        context.getLinksInfo().setCurLink(link.getAddr());
        return context;
    }

    @Test
    public void cramMatchesIndependentJceHmacForLongUtf8PasswordsAndBothAlgorithms()
            throws Exception {
        String password = "пароль".repeat(20);
        byte[] challenge = new byte[]{1, 2, 3, 4};
        for (String algorithm : new String[]{"MD5", "SHA1"}) {
            Mac mac = Mac.getInstance("Hmac" + algorithm);
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8),
                                       "Hmac" + algorithm));
            StringBuilder expected = new StringBuilder("CRAM-" + algorithm + "-");
            for (byte value : mac.doFinal(challenge)) {
                expected.append(String.format("%02x", value));
            }
            Password actual = new Password(password,
                                           true,
                                           MessageDigest.getInstance(algorithm),
                                           "01020304");
            assertEquals(expected.toString(), actual.getText());
            assertEquals(expected.toString(), actual.getText());
        }
        for (String value : new String[]{null, "", "-"}) {
            assertEquals("-", new Password(value).getText());
        }
    }

    @Test
    public void choosesFirstSupportedCramAlgorithmAndIgnoresUnsupportedOrDisabledOffers()
            throws Exception {
        SessionContext context = context(true);
        new OPTCommand().handle(new RecordingSession(),
                                context,
                                "OPT CRAM-unknown/SHA1/MD5-01020304");
        assertEquals("SHA1", context.getMessageDigest().getAlgorithm());
        assertEquals("01020304", context.getPasswordKey());
        assertTrue(context.getPassword().isCrypt());
        for (SessionContext ignored : Arrays.asList(context(false), context(true))) {
            new OPTCommand().handle(new RecordingSession(), ignored, "OPT CRAM-unknown-0102");
            assertNull(ignored.getMessageDigest());
            assertFalse(ignored.getPassword().isCrypt());
        }
    }

    @Test
    public void rejectsMalformedCramChallenges() throws Exception {
        for (String offer : new String[]{"CRAM-MD5", "CRAM-MD5-", "CRAM-MD5-1", "CRAM-MD5-zz"}) {
            SessionContext context = context(true);
            try {
                new OPTCommand().handle(new RecordingSession(), context, "OPT " + offer);
                fail("Accepted " + offer);
            } catch (IllegalArgumentException expected) {
                assertFalse(context.getPassword().isCrypt());
            }
        }
    }

    @Test
    public void cryptNegotiationRespectsLocalCapability() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) {
            SessionContext context = context(false);
            context.getStationConfig().setCryptMode(enabled);
            context.getStationConfig().setNRRequested(true);
            new OPTCommand().handle(new RecordingSession(), context, "OPT CRYPT unknown");
            assertEquals(enabled, context.isCryptMode());
            assertEquals(enabled ? "OPT NR CRYPT" : "OPT NR",
                         new OPTCommand().getCommandArguments(context));
        }
    }
}
