package org.fidonet.binkp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.binkp.common.commands.ADRCommand;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.OKCommand;
import org.fidonet.binkp.common.commands.PWDCommand;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.commands.share.CommandFactory;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.ReceiveTransfer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class AuthenticationStateTest {
    @Test
    void rejectsTransferCommandsAndDataBeforePasswordInBothRoles() {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = waiting(role);
            RecordingSession session = new RecordingSession();
            AtomicInteger received = new AtomicInteger();
            context.setReceivedFileHandler((connection, file) -> received.incrementAndGet());
            for (BinkCommand command : new BinkCommand[]{BinkCommand.M_FILE, BinkCommand.M_GET,
                    BinkCommand.M_GOT, BinkCommand.M_SKIP, BinkCommand.M_EOB}) {
                assertThrows(IllegalStateException.class,
                             () -> new CommandFactory().createCommand(context, command, ""));
            }
            assertThrows(IllegalStateException.class,
                         () -> new FILECommand().handle(session, context, "TEST.PKT 1 1 0"));
            assertThrows(IllegalStateException.class,
                         () -> ReceiveTransfer.accept(session, context, new byte[]{42}));
            assertEquals(0, received.get());
            assertTrue(context.getRecvFiles().isEmpty());
            assertTrue(session.frames.isEmpty());
        }
    }

    @Test
    void forgedOkCannotStartServerOutboundOrAuthenticateAnUnpreparedClient() {
        for (ServerRole role : ServerRole.values()) {
            SessionContext context = waiting(role);
            if (role == ServerRole.CLIENT) {
                context.setState(SessionState.STATE_WAITPWD);
            }
            AtomicInteger loaded = new AtomicInteger();
            context.setOutboundLoader(connection -> loaded.incrementAndGet());
            RecordingSession session = new RecordingSession();
            assertThrows(IllegalStateException.class,
                         () -> new OKCommand().handle(session, context, "secure"));
            assertEquals(0, loaded.get());
            assertTrue(session.frames.isEmpty());
        }
    }

    @Test
    void rejectsRepeatedAuthenticationAndAddressChangesAfterAuthentication() {
        SessionContext context = waiting(ServerRole.SERVER);
        context.beginTransfer();
        context.setState(SessionState.STATE_TRANSFER);
        RecordingSession session = new RecordingSession();
        assertThrows(IllegalStateException.class,
                     () -> new PWDCommand().handle(session, context, "secret"));
        assertThrows(IllegalStateException.class,
                     () -> new ADRCommand().handle(session, context, "1:500/999"));
        context.setState(SessionState.STATE_ERR);
        assertThrows(IllegalStateException.class,
                     () -> ReceiveTransfer.accept(session, context, new byte[]{42}));
    }

    private SessionContext waiting(ServerRole role) {
        SessionContext context = SessionTestFixtures.context();
        context.setServerRole(role);
        context.setState(role == ServerRole.SERVER
                ? SessionState.STATE_WAITPWD
                : SessionState.STATE_WAITOK);
        return context;
    }
}
