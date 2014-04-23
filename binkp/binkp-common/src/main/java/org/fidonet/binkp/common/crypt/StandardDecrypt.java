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

package org.fidonet.binkp.common.crypt;

public class StandardDecrypt implements Decrypt {

    private ZipCryptoEngine zipCryptoEngine;
    private char[] password;
    private boolean isClient;

    public StandardDecrypt(char[] password, boolean isClient) {
        this.zipCryptoEngine = new ZipCryptoEngine();
        this.password = password;
        this.isClient = isClient;
        reset();
    }

    public int decryptData(byte[] buff) {
        return decryptData(buff, buff.length);
    }

    public int decryptData(byte[] buff, int len) {
        for (int i = 0; i < len; i++) {
            int val = buff[i] & 0xff;
            val = (val ^ zipCryptoEngine.decryptByte()) & 0xff;
            zipCryptoEngine.updateKeys((byte) val);
            buff[i] = (byte) val;
        }
        return len;
    }

    @Override
    public void save() {
        zipCryptoEngine.save();
    }

    @Override
    public void restore() {
        zipCryptoEngine.restore();
    }

    public void reset() {
        if (isClient) {
            zipCryptoEngine.initKeys("-".toCharArray());
            for (char pc : password) {
                zipCryptoEngine.updateKeys((byte) (pc & 0xff));
            }
        } else {
            zipCryptoEngine.initKeys(password);
        }

    }

}