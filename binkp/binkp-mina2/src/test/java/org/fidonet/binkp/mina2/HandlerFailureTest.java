package org.fidonet.binkp.mina2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.io.BinkData;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.mina2.commons.SessionKeys;
import org.fidonet.binkp.mina2.handler.BinkSessionHandler;
import org.fidonet.events.HasEventBus;

import org.apache.mina.core.session.DummySession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class HandlerFailureTest extends HasEventBus {
    @Test
    public void unknownCommandDoesNotBecomeIncomingFileData() throws Exception {
        SessionContext context = new SessionContext(new StationConfig("test",
                                                                      "sysop",
                                                                      "city",
                                                                      "BINKP",
                                                                      "2:500/1"),
                                                    new LinksInfo(new ArrayList<>()));
        context.setServerRole(ServerRole.CLIENT);
        DummySession session = new DummySession();
        session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, context);
        FileInfo info = new FileInfo("receiving.bin", 10, 123);
        context.getRecvFiles().add(new FileData<>(info, new ByteArrayOutputStream()));
        new BinkSessionHandler(getEventBus()).messageReceived(session,
                                                              BinkData.toBinkFrame(new BinkData(true,
                                                                                                (byte) 100,
                                                                                                new byte[]{
                                                                                                        42})));
        assertEquals(0, info.getCurSize());
    }

    private SessionContext context() {
        SessionContext context = new SessionContext(new StationConfig("station",
                                                                      "sysop",
                                                                      "city",
                                                                      "BINKP",
                                                                      "2:500/1"),
                                                    new LinksInfo(new ArrayList<>()));
        context.setServerRole(ServerRole.SERVER);
        return context;
    }

    @Test
    public void malformedCommandReportsErrorAndClosesSession() throws Exception {
        SessionContext context = context();
        DummySession session = new DummySession();
        session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, context);
        BinkSessionHandler handler = new BinkSessionHandler(getEventBus());
        try {
            handler.messageReceived(session, new BinkFrame((short) 0x8002, new byte[]{1}));
            fail("Malformed frame accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(SessionState.STATE_ERR, context.getState());
            assertTrue(context.getLastErrorMessage().contains("length"));
            assertTrue(session.isClosing());
        }
        handler.sessionClosed(session);
        assertEquals(SessionState.STATE_ERR, context.getState());
    }

    @Test
    public void errorsWithoutContextAreSafeAndClosePreservesBusyOrErrorStates() throws Exception {
        BinkSessionHandler handler = new BinkSessionHandler(getEventBus());
        DummySession uninitialized = new DummySession();
        handler.exceptionCaught(uninitialized, new IOException("early failure"));
        handler.sessionClosed(uninitialized);
        for (SessionState state : new SessionState[]{SessionState.STATE_BSY, SessionState.STATE_ERR,
                SessionState.STATE_IDLE}) {
            SessionContext context = context();
            context.setState(state);
            DummySession session = new DummySession();
            session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, context);
            handler.sessionClosed(session);
            assertEquals(state == SessionState.STATE_IDLE ? SessionState.STATE_END : state,
                         context.getState());
        }
    }

    @Test
    public void exceptionCancelsReceiveAndKeepsFailureMessage() throws Exception {
        SessionContext context = context();
        DummySession session = new DummySession();
        session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, context);
        new BinkSessionHandler(getEventBus()).exceptionCaught(session,
                                                              new IOException("read failed"));
        assertEquals(SessionState.STATE_ERR, context.getState());
        assertEquals("read failed", context.getLastErrorMessage());
        assertTrue(session.isClosing());
    }
}
