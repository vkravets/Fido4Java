package org.fidonet.echobase.jam;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JLRFile {
    private RandomAccessFile jlr;

    public JLRFile(File tmp) throws FileNotFoundException {
        jlr = new RandomAccessFile(tmp, "rw");
    }

    void close() throws IOException {
        jlr.close();
    }


}
