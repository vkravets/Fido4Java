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

package org.fidonet.fts;

import org.fidonet.types.FTNAddr;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 11:50 AM
 */

public abstract class AbstractFidoAddresses {

    protected Set<FTNAddr> addresses;
    
    public AbstractFidoAddresses() { }

    public AbstractFidoAddresses(Set<FTNAddr> path) {
        this.addresses = path;
    }

    protected static <T extends Set<FTNAddr>> T parseAddresses(String addressesString, int defaultZone, T addresses) {
        FTNAddr node = new FTNAddr(defaultZone, 0, 0, 0);
        String[] tokens = addressesString.split("\\s");
        for (String addr : tokens) {
            if (addr.contains("/")) {
                node = new FTNAddr(defaultZone + ":" + addr.trim());
            } else {
                node = new FTNAddr(node.getZone(), node.getNet(), Integer.valueOf(addr), 0);
            }
            addresses.add(node);
        }
        return addresses;
    }

    public void add(FTNAddr addr) {
        addresses.add(addr);
    }
    
    public void addAll(Collection<? extends FTNAddr> addresses) {
        this.addresses.addAll(addresses);
    }

    public void remove(FTNAddr addr) {
        addresses.remove(addr);
    }

    protected String stripAddr(FTNAddr addr, boolean stripNet) {
        return stripAddr(addr, true, stripNet);
    }

    protected String stripAddr(FTNAddr addr, boolean stripZone, boolean stripNet) {
        String format = "";
        List<Integer> params = new LinkedList<Integer>();
        if (!stripZone) {
            format += "%d:";
            params.add(addr.getZone());
        }
        if (!stripNet) {
            format += "%d/";
            params.add(addr.getNet());
        }
        params.add(addr.getNode());
        format += "%d";
        return String.format(format, params.toArray());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AbstractFidoAddresses that = (AbstractFidoAddresses) o;
        return addresses.equals(that.addresses);

    }

    @Override
    public int hashCode() {
        return addresses.hashCode();
    }

    @Override
    public String toString() {
        if (addresses.size() == 0)
            return null;
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<FTNAddr> iterator = addresses.iterator();
        FTNAddr first = iterator.next();
        stringBuilder.append(stripAddr(first, false));
        //noinspection WhileLoopReplaceableByForEach
        while (iterator.hasNext()) {
            FTNAddr addr = iterator.next();
            stringBuilder.append(" ");
            boolean netEquals = addr.getNet().equals(first.getNet());
            stringBuilder.append(stripAddr(addr, netEquals));
            if (!netEquals) {
                first = addr;
            }
        }
        return stringBuilder.toString();
    }
    
}