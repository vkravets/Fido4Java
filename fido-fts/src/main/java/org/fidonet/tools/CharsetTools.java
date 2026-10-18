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

package org.fidonet.tools;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/19/14
 * Time: 3:54 PM
 */
public class CharsetTools {

    public static final String DEFAULT_ENCODING = "CP866";
    private static final Charset CP848 = new UkrainianCharset();
    private static final Map<String, String> ALIASES = Map.of("IBMPC",
                                                              DEFAULT_ENCODING,
                                                              "+7_FIDO",
                                                              DEFAULT_ENCODING,
                                                              "CP10000",
                                                              "x-MacRoman",
                                                              "MAC",
                                                              "x-MacRoman",
                                                              "LATIN-1",
                                                              "ISO-8859-1",
                                                              "LATIN-2",
                                                              "ISO-8859-2",
                                                              "LATIN-5",
                                                              "ISO-8859-9",
                                                              "LATIN-9",
                                                              "ISO-8859-15");

    public static String messageEncoding(String chrs, String codepage) {
        String identifier = chrs == null || chrs.trim().isEmpty()
                ? DEFAULT_ENCODING
                : chrs.trim().split("\\s+")[0];
        if (identifier.equalsIgnoreCase("IBMPC") && codepage != null
                && codepage.trim().matches("[0-9]+")) {
            return "CP" + codepage.trim();
        }
        return identifier;
    }

    public static Charset charsetDetect(String messageCharset) {
        String identifier = messageEncoding(messageCharset, null).toUpperCase(Locale.ROOT);
        if (identifier.equals("CP848") || identifier.equals("IBM848")
                || identifier.equals("IBM-848")) {
            return CP848;
        }
        try {
            return Charset.forName(ALIASES.getOrDefault(identifier, identifier));
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return Charset.forName(DEFAULT_ENCODING);
        }
    }
}
