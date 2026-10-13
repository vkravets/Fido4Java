package org.fidonet.binkp.mina3;

import org.apache.mina.api.IoFuture;
import org.apache.mina.api.IoFutureListener;
import org.apache.mina.api.IoSession;
import org.fidonet.binkp.mina3.commons.Mina3Session;
import org.fidonet.binkp.mina3.commons.SessionKeys;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

public class Mina3SessionCloseTest {
    @Test public void gracefulCloseWaitsForTheLastWriteAcrossSessionWrappers() {
        IoSession io = mock(IoSession.class);
        IoFuture<Void> write = mock(IoFuture.class);
        Object frame = new Object();
        when(io.writeWithFuture(frame)).thenReturn(write);
        new Mina3Session(io).write(frame);
        verify(io).setAttribute(SessionKeys.LAST_WRITE_KEY, write);
        when(io.getAttribute(SessionKeys.LAST_WRITE_KEY)).thenReturn(write);

        new Mina3Session(io).close(false);
        verify(io, never()).close(anyBoolean());
        ArgumentCaptor<IoFutureListener<Void>> listener = ArgumentCaptor.forClass(IoFutureListener.class);
        verify(write).register(listener.capture());
        listener.getValue().completed(null);
        verify(io).close(true);
    }
}
