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

package org.fidonet.binkp.mina3.codec;

import org.apache.mina.session.AttributeKey;
import org.fidonet.binkp.mina3.crypt.Decrypt;
import org.fidonet.binkp.mina3.crypt.DummyDecrypt;
import org.fidonet.binkp.mina3.crypt.DummyEncrypt;
import org.fidonet.binkp.mina3.crypt.Encrypt;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/25/12
 * Time: 11:56 PM
 */
public class TrafficCrypter {

    public static final AttributeKey<TrafficCrypter> TRAFFIC_CRYPTER_KEY = new AttributeKey<TrafficCrypter>(TrafficCrypter.class, TrafficCrypter.class.getName() + ".KEY");

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

