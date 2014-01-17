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

package org.fidonet.echobase.jam;

import org.fidonet.misc.MyCRC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class JDXFile {

    private RandomAccessFile jdx;

    public JDXFile(File tmp) throws FileNotFoundException {
        jdx = new RandomAccessFile(tmp, "rw");
    }

    void close() throws IOException {
        jdx.close();
    }

    public void writeIndex(String uname, int offset) throws IOException {
        int CRC = MyCRC.CRC(uname.toLowerCase().getBytes());
        jdx.seek(jdx.length());
        jdx.writeInt(Integer.reverseBytes(CRC));
        jdx.writeInt(Integer.reverseBytes(offset));
    }

    public int getLastMessageShift() throws IOException {
//        int mcrc = 0;
        int mshift = 0;

        long offset = jdx.length() - 8;
        if (offset < 0) {
            return 0;
        }
        jdx.seek(jdx.length() - 8);
//        mcrc = (Integer.reverseBytes(jdx.readInt()));
        jdx.readInt();
        mshift = (Integer.reverseBytes(jdx.readInt()));
        return mshift;
    }
}
