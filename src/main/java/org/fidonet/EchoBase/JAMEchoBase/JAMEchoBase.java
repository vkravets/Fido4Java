package org.fidonet.EchoBase.JAMEchoBase;

import org.fidonet.EchoBase.EchoBase;
import org.fidonet.EchoBase.EchoCfg;
import org.fidonet.EchoBase.EchoList;
import org.fidonet.EchoBase.JAMEchoBase.JAMStruct.FixedHeaderInfoStruct;
import org.fidonet.EchoBase.JAMEchoBase.JAMStruct.MessageHeader;
import org.fidonet.EchoBase.JAMEchoBase.JAMStruct.SubField;
import org.fidonet.misc.Logger;
import org.fidonet.misc.MyCRC;
import org.fidonet.types.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class JAMEchoBase implements EchoBase {

    private String headerfilename;
    private String textfilename;
    private String indexfilename;
    private String lastrfilename;

    private File headerfile;
    private File textfile;
    private File indexfile;
    private File lastrfile;

    private JHRFile header;
    private JDTFile text;
    private JDXFile index;
    private JLRFile lastread;

    public JAMEchoBase(String root) {
    }

    void setFileNames(String name) {
        EchoCfg echo = EchoList.getEcho(name);
        headerfilename = echo.Path + ".jhr";
        textfilename = echo.Path + ".jdt";
        indexfilename = echo.Path + ".jdx";
        lastrfilename = echo.Path + ".jlr";
    }

    void initFiles() {
        headerfile = new File(headerfilename);
        textfile = new File(textfilename);
        indexfile = new File(indexfilename);
        lastrfile = new File(lastrfilename);
    }

    public void createArea(String name) {
        setFileNames(name);
        initFiles();
        try {
            headerfile.createNewFile();
            textfile.createNewFile();
            indexfile.createNewFile();
            lastrfile.createNewFile();
        } catch (IOException e) {
            Logger.Log("Unable to create files for area " + name);
            return;
        }

        FixedHeaderInfoStruct hdr = new FixedHeaderInfoStruct();
        hdr.setDatecreated((int) System.currentTimeMillis() / 1000);
        hdr.setActivemsg(0);
        hdr.setBasemsgnum(1);
        hdr.setModcounter(1);
        hdr.setPasswordcrc(0xFFFFFFFF);

        FileOutputStream fhdr = null;

        try {
            fhdr = new FileOutputStream(headerfile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            if (fhdr != null) {
                fhdr.write(hdr.toByteArray());
                fhdr.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void openArea(String name) {
        setFileNames(name);
        initFiles();
        if (!isExists()) {
            createArea(name);
        }
        header = new JHRFile(headerfile);
        text = new JDTFile(textfile);
        index = new JDXFile(indexfile);
        lastread = new JLRFile(lastrfile);
    }

    @Override
    public void closeArea() {
        header.close();
        text.close();
        index.close();
        lastread.close();
    }

    @Override
    public void addMessage(Message msg) {
        int shiftoflast = index.getLastMessageShift();
        int mesnum;
        MessageHeader msgheader = header.getMsgHeaderByShift(shiftoflast);
        if (msgheader == null) {
            mesnum = 0;
        } else
            mesnum = msgheader.MessageNumber;

        MessageHeader newmsg = new MessageHeader();
        newmsg.MessageNumber = mesnum + 1;
        newmsg.TimesRead = 0;
        newmsg.Offset = text.getSize();
        String MSGID = msg.getSingleKludge("MSGID");
        newmsg.MSGIDcrc = MyCRC.CRC(MSGID.getBytes());

        String REPLY = msg.getSingleKludge("REPLY");
        if (REPLY != null) newmsg.REPLYcrc = MyCRC.CRC(REPLY.getBytes());

        newmsg.Attribute |= 0x01000000; // MSG_TYPEECHO

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yy  HH:mm:ss", Locale.US);

        Date date = df.parse(msg.getMsgDate(), new ParsePosition(0));

        newmsg.DateWritten = (int) (date.getTime() / 1000L);
        newmsg.DateReceived = 0; //(int) (System.currentTimeMillis() / 1000L);
        newmsg.DateProcessed = (int) (System.currentTimeMillis() / 1000L);
        newmsg.PasswordCRC = 0xFFFFFFFF;

        SubField FromName = new SubField();
        FromName.LoID = 2;
        FromName.Buffer = msg.getFrom().getBytes();
        FromName.datalen = FromName.Buffer.length;
        newmsg.SubFieldList.add(FromName);

        SubField ToName = new SubField();
        ToName.LoID = 3;
        ToName.Buffer = msg.getTo().getBytes();
        ToName.datalen = ToName.Buffer.length;
        newmsg.SubFieldList.add(ToName);

/*        SubField FromAdd = new SubField();
        FromAdd.LoID = 0;
        FromAdd.Buffer = msg.getFAddr().toString().getBytes();
        FromAdd.datalen = FromAdd.Buffer.length;
        newmsg.SubFieldList.add(FromAdd);*/

        SubField Subject = new SubField();
        Subject.LoID = 6;
        Subject.Buffer = msg.getByteSubj();
        Subject.datalen = Subject.Buffer.length;
        newmsg.SubFieldList.add(Subject);


        if (msg.getTAddr() != null) {
            SubField ToAdd = new SubField();
            ToAdd.LoID = 1;
            ToAdd.Buffer = msg.getTAddr().toString().getBytes();
            ToAdd.datalen = ToAdd.Buffer.length;
            newmsg.SubFieldList.add(ToAdd);
        }

        Pattern collon = Pattern.compile("[:][:\\s]");

        String[] kludges = msg.getKludges();
        for (String kludge : kludges) {
            if (kludge.startsWith("MSGID:")) {
                SubField msgid = new SubField();
                msgid.LoID = 4;
                msgid.Buffer = collon.split(kludge)[1].getBytes();
                msgid.datalen = msgid.Buffer.length;
                newmsg.SubFieldList.add(msgid);
            } else if (kludge.startsWith("REPLY:")) {
                SubField reply = new SubField();
                reply.LoID = 5;
                reply.Buffer = collon.split(kludge)[1].getBytes();
                reply.datalen = reply.Buffer.length;
                newmsg.SubFieldList.add(reply);
            } else if (kludge.startsWith("PATH:")) {
                SubField path = new SubField();
                path.LoID = 2002;
                path.Buffer = collon.split(kludge)[1].getBytes();
                path.datalen = path.Buffer.length;
                newmsg.SubFieldList.add(path);
                newmsg.SubfieldLen = newmsg.getSubLen();
            } else if (kludge.startsWith("TZUTC:")) {
                SubField tz = new SubField();
                tz.LoID = 2004;
                tz.Buffer = collon.split(kludge)[1].getBytes();
                tz.datalen = tz.Buffer.length;
                newmsg.SubFieldList.add(tz);
                newmsg.SubfieldLen = newmsg.getSubLen();
            } else if (kludge.startsWith("SEEN-BY:")) {
                SubField seen = new SubField();
                seen.LoID = 2001;
                seen.Buffer = collon.split(kludge)[1].getBytes();
                seen.datalen = seen.Buffer.length;
                newmsg.SubFieldList.add(seen);
                newmsg.SubfieldLen = newmsg.getSubLen();
            } else {
                SubField tid = new SubField();
                tid.LoID = 2000;
                tid.Buffer = kludge.getBytes();
                tid.datalen = tid.Buffer.length;
                newmsg.SubFieldList.add(tid);
            }

        }


        byte[] msgtext = msg.getBody();

        newmsg.TxtLen = msgtext.length;
        int headeroffset = header.writeHeader(newmsg);

        text.writeText(msgtext);
        index.writeIndex(msg.getTo(), headeroffset);

        FixedHeaderInfoStruct fh = header.getFixedHeader();
        fh.setActivemsg(fh.getActivemsg() + 1);
        header.setFixedHeader(fh);

    }

    @Override
    public void delMessage(int id) {

    }

    @Override
    public void getMessage(int id) {

    }

    boolean isExists() {
        File headerfile = new File(headerfilename);
        return headerfile.exists();
    }
}
