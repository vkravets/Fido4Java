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

package org.fidonet;

import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;

import java.util.Map;

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

    public Body(String body, Map<String, String> kludges, String origin, SeenBy seenBy, FidoPath path) {
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
        // insert kludges
        for (Map.Entry<String, String> kludge : kludges.entrySet()) {
            sb.append(kludge.getKey()).append(":").append(kludge.getValue()).append("\r");
        }
        // insert text
        sb.append(body);
        // insert origin
        sb.append(origin).append("\r");
        // insert seen
        for (String s : seenBy.toSeenByStrings()) {
            sb.append(s).append("\r");
        }
        // insert path
        for (String p : path.toPathStrings()) {
            sb.append(p).append("\r");
        }
        return sb.toString();
    }
}
