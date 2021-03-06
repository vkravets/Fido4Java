/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.binkp.common.crypt;

public class StandardEncrypt implements Encrypt {

    private ZipCryptoEngine zipCryptoEngine;
    private char[] password;
    private boolean isClient;

    public StandardEncrypt(char[] password, boolean isClient) {
        this.zipCryptoEngine = new ZipCryptoEngine();
        this.password = password;
        this.isClient = isClient;
        reset();
    }

    public void reset() {
        if (!isClient) {
            zipCryptoEngine.initKeys("-".toCharArray());
            for (char pc : password) {
                zipCryptoEngine.updateKeys((byte) (pc & 0xff));
            }
        } else {
            zipCryptoEngine.initKeys(password);
        }
    }

    public int encryptData(byte[] buff) {
        if (buff == null) {
            throw new NullPointerException();
        }
        return encryptData(buff, buff.length);
    }

    public int encryptData(byte[] buff, int len) {
        for (int i = 0; i < len; i++) {
            buff[i] = encryptByte(buff[i]);
        }
        return len;
    }

    protected byte encryptByte(byte val) {
        byte temp_val = (byte) (val ^ zipCryptoEngine.decryptByte() & 0xff);
        zipCryptoEngine.updateKeys(val);
        return temp_val;
    }
}
