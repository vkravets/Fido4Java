package org.fidonet.binkp.codec;

import org.fidonet.binkp.crypt.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/25/12
 * Time: 11:56 PM
 */
public class TrafficCrypter {

    public static final String TRAFFIC_CRYPTER_KEY = TrafficCrypter.class.getName() + ".KEY";

    private Encrypt encrypt;
    private Decrypt decrypt;

    public TrafficCrypter() {
        this.encrypt = new DummyEncrypt();
        this.decrypt = new DummyDecrypt();
    }


    public Encrypt getEncrypt() {
        return encrypt;
    }

    public Decrypt getDecrypt() {
        return decrypt;
    }

    public void setEncrypt(Encrypt encrypt) {
        this.encrypt = encrypt;
    }

    public void setDecrypt(Decrypt decrypt) {
        this.decrypt = decrypt;
    }

    public void encrypt(byte[] dataBuf, int length) {
        encrypt.encryptData(dataBuf, length);
    }

    public void decrypt(byte[] dataBuf, int length) {
        decrypt.decryptData(dataBuf, length);
    }
}

