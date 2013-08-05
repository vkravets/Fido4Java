/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.echobase.jam.struct;

import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 18.11.2010
 * Time: 12:48:56
 */
public class MessageHeader {
    // TODO change field name to camel case (first latter is in lower case)
    public final byte[] signature = {'J', 'A', 'M', 0};
    public short revision = 1;
    public short reservedword = 0;
    public int SubfieldLen;
    public int TimesRead;
    public int MSGIDcrc;
    public int REPLYcrc;
    public int ReplyTo;
    public int Reply1st;
    public int Replynext;
    public int DateWritten;
    public int DateReceived;
    public int DateProcessed;
    public int MessageNumber;
    public int Attribute;
    public int Attribute2;
    public int Offset;
    public int TxtLen;
    public int PasswordCRC;
    public int Cost;
    public LinkedList<SubField> SubFieldList;

    public static final int MessageHeaderSize = 19 * 4;

    public MessageHeader() {
        SubFieldList = new LinkedList<SubField>();
    }

    public int getSubLen() {
        int result = 0;
        if (SubFieldList.size() != 0) {
            for (SubField sub : SubFieldList) {
                result += sub.datalen + 8;
            }
            return result;
        } else return 0;

    }

}
