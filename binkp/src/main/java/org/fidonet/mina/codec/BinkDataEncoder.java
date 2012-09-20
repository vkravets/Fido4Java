package org.fidonet.mina.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.fidonet.mina.io.BinkFrame;

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
        buf.putShort(msg.getDataInfo());
        buf.put(msg.getData());
        buf.flip();
        out.write(buf);
    }

}
