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

package org.fidonet;

import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 7/15/14
 * Time: 10:05 AM
 */
public class Body {

    private Map<String, String> kludges;
    private String origin;
    private SeenBy seenBy;
    private FidoPath path;
    private String body;
    private String area;
    private List<String> controlLines = Collections.emptyList();
    private List<String> viaLines = new ArrayList<>();

    public List<String> getViaLines() {
        return Collections.unmodifiableList(viaLines);
    }

    public void addVia(String line) {
        viaLines.add(line);
    }

    public void setControlLines(List<String> lines) {
        controlLines = new ArrayList<>(lines);
    }

    public void setArea(String area) {
        this.area = area;
    }

    public Body(String body,
                Map<String, String> kludges,
                String origin,
                SeenBy seenBy,
                FidoPath path) {
        this.kludges = kludges;
        this.origin = origin;
        this.seenBy = seenBy;
        this.path = path;
        this.body = body;
    }

    public Map<String, String> getKludges() {
        return kludges;
    }

    public String getOrigin() {
        return origin;
    }

    public SeenBy getSeenBy() {
        return seenBy;
    }

    public FidoPath getPath() {
        return path;
    }

    public String getBody() {
        return body;
    }

    public void setKludges(Map<String, String> kludges) {
        this.kludges = kludges;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public void setSeenBy(SeenBy seenBy) {
        this.seenBy = seenBy;
    }

    public void setPath(FidoPath path) {
        this.path = path;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (area != null && !area.isEmpty()) {
            sb.append("AREA:").append(area).append("\r");
        }
        // insert kludges
        Map<String, String> originalValues = new LinkedHashMap<>();
        Set<String> emitted = new HashSet<>();
        for (String line : controlLines) {
            Matcher match = Pattern.compile("^\\u0001([^ :]+)(?::|(?= ))(.*)$").matcher(line);
            if (!match.matches()) {
                continue;
            }
            String key = match.group(1);
            originalValues.putIfAbsent(key, match.group(2));
            if (!kludges.containsKey(key)) {
                continue;
            }
            if (Objects.equals(kludges.get(key), originalValues.get(key))) {
                sb.append(line).append('\r');
                emitted.add(key);
            } else if (emitted.add(key)) {
                appendControl(sb, key, kludges.get(key));
            }
        }
        for (Map.Entry<String, String> kludge : kludges.entrySet()) {
            if (emitted.add(kludge.getKey())) {
                appendControl(sb, kludge.getKey(), kludge.getValue());
            }
        }
        // insert text
        sb.append(body);
        // insert origin
        if (origin != null) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\r') {
                sb.append('\r');
            }
            sb.append(origin).append("\r");
        }
        // insert seen
        for (String s : seenBy.toSeenByStrings()) {
            sb.append(s).append("\r");
        }
        // insert path
        for (String p : path.toPathStrings()) {
            sb.append(p).append("\r");
        }
        for (String line : viaLines) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\r') {
                sb.append('\r');
            }
            sb.append(line).append('\r');
        }
        return sb.toString();
    }

    private static void appendControl(StringBuilder sb, String key, String value) {
        sb.append('\1').append(key);
        if (key.equals("INTL") || key.equals("FMPT") || key.equals("TOPT")) {
            sb.append(' ').append(value.trim());
        } else {
            sb.append(':');
            if (!value.isEmpty() && !Character.isWhitespace(value.charAt(0))) {
                sb.append(' ');
            }
            sb.append(value);
        }
        sb.append('\r');
    }
}
