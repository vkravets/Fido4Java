package org.fidonet.binkp.crypt;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/28/12
 * Time: 3:59 PM
 */
public interface Decrypt {
    public int decryptData(byte[] buff);
    public int decryptData(byte[] buff, int bufSize);
    public void save();
    public void restore();
}
