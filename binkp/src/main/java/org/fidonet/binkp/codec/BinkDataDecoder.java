package org.fidonet.binkp.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
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
public class BinkDataDecoder extends CumulativeProtocolDecoder {

    @Override
    protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        int start = in.position();
        if (in.remaining() < 2) return false;
        short dataInfoRaw = in.getShort();
        DataInfo dataInfo = DataReader.parseDataInfo(dataInfoRaw);
        if (dataInfo == null) {
            return false;
        }
        if (in.remaining() < dataInfo.getSize()) {
            in.position(start);
            return false;
        }
        byte[] dataBuf = new byte[dataInfo.getSize()];
        in.get(dataBuf);

        // Check if crypt mode is turn on
        SessionContext sessionContext = (SessionContext) session.getAttribute(SessionContext.SESSION_CONTEXT_KEY);
        boolean isCrypt = sessionContext.isCryptMode() && sessionContext.getStationConfig().isCryptMode();
        Password password = sessionContext.getPassword();
        boolean isMD5 = sessionContext.getPassword().isCrypt() && password.getMessageDigest().getAlgorithm().equals("MD5");
        if  (isCrypt && isMD5) {
            TrafficCrypter crypter = new TrafficCrypter(password.getPassword());
            dataBuf = crypter.decrypt(dataBuf, dataInfo.getSize());
        }
        BinkFrame data = new BinkFrame(dataInfoRaw, dataBuf);
        out.write(data);
        return true;
    }

}
