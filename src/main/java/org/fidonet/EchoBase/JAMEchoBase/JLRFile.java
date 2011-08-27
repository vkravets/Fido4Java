package org.fidonet.EchoBase.JAMEchoBase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JLRFile {
    private RandomAccessFile jlr;

    JLRFile(File tmp) {
        try {
            jlr = new RandomAccessFile(tmp, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    void close() {
        try {
            jlr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
