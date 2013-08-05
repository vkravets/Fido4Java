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

package org.fidonet.types;

import java.util.regex.Pattern;

public class FTNAddr {

    private int zone = -1;
    private int net = -1;
    private int node = -1;
    private int pnt = -1;
    private boolean valid = false;

    public FTNAddr(int z, int ne, int no, int p) {
        zone = z;
        net = ne;
        node = no;
        pnt = p;
        valid = true;
    }

    public FTNAddr(String _addr) {
        final String addr;
        if (_addr.contains("@")) {
            Pattern p = Pattern.compile("@");
            addr = p.split(_addr)[0];
        } else {
            addr = _addr;
        }
        final int ze = addr.indexOf(':');
        final int nete = addr.indexOf('/');
        int nodee = addr.indexOf('.');
        final int pnte = addr.length();
        if (ze == -1) {
            //System.out.println("Fuck fts!");
            zone = -1;
            net = -1;
            node = -1;
            pnt = -1;
            valid = false;
            return;
        }
        if (nete == -1) {
            zone = -1;
            net = -1;
            node = -1;
            pnt = -1;
            valid = false;
            return;
        }
        if (nodee == -1) {
            nodee = addr.length();
        }
        zone = Integer.valueOf(addr.substring(0, ze));
        net = Integer.valueOf(addr.substring(ze + 1, nete));
        node = Integer.valueOf(addr.substring(nete + 1, nodee));
        if (nodee < pnte) {
            pnt = Integer.valueOf(addr.substring(nodee + 1, pnte));
        } else {
            pnt = 0;
        }
        valid = true;
    }

    public FTNAddr(FTNAddr addr) {
        this(addr.toString());
    }

    public String toString() {
        return zone + ":" + net + '/' + node + '.' + pnt;
    }

    public String as5D() {
        return toString() + "@fidonet.org";
    }

    public String as4D() {
        return toString();
    }


    public String toHex()
    {
        String hexnet = String.format("%04x",net);
        String hexnode = String.format("%04x",node);
        return hexnet + hexnode;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isEquals(FTNAddr raddr) {
        return (raddr.zone == zone) &&
                (raddr.net == net) &&
                (raddr.node == node) &&
                (raddr.pnt == pnt);
    }
}
