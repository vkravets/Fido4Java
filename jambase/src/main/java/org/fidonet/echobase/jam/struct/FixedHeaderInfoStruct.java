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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 18.11.2010
 * Time: 12:48:21
 */
public class FixedHeaderInfoStruct {
    private final byte[] signature = {'J', 'A', 'M', 0};
    private int datecreated;
    private int modcounter;
    private int activemsg;
    private int passwordcrc;
    private int basemsgnum;
    private final byte[] reserved = new byte[1000];

    public byte[] toByteArray() {
        ByteBuffer res = ByteBuffer.allocate(1024);
        res.order(ByteOrder.LITTLE_ENDIAN);
        res.put(signature);
        res.putInt(datecreated);
        res.putInt(modcounter);
        res.putInt(activemsg);
        res.putInt(passwordcrc);
        res.putInt(basemsgnum);
        res.put(reserved);
        return res.array();
    }

    public void fromByteArray(ByteBuffer income) {
        income.position(0);
        income.order(ByteOrder.LITTLE_ENDIAN);
        income.get(signature);
        datecreated = income.getInt();
        modcounter = income.getInt();
        activemsg = income.getInt();
        passwordcrc = income.getInt();
        basemsgnum = income.getInt();
        income.get(reserved);
    }

    public int getDatecreated() {
        return datecreated;
    }

    public void setDatecreated(int datecreated) {
        this.datecreated = datecreated;
    }

    public int getModcounter() {
        return modcounter;
    }

    public void setModcounter(int modcounter) {
        this.modcounter = modcounter;
    }

    public int getActivemsg() {
        return activemsg;
    }

    public void setActivemsg(int activemsg) {
        this.activemsg = activemsg;
    }

    public int getPasswordcrc() {
        return passwordcrc;
    }

    public void setPasswordcrc(int passwordcrc) {
        this.passwordcrc = passwordcrc;
    }

    public int getBasemsgnum() {
        return basemsgnum;
    }

    public void setBasemsgnum(int basemsgnum) {
        this.basemsgnum = basemsgnum;
    }
}
