package org.fidonet.echobase.jam;

import org.fidonet.misc.MyCRC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JDXFile {

    private RandomAccessFile jdx;

    public JDXFile(File tmp) throws FileNotFoundException {
        jdx = new RandomAccessFile(tmp, "rw");
    }

    void close() throws IOException {
        jdx.close();
    }

    public void writeIndex(String uname, int offset) throws IOException {
        int CRC = MyCRC.CRC(uname.toLowerCase().getBytes());
        jdx.seek(jdx.length());
        jdx.writeInt(Integer.reverseBytes(CRC));
        jdx.writeInt(Integer.reverseBytes(offset));
    }

    public int getLastMessageShift() throws IOException {
//        int mcrc = 0;
        int mshift = 0;

        long offset = jdx.length() - 8;
        if (offset < 0) {
            return 0;
        }
        jdx.seek(jdx.length() - 8);
//        mcrc = (Integer.reverseBytes(jdx.readInt()));
        jdx.readInt();
        mshift = (Integer.reverseBytes(jdx.readInt()));
        return mshift;
    }
}
