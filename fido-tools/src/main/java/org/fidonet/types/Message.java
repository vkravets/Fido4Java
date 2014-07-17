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

package org.fidonet.types;

import org.fidonet.Body;
import org.fidonet.fts.FidoPath;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.SeenBy;
import org.fidonet.tools.CharsetTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public Message(String from, String to, FTNAddr fromAddr, FTNAddr toAddr, String subj, String message, Date date) {
        this.from = from;
        this.to = to;
        this.fAddr = fromAddr;
        this.tAddr = toAddr;
        this.subject = subj;
        this.msgDate = date.toString();
        this.isValid = true;

        stripAndUpdateKludges(message);

        if (echomail) {
            tryDetectAddressesAndId();
        }

    }

    public Message(FtsPackMsg src) {
        from = src.getFrom();
        to = src.getTo();
        subject = src.getSubj();
        attrs = new Attribute(src.getAttr());
        byte[] ttTime = src.getDateTime();
        msgDate = new String(ttTime);
        int net = src.getOrigNet();
        int node = src.getOrigNode();
        upLink = new FTNAddr(2, net, node, 0);


//        String messageCharsetName = getMessageCharset();
//        Charset charset = CharsetTools.charsetDetect(messageCharsetName);
//        CharBuffer decode = charset.decode(ByteBuffer.wrap(src.getBody()));
//        body = new String(decode.array());
        try {
            stripAndUpdateKludges(new String(src.getBody(), CharsetTools.DEFAULT_ENCODING));
        } catch (UnsupportedEncodingException e) {
            logger.warn(e.getMessage(), e);
        }

        if (echomail) {
            tryDetectAddressesAndId();
        }
        isValid = true;
    }

    public void stripAndUpdateKludges(String str) {
        body = getStripedBody(str);
    }

    public Body getStripedBody(String body) {
        return getStripedBody(body.split("\\r"));
    }

    public Body getStripedBody(String[] lines) {
        final Map<String, String> kltmp = new LinkedHashMap<String, String>();
        SeenBy seenBy = new SeenBy();
        FidoPath path = new FidoPath();
        StringBuffer sb = new StringBuffer();
        String origin = null;
        if (lines[0].startsWith("AREA:")) {
            area = collon.split(lines[0])[1];
            echomail = true;
        } else {
            area = "";
            echomail = false;
        }

        for (String x : lines) {
            if (x.lastIndexOf(1) == 0 && x.indexOf(":") > 0) {
                String[] split = collon.split(x, 2);
                if (x.startsWith(FidoPath.PATH_LINE_PREFIX)) {
                    // FIXME: need to pass default zone
                    path.add(FidoPath.valueOf(split[1], 2));
                } else {
                    kltmp.put(split[0].trim(), split[1]);
                }
            } else {
                if (x.startsWith("SEEN-BY: ")) {
                    String[] split = collon.split(x, 2);
                    // FIXME: need to pass default zone
                    seenBy.add(SeenBy.valueOf(split[1], 2));
                } else if (x.startsWith(" * Origin")) {
                    origin = x;
                } else {
                    sb.append(x).append("\r");
                }
            }
        }
        return new Body(sb.toString(), kltmp, origin, seenBy, path);
    }

    private void tryDetectAddressesAndId() {
        final String fmpt = body.getKludges().get("MSGID");
        Pattern p = Pattern.compile("[\\s@]");
        if (fmpt != null) {
            String[] tokens = p.split(fmpt);
            fAddr = new FTNAddr(tokens[0].trim());
            msgId = fmpt.trim();
            logger.debug("{} {} {} {}", msgId, subject, from, area);
        } else {
            fAddr = new FTNAddr(-1, -1, -1, -1);
        }

        if (!fAddr.isValid() && body.getOrigin() != null) {
            final String origin = body.getOrigin();
            fAddr = new FTNAddr(origin.substring(origin.lastIndexOf('(') + 1, origin.lastIndexOf(')')));
        }
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
        Charset charset = CharsetTools.charsetDetect(getMessageCharset());
        return new String(charset.encode(body.getBody()).array());
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
            return CharsetTools.DEFAULT_ENCODING;
        }
        return messageEncoding.split(" ")[0];
    }

    @Override
    public String toString() {
        return "Message{\n" +
                "from='" + from + '\'' +
                ", \n\tto='" + to + '\'' +
                ", \n\tarea='" + area + '\'' +
                ", \n\tsubject='" + subject + '\'' +
                ", \n\tmsgDate='" + msgDate + '\'' +
                ", \n\tmsgId='" + msgId + '\'' +
                "\n}";
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }
}
