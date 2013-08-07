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

package org.fidonet.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 3:18 PM
 */
public class StringTools {

    public static final String NEW_LINE = "\n";

    public static List<String> wrap(String str, int wrapLength) {
        if (str == null) return null;
        if (wrapLength < 1) wrapLength = 1;
        List<String> result = new ArrayList<String>();
        int inputLineLength = str.length();
        int offset = 0;
        while ((inputLineLength - offset) > wrapLength) {
            if (str.charAt(offset) == ' ') {
                offset++;
                continue;
            }
            int spaceToWrapAt = str.lastIndexOf(' ', wrapLength + offset);
            if (spaceToWrapAt >= offset) {
                result.add(str.substring(offset, spaceToWrapAt));
                offset = spaceToWrapAt + 1;
            } else {
                spaceToWrapAt = str.indexOf(' ', wrapLength + offset);
                if (spaceToWrapAt >= 0) {
                    result.add(str.substring(offset, spaceToWrapAt));
                    offset = spaceToWrapAt + 1;
                } else {
                    result.add(str.substring(offset));
                    offset = inputLineLength;
                }
            }
        }
        result.add(str.substring(offset));
        return result;
    }

    public static String join(Collection<String> strings, String sep, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            sb.append(prefix).append(string).append(sep);
        }
        return sb.toString();
    }

    public static String join(Collection<String> strings) {
        return join(strings, NEW_LINE, "");
    }

    public static String join(Collection<String> strings, String prefix) {
        return join(strings, NEW_LINE, prefix);
    }

}
