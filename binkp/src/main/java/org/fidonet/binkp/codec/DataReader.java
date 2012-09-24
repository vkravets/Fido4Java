package org.fidonet.binkp.codec;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:58 PM
 */
public class DataReader {

    public static DataInfo parseDataInfo(short dataInfo) {
        int len = dataInfo & 0xffff;
        boolean command = ((len & 0x8000) > 0);
        len &= 0x7fff;
        if (len > 0) {
            return new DataInfo(command, len);
        }
        return null;
    }

    public DataInfo readInfo(IoBuffer buf) {
        short dataInfo = buf.getShort();
        return parseDataInfo(dataInfo);
    }

}
