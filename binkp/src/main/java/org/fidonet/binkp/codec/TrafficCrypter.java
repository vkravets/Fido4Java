package org.fidonet.binkp.codec;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/25/12
 * Time: 11:56 PM
 */
public class TrafficCrypter {

    private String password;

    public TrafficCrypter(String password) {
        this.password = password;
    }

    public byte[] decrypt(byte[] dataBuf, int size) {
        // TODO decoding
        return dataBuf;
    }

    public byte[] encrypt(byte[] dataBuf, int length) {
        // TODO encoding
        return dataBuf;
    }
}
