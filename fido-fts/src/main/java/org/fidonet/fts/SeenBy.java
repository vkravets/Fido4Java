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

import org.fidonet.tools.StringTools;
import org.fidonet.types.FTNAddr;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 11:58 AM
 */
public class SeenBy extends AbstractFidoAddresses {
    
    private static final String SEEN_BY_LINE_PREFIX = "SEEN-BY: ";

    public SeenBy() {
        addresses = new TreeSet<FTNAddr>(new Comparator<FTNAddr>() {
            @Override
            public int compare(FTNAddr o1, FTNAddr o2) {
                return o2.compareTo(o1);
            }
        });
    }

    public SeenBy(Set<FTNAddr> addresses) {
        if (addresses instanceof TreeSet) {
            this.addresses = addresses;
        } else {
            this.addresses = new TreeSet<FTNAddr>(addresses); 
        }
    }

    public static SeenBy valueOf(String addressesString, int defaultZone) {
        Set<FTNAddr> path = new TreeSet<FTNAddr>(new Comparator<FTNAddr>() {
            @Override
            public int compare(FTNAddr o1, FTNAddr o2) {
                return o2.compareTo(o1);
            }
        });
        return new SeenBy(parseAddresses(addressesString, defaultZone, path));
    }

    public String toSeenByString() {
        List<String> wrapLines = getWrapStrings(WRAP_LENGTH-SEEN_BY_LINE_PREFIX.length());
        return StringTools.join(wrapLines, SEEN_BY_LINE_PREFIX);
    }

    public String[] toSeenByStrings() {
        // FIXME: possible performance issue
        return toSeenByString().split("\\n");
    }
}