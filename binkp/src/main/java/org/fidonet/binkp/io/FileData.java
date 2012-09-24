package org.fidonet.binkp.io;

import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 11:55 AM
 */
public class FileData {

    private FileInfo info;
    private InputStream stream;

    public FileData(FileInfo info) {
        this(info, null);
    }

    public FileData(FileInfo info, InputStream stream) {
        this.info = info;
        this.stream = stream;
    }

    public FileInfo getInfo() {
        return info;
    }

    public InputStream getStream() {
        return stream;
    }

    public void setStream(InputStream stream) {
        this.stream = stream;
    }
}
