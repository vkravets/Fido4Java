package org.fidonet.binkp.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.fidonet.binkp.io.BinkFrame;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:20 PM
 */
public class BinkDataEncoder<T extends BinkFrame> extends ProtocolEncoderAdapter {
    @Override
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        BinkFrame msg = (T) message;
        IoBuffer buf = IoBuffer.allocate(msg.getData().length+2);
        buf.setAutoExpand(false);
        ByteBuffer bufCrypt = ByteBuffer.allocate(msg.getData().length+2);
        bufCrypt.putShort(msg.getDataInfo());
        bufCrypt.put(msg.getData());
        bufCrypt.flip();
        byte[] bufCryptRaw = bufCrypt.array();
        TrafficCrypter trafficCrypter = (TrafficCrypter) session.getAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY);
        trafficCrypter.encrypt(bufCryptRaw, bufCryptRaw.length);
        buf.put(bufCryptRaw);
        buf.flip();
        out.write(buf);
    }

}
