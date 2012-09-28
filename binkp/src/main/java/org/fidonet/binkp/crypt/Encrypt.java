package org.fidonet.binkp.crypt;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/28/12
 * Time: 3:59 PM
 */
public interface Encrypt {
    public int encryptData(byte[] buff);
    public int encryptData(byte[] buff, int bufSize);
}
