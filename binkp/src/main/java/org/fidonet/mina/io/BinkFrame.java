package org.fidonet.mina.io;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import org.fidonet.mina.codec.DataInfo;
import org.fidonet.mina.codec.DataReader;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:22 PM
 */
public class BinkFrame {

    private static final short MAX_DATA_SIZE = 32767;

    private short dataInfo;
    private byte[] data;

    public BinkFrame(short dataInfo, byte[] data) {
        this.dataInfo = dataInfo;
        this.data = data;
    }

    public short getDataInfo() {
        return dataInfo;
    }

    public byte[] getData() {
        return data;
    }

    public static BinkData toBinkData(BinkFrame frame) {
        DataInfo info = DataReader.parseDataInfo(frame.getDataInfo());
        byte cmd = -1;
        byte[] dataBuf = null;
        if (info.isCommand()) {
            DataInputStream dataStream = new DataInputStream(new ByteInputStream(frame.getData(), frame.getData().length));
            try {
                cmd = dataStream.readByte();
                dataBuf = new byte[info.getSize()-1];
                dataStream.read(dataBuf);
            } catch (IOException e) {
                // todo logger
            }
        } else {
            dataBuf = frame.getData();
        }
        return new BinkData(info.isCommand(), cmd, dataBuf);

    }

}
