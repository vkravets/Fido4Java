package org.fidonet.binkp.crypt;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/28/12
 * Time: 4:01 PM
 */
public class DummyEncrypt implements Encrypt{
    @Override
    public int encryptData(byte[] buff) {
        return buff.length;
    }

    @Override
    public int encryptData(byte[] buff, int bufSize) {
        return bufSize;
    }
}
