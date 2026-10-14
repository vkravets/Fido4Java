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

import org.fidonet.Body;
import org.fidonet.fts.FidoPath;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.SeenBy;
import org.fidonet.tools.CharsetTools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Message {

    private static final Logger logger = LoggerFactory.getLogger(Message.class.getName());

    private String from;
    private String to;
    private String subject;
    private String area;
    private FTNAddr fAddr;
    private FTNAddr tAddr;
    private boolean echomail;
    private static final Pattern collon = Pattern.compile(":");
    private static final Pattern collonorwhitespace = Pattern.compile("[:][:\\s]");
    private Body body;
    private String msgDate;
    private Attribute attrs;
    private FTNAddr upLink;
    private String msgId;
    private boolean isValid;

    public Message(String from,
                   String to,
                   FTNAddr fromAddr,
                   FTNAddr toAddr,
                   String subj,
                   String message,
                   Date date) {
        this.from = from;
        this.to = to;
        this.fAddr = fromAddr;
        this.tAddr = toAddr;
        this.subject = subj;
        this.msgDate = new SimpleDateFormat("dd MMM yy  HH:mm:ss", Locale.US).format(date);
        this.isValid = true;

        stripAndUpdateKludges(message);

        tryDetectAddressesAndId();
    }

    public Message(FtsPackMsg src) {
        this(src, 2, 2);
    }

    public Message(FtsPackMsg src, int originZone, int destinationZone) {
        from = src.getFrom();
        to = src.getTo();
        subject = src.getSubj();
        attrs = new Attribute(src.getAttr());
        byte[] ttTime = src.getDateTime();
        msgDate = new String(ttTime, StandardCharsets.US_ASCII).replace("\0", "").trim();
        int net = src.getOrigNet();
        int node = src.getOrigNode();
        upLink = new FTNAddr(originZone, net, node, 0);
        fAddr = new FTNAddr(originZone, net, node, 0);
        tAddr = new FTNAddr(destinationZone, src.getDestNet(), src.getDestNode(), 0);

        //        String messageCharsetName = getMessageCharset();
        //        Charset charset = CharsetTools.charsetDetect(messageCharsetName);
        //        CharBuffer decode = charset.decode(ByteBuffer.wrap(src.getBody()));
        //        body = new String(decode.array());
        stripAndUpdateKludges(new String(src.getBody(), FtsPackMsg.detectCharset(src.getBody())));
        tryDetectAddressesAndId();
        isValid = true;
    }

    public void stripAndUpdateKludges(String str) {
        body = getStripedBody(str);
    }

    public Body getStripedBody(String body) {
        return getStripedBody(body.replace("\n", "").replace("\u008d", "").split("\\r", -1));
    }

    public Body getStripedBody(String[] lines) {
        final Map<String, String> kltmp = new LinkedHashMap<String, String>();
        List<String> controlLines = new ArrayList<>();
        SeenBy seenBy = new SeenBy();
        FidoPath path = new FidoPath();
        StringBuilder sb = new StringBuilder();
        String origin = null;
        if (lines.length > 0 && lines[0].startsWith("AREA:")) {
            area = lines[0].substring(5).trim();
            echomail = true;
        } else {
            area = "";
            echomail = false;
        }

        int defaultZone = fAddr == null ? 2 : fAddr.getZone();
        for (int i = 0; i < lines.length; i++) {
            String x = lines[i];
            if (i == lines.length - 1 && x.isEmpty()) {
                continue;
            }
            if (i == 0 && echomail) {
                continue;
            }
            if (x.startsWith("\1")) {
                Matcher control = Pattern.compile("^\\u0001([^ :]+)(?::|(?= ))(.*)$").matcher(x);
                if (!control.matches()) {
                    sb.append(x).append("\r");
                    continue;
                }
                String key = control.group(1);
                String value = control.group(2);
                if (x.startsWith(FidoPath.PATH_LINE_PREFIX)) {
                    path.add(FidoPath.valueOf(value, defaultZone));
                } else {
                    kltmp.putIfAbsent(key, value);
                    controlLines.add(x);
                }
            } else {
                if (x.startsWith("SEEN-BY: ")) {
                    String[] split = collon.split(x, 2);
                    seenBy.add(SeenBy.valueOf(split[1], defaultZone));
                } else if (x.startsWith(" * Origin")) {
                    origin = x;
                } else {
                    sb.append(x).append("\r");
                }
            }
        }
        Body parsed = new Body(sb.toString(), kltmp, origin, seenBy, path);
        parsed.setArea(echomail ? area : null);
        parsed.setControlLines(controlLines);
        return parsed;
    }

    private void tryDetectAddressesAndId() {
        String id = body.getKludges().get("MSGID");
        msgId = id == null ? null : id.trim();
        if (echomail) {
            String origin = body.getOrigin();
            if (origin != null && origin.lastIndexOf('(') >= 0
                    && origin.lastIndexOf(')') > origin.lastIndexOf('(')) {
                FTNAddr address = parseAddress(origin.substring(origin.lastIndexOf('(') + 1,
                                                                origin.lastIndexOf(')')));
                if (address != null) {
                    fAddr = address;
                }
            }
            if ((fAddr == null || !fAddr.isValid()) && msgId != null) {
                FTNAddr address = parseAddress(msgId.split("\\s+", 2)[0]);
                if (address != null) {
                    fAddr = address;
                }
            }
        } else {
            String intl = body.getKludges().get("INTL");
            if (intl != null) {
                String[] addresses = intl.trim().split("\\s+");
                if (addresses.length == 2) {
                    FTNAddr dest = parseAddress(addresses[0]);
                    FTNAddr source = parseAddress(addresses[1]);
                    if (dest != null && source != null) {
                        tAddr = dest;
                        fAddr = source;
                    }
                }
            }
            fAddr = withPoint(fAddr, body.getKludges().get("FMPT"));
            tAddr = withPoint(tAddr, body.getKludges().get("TOPT"));
        }
    }

    private static FTNAddr parseAddress(String value) {
        try {
            FTNAddr address = new FTNAddr(value);
            return address.isValid() ? address : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FTNAddr withPoint(FTNAddr address, String point) {
        if (address == null || point == null) {
            return address;
        }
        try {
            int n = Integer.parseInt(point.trim());
            if (n > 0 && n <= 65535) {
                return new FTNAddr(address.getZone(), address.getNet(), address.getNode(), n);
            }
        } catch (NumberFormatException ignored) {
        }
        return address;
    }

    public void dumpHead() {
        logger.debug("from: {} {} to: {} {} area: {}", from, fAddr, to, tAddr, area);
    }

    public boolean isEchomail() {
        return echomail;
    }

    public String getArea() {
        return area;
    }

    public String getMsgDate() {
        return msgDate;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public FTNAddr getFAddr() {
        return fAddr;
    }

    public FTNAddr getTAddr() {
        return tAddr;
    }

    public Body getBody() {
        return body;
    }

    public String getBodyInMessageCharset() {
        return body.getBody();
    }

    public FTNAddr getUpLink() {
        return upLink;
    }

    public String getMsgId() {
        return msgId;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessageCharset() {
        String messageEncoding = body.getKludges().get("CHRS");
        if (messageEncoding == null) {
            messageEncoding = body.getKludges().get("CHARSET");
        }
        if (messageEncoding == null) {
            return CharsetTools.DEFAULT_ENCODING;
        }
        return messageEncoding.trim().split("\\s+")[0];
    }

    @Override
    public String toString() {
        return "Message{\n"
               + "from='"
               + from
               + '\''
               + ", \n\tto='"
               + to
               + '\''
               + ", \n\tarea='"
               + area
               + '\''
               + ", \n\tsubject='"
               + subject
               + '\''
               + ", \n\tmsgDate='"
               + msgDate
               + '\''
               + ", \n\tmsgId='"
               + msgId
               + '\''
               + "\n}";
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }
}
