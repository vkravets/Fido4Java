package org.fidonet.binkp.crypt;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/28/12
 * Time: 4:01 PM
 */
public class DummyDecrypt implements Decrypt{

    @Override
    public int decryptData(byte[] buff) {
        return buff.length;
    }

    @Override
    public int decryptData(byte[] buff, int bufSize) {
        return bufSize;
    }

    @Override
    public void save() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void restore() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}
