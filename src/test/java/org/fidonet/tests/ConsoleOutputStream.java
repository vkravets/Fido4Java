package org.fidonet.tests;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 5:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConsoleOutputStream extends OutputStream {

    private String buffer = "";

    @Override
    public void write(int b) throws IOException {
        buffer += String.valueOf((char )b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer += new String(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public String getBuffer() {
        return buffer;
    }
}