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

package org.fidonet.echobase;

import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.util.List;

public class EchoMgr {

    private final IBase echosbase;
    private EchoList echoList;
    private String echoPath;

    public EchoMgr(IBase base, EchoList echoList, String echoPath) {
        this.echosbase = base;
        this.echoPath = echoPath;
        this.echoList = echoList;
    }

    public void addMessage(Message msg, FTNAddr myAddr) throws IOException, EchoBaseException {
        if (!echoList.isInList(msg.getArea().toLowerCase())) {
            echoList.addArea(msg.getArea().toLowerCase(), echoPath, msg.getUpLink(), myAddr);
            echosbase.createArea(msg.getArea().toLowerCase());
        }
        echosbase.open();
        echosbase.addMessage(msg, msg.getArea().toLowerCase());
    }

    public void getMessage(String area, int id) throws EchoBaseException {
        echosbase.open();
        echosbase.getMessage(area, id);
    }

    public void getMessage(int id) throws EchoBaseException {
        echosbase.open();
        echosbase.getMessage(id);
    }

    private void createArea(String name) throws EchoBaseException {
        echosbase.createArea(name);
    }

    public List<String> getEchos() {
        return echoList.getEchoList();
    }

    public boolean isEchoExists(String name) {
        return echoList.isInList(name);
    }

    public void close() {
        echosbase.close();
    }
}
