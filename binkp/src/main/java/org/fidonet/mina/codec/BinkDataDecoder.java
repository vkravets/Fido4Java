package org.fidonet.mina.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.fidonet.mina.io.BinkFrame;

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
        short dataInfoRaw = in.getShort();
        DataInfo dataInfo = DataReader.parseDataInfo(dataInfoRaw);
        if (in.remaining() < dataInfo.getSize()) {
            in.position(start);
            return false;
        }
        byte[] dataBuf = new byte[dataInfo.getSize()];
        in.get(dataBuf);
        BinkFrame data = new BinkFrame(dataInfoRaw, dataBuf);
        out.write(data);
        return true;
    }

}
