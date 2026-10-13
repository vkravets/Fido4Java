package org.fidonet.binkp.mina3;

import org.apache.mina.api.IoSession;
import org.apache.mina.filterchain.WriteFilterChainController;
import org.apache.mina.session.WriteRequest;
import org.fidonet.binkp.mina3.codec.TrafficCrypterCodecFilter;
import org.junit.Test;
import java.nio.ByteBuffer;
import static org.mockito.Mockito.*;

public class EmptyBufferCloseTest {
    @Test public void gracefulCloseMarkerReachesTheTransport() {
        WriteRequest request = mock(WriteRequest.class);
        when(request.getMessage()).thenReturn(ByteBuffer.allocate(0));
        WriteFilterChainController chain = mock(WriteFilterChainController.class);
        new TrafficCrypterCodecFilter().messageWriting(mock(IoSession.class), request, chain);
        verify(chain).callWriteNextFilter(request);
    }
}
