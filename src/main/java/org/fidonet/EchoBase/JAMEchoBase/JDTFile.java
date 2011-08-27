package org.fidonet.EchoBase.JAMEchoBase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JDTFile {

    private RandomAccessFile jdt;

    JDTFile(File tmp) {
        try {
            jdt = new RandomAccessFile(tmp, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    public int getSize() {
        int size = 0;
        try {
            size = (int) jdt.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return size;
    }

    public void writeText(byte[] buffer) {
        try {
            jdt.seek(jdt.length());
            jdt.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void close() {
        try {
            jdt.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}