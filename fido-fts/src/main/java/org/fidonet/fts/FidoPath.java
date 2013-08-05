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
 * Time: 11:59 AM
 */
public class FidoPath {
    
    private Set<FTNAddr> path;

    public FidoPath() {
        this.path = new LinkedHashSet<FTNAddr>();
    }

    public FidoPath(Set<FTNAddr> path) {
        this.path = path;
    }

    public static FidoPath valueOf(String pathString, int defaultZone) {
        String[] tokens = pathString.split("\\s");
        FTNAddr node = new FTNAddr(defaultZone, 0, 0, 0);
        Set<FTNAddr> path = new LinkedHashSet<FTNAddr>();  
        for (String addr : tokens) {
            if (addr.contains("/")) {
                node = new FTNAddr(defaultZone+":"+addr.trim());
            } else {
                node = new FTNAddr(node.getZone(), node.getNet(), Integer.valueOf(addr), 0);
            }
            path.add(node);
        }
        return new FidoPath(path);
    }

    
    @Override
    public String toString() {
        if (path.size() == 0)
            return null;
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<FTNAddr> iterator = path.iterator();
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
    
    private String stripAddr(FTNAddr addr, boolean stripNet) {
        return stripAddr(addr, true, stripNet);
    }
    
    private String stripAddr(FTNAddr addr, boolean stripZone, boolean stripNet) {
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
    
    public void add(FTNAddr addr) {
        path.add(addr);
    }
    
    public void remove(FTNAddr addr) {
        path.remove(addr);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FidoPath path1 = (FidoPath) o;

        return path.equals(path1.path);

    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
