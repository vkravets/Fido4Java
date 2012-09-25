package org.fidonet.binkp.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.config.Password;
import org.fidonet.binkp.io.BinkFrame;

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
        byte[] dataBuf = msg.getData();
        SessionContext sessionContext = (SessionContext) session.getAttribute(SessionContext.SESSION_CONTEXT_KEY);
        boolean isCrypt = sessionContext.isCryptMode() && sessionContext.getStationConfig().isCryptMode();
        Password password = sessionContext.getPassword();
        boolean isMD5 = sessionContext.getPassword().isCrypt() && password.getMessageDigest().getAlgorithm().equals("MD5");
        if  (isCrypt && isMD5) {
            TrafficCrypter crypter = new TrafficCrypter(password.getPassword());
            dataBuf = crypter.encrypt(dataBuf, dataBuf.length);
        }
        buf.put(dataBuf);
        buf.flip();
        out.write(buf);
    }

}
