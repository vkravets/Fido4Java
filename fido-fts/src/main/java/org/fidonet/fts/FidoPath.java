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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 11:59 AM
 */
public class FidoPath extends AbstractFidoAddresses {

    public static final String PATH_LINE_PREFIX = "\001PATH: ";

    public FidoPath() {
        addresses = new LinkedHashSet<FTNAddr>();
    }

    public FidoPath(Set<FTNAddr> addresses) {
        super(addresses);
    }

    public static FidoPath valueOf(String addressesString, int defaultZone) {
        Set<FTNAddr> addresses = new LinkedHashSet<FTNAddr>();
        return new FidoPath(parseAddresses(addressesString, defaultZone, addresses));
    }

    public String toPathString() {
        List<String> wrapLines = getWrapStrings(WRAP_LENGTH - PATH_LINE_PREFIX.length());
        return StringTools.join(wrapLines, PATH_LINE_PREFIX);
    }

    public String[] toPathStrings() {
        // FIXME: possible performance issue
        return toPathString().split("\\n");
    }
}
