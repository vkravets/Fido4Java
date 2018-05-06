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

package org.fidonet.types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

public class Link {

    private static final Logger logger = LoggerFactory.getLogger(Link.class.getName());

    private FTNAddr addr;
    private List<FTNAddr> myaddr;
    private String pass;
    private String hostAddress;
    private int port;
    private String boxPath;
    private boolean isMD;

    public Link(FTNAddr addr, List<FTNAddr> myaddrs, String pass, String hostAddress, int port) {
        this.addr = addr;
        this.myaddr = myaddrs;
        this.pass = pass;
        this.hostAddress = hostAddress;
        this.port = port;
    }

    public Link(String linkstr) {

        String[] linkToken = linkstr.split(",");
        if (linkToken.length < 2) {
            throw new IllegalArgumentException("Invalid link configuration");
        }
        addr = new FTNAddr(linkToken[0].trim());
        String addresses = linkToken[1].trim();
        myaddr = convertAddressesToList(addresses);
        if (linkToken.length > 3)
            pass = linkToken[2].trim();
        if (linkToken.length >= 4) {
            String url = linkToken[3].trim();
            if (url.contains(":")) {
                String[] hostToken = url.split(":");
                this.hostAddress = hostToken[0];
                this.port = Integer.valueOf(hostToken[1].trim());
            } else {
                this.hostAddress = url;
                this.port = 0;
            }
        }
    }

    private List<FTNAddr> convertAddressesToList(String addresses) {
        List<FTNAddr> result = new LinkedList<FTNAddr>();
        for (String addr : addresses.split(" ")) {
            FTNAddr ftnAddr = FTNAddr.valueOf(addr);
            if (ftnAddr.isValid()) {
                result.add(ftnAddr);
            } else {
                logger.warn("FTN address \"{}\" cannot be parsed. Skipped!", addr);
            }

        }
        return result;
    }

    public FTNAddr getAddr() {
        return addr;
    }

    public String getPass() {
        return (pass != null && pass.equals("-")) ? "" : pass;
    }

    public List<FTNAddr> getMyaddr() {
        return myaddr;
    }

    @Override
    public String toString() {
        if (myaddr != null && getAddr() != null) {
            return myaddr.listIterator().next() + " -> " + getAddr().toString();
        } else {
            return hostAddress;
        }
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getPort() {
        return port;
    }

    public String getBoxPath() {
        return boxPath;
    }

    public void setBoxPath(String boxPath) {
        this.boxPath = boxPath;
    }

    public boolean isMD() {
        return isMD;
    }

    public void setMD(boolean MD) {
        isMD = MD;
    }

    public Link withMD(Boolean isMD) {
        this.setMD(isMD);
        return this;
    }
}
