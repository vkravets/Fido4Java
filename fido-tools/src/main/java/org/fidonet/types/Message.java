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

import org.fidonet.fts.FtsPackMsg;
import org.fidonet.tools.CharsetTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class Message {

    private static final Logger logger = LoggerFactory.getLogger(Message.class.getName());

    private String from;
    private String to;
    private String subject;
    private String area;
    private FTNAddr fAddr;
    private FTNAddr tAddr;
    private String[] kludges;
    private boolean echomail;
    private static final Pattern collon = Pattern.compile(":");
    private static final Pattern collonorwhitespace = Pattern.compile("[:][:\\s]");
    private String body;
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
        this.body = message;
        this.msgDate = date.toString();
        this.isValid = true;
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


        kludges = getKludgesFromBody(src.getBody());
//        String messageCharsetName = getMessageCharset();
//        Charset charset = CharsetTools.charsetDetect(messageCharsetName);
//        CharBuffer decode = charset.decode(ByteBuffer.wrap(src.getBody()));
//        body = new String(decode.array());
        try {
            body = new String(src.getBody(), CharsetTools.DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.warn(e.getMessage(), e);
        }

        if (echomail) {
            tryDetectAddressesAndId();
        }
        isValid = true;
    }

    public String[] getKludgesFromBody(byte[] body) {
        if (body != null && body.length > 0) {
            String bodyStr = new String(body, Charset.forName(CharsetTools.DEFAULT_ENCODING));
            return getKludgesFromBody(bodyStr.split("\\r"));
        }
        return null;
    }

    public void updateKludges() {
        kludges = getKludgesFromBody(body);
    }

    public String[] getKludgesFromBody(String body) {
        return getKludgesFromBody(body.split("\\r"));
    }

    public String[] getKludgesFromBody(String[] lines) {
        final LinkedList<String> kltmp = new LinkedList<String>();
        if (lines[0].startsWith("AREA:")) {
            area = collon.split(lines[0])[1];
            echomail = true;
        } else {
            area = "";
            echomail = false;
        }

        for (String x : lines) {
            if (x.lastIndexOf(1) == 0) {
                kltmp.add(x.substring(1, x.length()));
            } else {
                if (x.startsWith("SEEN-BY: ")) {
                    kltmp.add(x);
                }
            }
        }
        String[] result = new String[kltmp.size()];
        return kltmp.toArray(result);
    }

    public String getSingleKludge(String kl) {
        StringBuilder result = new StringBuilder();
        for (String Kludge : kludges) {
            if (Kludge.startsWith(kl)) {
                String kkk;
                try {
                    kkk = collonorwhitespace.split(Kludge)[1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    kkk = "";
                }
                result.append(kkk).append(" ");
            }
        }

        String s = result.toString().trim();
        return s.isEmpty() ? null : s;
    }

    void tryDetectAddressesAndId() {
        final String fmpt = getSingleKludge("MSGID: ");
        Pattern p = Pattern.compile("[\\s@]");
        if (fmpt != null) {
            String[] tokens = p.split(fmpt);
            fAddr = new FTNAddr(tokens[0].trim());
            msgId = fmpt.trim();
            logger.debug("{} {} {} {}", msgId, subject, from, area);
        } else {
            fAddr = new FTNAddr(-1, -1, -1, -1);
        }

        if (!fAddr.isValid()) {

            String[] splittedleeter = body.split("\\r");
            for (int i = 0; i < splittedleeter.length; i++) {
                if (splittedleeter[i].indexOf("SEEN-BY:") == 0) {
                    if (splittedleeter[i - 1].contains("Origin:")) {
                        final String origin = splittedleeter[i - 1];
                        fAddr = new FTNAddr(origin.substring(origin.indexOf('(') + 1, origin.indexOf(')')));
                    }
                }
            }
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

    public String[] getKludges() {
        return kludges.clone();
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

    public String getBody() {
        return body;
    }

    public String getBodyInMessageCharset() {
        Charset charset = CharsetTools.charsetDetect(getMessageCharset());
        return new String(charset.encode(body).array());
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
        String messageEncoding = getSingleKludge("CHRS");
        if (messageEncoding == null) {
            return CharsetTools.DEFAULT_ENCODING;
        }
        return messageEncoding.split(" ")[0];
    }

    @Override
    public String toString() {
        return "Message{\n" +
                "from='" + from + '\'' +
                ", \nto='" + to + '\'' +
                ", \narea='" + area + '\'' +
                ", \nsubject='" + subject + '\'' +
                ", \nmsgDate='" + msgDate + '\'' +
                ", \nmsgId='" + msgId + '\'' +
                "\n}";
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }
}
