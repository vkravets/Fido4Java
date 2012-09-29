/*
* Copyright 2010 Srikanth Reddy Lingala
* Copyright 2012 Vladimir Kravets
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.fidonet.binkp.crypt;

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
            buff[i] = (byte)val;
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
                zipCryptoEngine.updateKeys((byte)(pc & 0xff));
            }
        } else {
            zipCryptoEngine.initKeys(password);
        }

    }

}