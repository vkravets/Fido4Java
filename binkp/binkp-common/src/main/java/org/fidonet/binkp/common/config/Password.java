/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.binkp.common.config;

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


    public Password(String password, Boolean isCrypt, MessageDigest md, String key) {
        this(password);
        this.isCrypt = isCrypt;
        this.md = md;
        this.key = key;
    }


    public Password(String password) {
        this.password = password == null || password.startsWith("-") ? null : password;
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
            textKey[i / 2] = (byte) Integer.parseInt(byteString, 16);
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

    // return password text according to crypt setting
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

    // return password original password, w/o crypt even it was set
    public String getPassword() {
        return password;
    }
}
