package org.fidonet.binkp.config;

import java.security.MessageDigest;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 4:36 PM
 */
public class Password {

    private String password;
    private boolean isCrypt;
    private MessageDigest md;
    private String key;


    public Password(String password) {
        this.password = password == null || password.startsWith("-") ? null:password;
        this.isCrypt = false;
        this.md = null;
        this.key = null;
    }

    public boolean isCrypt() {
        return isCrypt;
    }

    public void setCrypt(boolean crypt) {
        isCrypt = crypt;
    }

    public MessageDigest getMessageDigest() {
        return md;
    }

    public void setMd(MessageDigest messageDigest) {
        this.md = messageDigest;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    private String getCryptText() {
        byte[] textKey = new byte[key.length() / 2];
        byte[] text = password.getBytes();
        byte[] k_ipad = new byte[64];
        byte[] k_opad = new byte[64];

        for (int i = 0; i < key.length(); i += 2) {
            String byteString = key.substring(i, i + 2);
            textKey[i / 2] = (byte)Integer.parseInt(byteString, 16);
        }

        for (int i = 0; i < text.length; i++) {
            k_ipad[i] = text[i];
            k_opad[i] = text[i];
        }

        for (int i = 0; i < 64; i++) {
            k_ipad[i] ^= 0x36;
            k_opad[i] ^= 0x5c;
        }
        md.update(k_ipad);
        md.update(textKey);
        byte[] digest = md.digest();
        md.update(k_opad);
        md.update(digest);
        digest = md.digest();
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("CRAM-%s-", md.getAlgorithm()));
        for (int i = 0; i < 16; i++) {
            builder.append(String.format("%02x", digest[i]));
        }
        return builder.toString();
    }

    public String getText() {
        if (password == null) {
            return null;
        }
        if (isCrypt) {
            return getCryptText();
        } else {
            return password;
        }
    }
}
