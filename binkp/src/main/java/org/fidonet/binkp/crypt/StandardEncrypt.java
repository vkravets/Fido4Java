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
        for (int i = 0; i <  len; i++) {
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