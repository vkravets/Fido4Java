package org.fidonet.echobase.jam;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JDTFile {

    private RandomAccessFile jdt;

    public JDTFile(File tmp) throws FileNotFoundException {
        jdt = new RandomAccessFile(tmp, "rw");
    }


    public int getSize() throws IOException {
        int size = 0;
        size = (int) jdt.length();
        return size;
    }

    public void writeText(byte[] buffer) throws IOException {
        jdt.seek(jdt.length());
        jdt.write(buffer);
    }

    void close() throws IOException {
        jdt.close();
    }
}
