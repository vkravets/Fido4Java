package org.fidonet.binkp;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 02.08.11
 * Time: 15:27
 */
public class SessFile {

    public final String filename;
    public byte[] body = null;
    final int length;
    int pos;
    final int time;

    SessFile(String n, int size, int unixtime) {
        filename = n;
        body = new byte[size];
        length = size;
        time = unixtime;
        pos = 0;
    }

    void append(byte[] data) {
        System.arraycopy(data, 0, body, pos, data.length);
        pos += data.length;
    }
}
