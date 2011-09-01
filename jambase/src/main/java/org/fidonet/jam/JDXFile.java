package org.fidonet.jam;

import org.fidonet.misc.MyCRC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JDXFile {

    private RandomAccessFile jdx;

    JDXFile(File tmp) {
        try {
            jdx = new RandomAccessFile(tmp, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    void close() {
        try {
            jdx.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeIndex(String uname, int offset) {
        int CRC = MyCRC.CRC(uname.toLowerCase().getBytes());

        try {
            jdx.seek(jdx.length());
            jdx.writeInt(Integer.reverseBytes(CRC));
            jdx.writeInt(Integer.reverseBytes(offset));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public int getLastMessageShift() {
        int mcrc = 0;
        int mshift = 0;

        try {
            jdx.seek(jdx.length() - 8);
        } catch (IOException e) {
//            e.printStackTrace();
            return 0;
        }
        try {
//            mcrc = (Integer.reverseBytes(jdx.readInt()));
            jdx.readInt();
            mshift = (Integer.reverseBytes(jdx.readInt()));
        } catch (IOException e) {
//            e.printStackTrace();
            return 0;
        }
        return mshift;
    }
}
