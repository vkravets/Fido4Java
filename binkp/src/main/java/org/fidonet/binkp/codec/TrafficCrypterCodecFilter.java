package org.fidonet.binkp.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/14/14
 * Time: 9:56 AM
 */

/**
 * This class control decrypt process for received data,
 * crypt process during sending data controls by last encoder
 */
public class TrafficCrypterCodecFilter extends IoFilterAdapter {

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        IoBuffer in = (IoBuffer) message;
        TrafficCrypter trafficCrypter = (TrafficCrypter) session.getAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY);
        byte[] array = new byte[in.array().length];
        IoBuffer buf = IoBuffer.allocate(array.length);
        in.get(array);
        trafficCrypter.decrypt(array, array.length);
        buf.put(array);
        buf.flip();
        super.messageReceived(nextFilter, session, buf);
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        IoBuffer in = (IoBuffer) writeRequest.getMessage();
        if (in.array().length > 0) {
            TrafficCrypter trafficCrypter = (TrafficCrypter) session.getAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY);
            byte[] array = new byte[in.array().length];
            IoBuffer buf = IoBuffer.allocate(array.length);
            in.get(array);
            trafficCrypter.encrypt(array, array.length);
            buf.put(array);
            buf.flip();
        }
        super.filterWrite(nextFilter, session, writeRequest);
    }
}
