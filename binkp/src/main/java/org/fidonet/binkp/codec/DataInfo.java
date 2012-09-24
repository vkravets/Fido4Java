package org.fidonet.binkp.codec;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 7:24 PM
 */
public class DataInfo {
    private boolean isCommand;
    private int size;

    public DataInfo(boolean isCommand, int size) {
        this.isCommand = isCommand;
        this.size = size;
    }

    public boolean isCommand() {
        return isCommand;
    }

    public int getSize() {
        return size;
    }
}
