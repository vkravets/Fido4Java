package org.fidonet.types;

import org.fidonet.fts.FtsPackMsg;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.util.Date;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class Message {

    private static final ILogger logger = LoggerFactory.getLogger(Message.class.getName());

    // TODO change field name to camel case (first latter is in lower case)
    private String From;
    private String To;
    private String Subject;
    private byte[] byteSubj;
    private String Area;
    private String Text = "";
    private FTNAddr FAddr;
    private FTNAddr TAddr;
    private String[] Kludges;
    private String[] splittedleeter;
    private boolean echomail;
    private Pattern collon = Pattern.compile(":");
    private Pattern collonorwhitespace = Pattern.compile("[:][:\\s]");
    private byte[] Body;
    private String msgDate;
    private Attribute attrs;
    private FTNAddr UpLink;

    public Message(String from, String to, FTNAddr fromAddr, FTNAddr toAddr, String subj, String message, Date date) {
        this.From = from;
        this.To = to;
        this.FAddr = fromAddr;
        this.TAddr = toAddr;
        this.Subject = subj;
        this.Text = message;
        this.msgDate = date.toString();
    }

    public Message(FtsPackMsg src) {
        From = src.getFrom();
        To = src.getTo();
        byteSubj = src.getSubj();
        Subject = new String(byteSubj);
        attrs = new Attribute(src.getAttr());
        byte[] ttTime = src.getDateTime();
        msgDate = new String(ttTime);
        int net = src.getOrigNet();
        int node = src.getOrigNode();
        UpLink = new FTNAddr(2, net, node, 0);

        splittedleeter = src.getSplittedtext();
        if (splittedleeter[0].startsWith("AREA:")) {
            Area = collon.split(splittedleeter[0])[1];
            echomail = true;
        } else {
            Area = "";
            echomail = false;
        }
        final LinkedList<String> kltmp = new LinkedList<String>();
        for (String x : splittedleeter) {
            if (x.lastIndexOf(1) == 0) {
                kltmp.add(x.substring(1, x.length()));
            } else {
                if (x.startsWith("SEEN-BY: ")) {
                    kltmp.add(x);
                }
            }
        }
        Kludges = new String[kltmp.size()];
        Kludges = kltmp.toArray(Kludges);

        int bodystart = 0;
        int bodyend = 0;
        for (int i = 1; i < src.body.length; i++) {
            if ((src.body[i] == 0x0d) && (src.body[i + 1] != 0x1)) {
                bodystart = i + 1;
                break;
            }
        }

        for (int i = bodystart; i < src.body.length; i++) {
            if ((src.body[i] == 0x0d) && (src.body[i + 1] == 'S') && (src.body[i + 2] == 'E')) {
                bodyend = i + 1;
                break;
            }
        }

        Body = new byte[bodyend - bodystart];

/*        for (int i = bodystart; i < bodyend; i++) {
            Body[i - bodystart] = src.body[i];
        }*/


        System.arraycopy(src.body, bodystart, Body, /*bodystart - bodystart*/ 0, bodyend - bodystart);


        if (echomail) {
            tryDetectAddresses();
        }

    }

    public String getSingleKludge(String kl) {
        for (String Kludge : Kludges) {
            if (Kludge.startsWith(kl)) {
                String kkk;
                try {
                    kkk = collonorwhitespace.split(Kludge)[1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    kkk = "";
                }
                return kkk;
            }
        }
        return null;
    }

    void tryDetectAddresses() {
        final String fmpt = getSingleKludge("MSGID: ");
        Pattern p = Pattern.compile("[\\s@]");
        if (fmpt != null) {
            FAddr = new FTNAddr(p.split(fmpt)[0]);
        } else {
            FAddr = new FTNAddr(-1, -1, -1, -1);
        }

        if (!FAddr.isValid()) {

            for (int i = 0; i < splittedleeter.length; i++) {
                if (splittedleeter[i].indexOf("SEEN-BY:") == 0) {
                    if (splittedleeter[i - 1].contains("Origin:")) {
                        final String origin = splittedleeter[i - 1];
                        FAddr = new FTNAddr(origin.substring(origin.indexOf('(') + 1, origin.indexOf(')')));
                    }
                }
            }
        }

    }

    public void DumpHead() {
        logger.debug("From: " + From + ' ' + FAddr + " To: " + To + ' ' + TAddr + " area: " + Area);
    }

    public boolean isEchomail() {
        return echomail;
    }

    public String getArea() {
        return Area;
    }

    public String[] getSplittedleeter() {
        return splittedleeter.clone();
    }

    public String getText() {
        return Text;
    }

    public String[] getKludges() {
        return Kludges.clone();
    }

    public String getMsgDate() {
        return msgDate;
    }

    public String getFrom() {
        return From;
    }

    public String getTo() {
        return To;
    }

    public FTNAddr getFAddr() {
        return FAddr;
    }

    public byte[] getByteSubj() {
        return byteSubj.clone();
    }

    public FTNAddr getTAddr() {
        return TAddr;
    }

    public byte[] getBody() {
        return Body.clone();
    }

    public FTNAddr getUpLink() {
        return UpLink;
    }
}
