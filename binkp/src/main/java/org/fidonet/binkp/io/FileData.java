package org.fidonet.binkp.io;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 11:55 AM
 */
public class FileData<T> {

    private FileInfo info;
    private T stream;

    public FileData(FileInfo info) {
        this.info = info;
    }

    public FileData(FileInfo info, T stream) {
        this.info = info;
        this.stream = stream;
    }

    public FileInfo getInfo() {
        return info;
    }

    public T getStream() {
        return stream;
    }

    public void setStream(T stream) {
        this.stream = stream;
    }

}
