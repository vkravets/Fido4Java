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

package org.fidonet.echobase.jam;

import org.fidonet.echobase.EchoBase;
import org.fidonet.echobase.EchoCfg;
import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.echobase.jam.struct.FixedHeaderInfoStruct;
import org.fidonet.echobase.jam.struct.MessageHeader;
import org.fidonet.echobase.jam.struct.SubField;
import org.fidonet.jftn.tools.SafeSimpleDateFormat;
import org.fidonet.misc.MyCRC;
import org.fidonet.tools.CharsetTools;
import org.fidonet.types.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class JAMEchoBase implements EchoBase {

    private static final Logger logger = LoggerFactory.getLogger(JAMEchoBase.class.getName());

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

    private EchoList echoList;

    public JAMEchoBase(EchoList echoList) {
        this.echoList = echoList;
    }

    void setFileNames(String name) {
        EchoCfg echo = echoList.getEcho(name);
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

    public void createArea(String name) throws EchoBaseException {
        setFileNames(name);
        initFiles();
        try {
            headerfile.createNewFile();
            textfile.createNewFile();
            indexfile.createNewFile();
            lastrfile.createNewFile();
        } catch (IOException e) {
            throw new EchoBaseException("Unable to create " + name + "area");
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
    public void openArea(String name) throws EchoBaseException {
        setFileNames(name);
        initFiles();
        if (!isExists()) {
            createArea(name);
        }
        try {
            header = new JHRFile(headerfile);
            text = new JDTFile(textfile);
            index = new JDXFile(indexfile);
            lastread = new JLRFile(lastrfile);
        } catch (FileNotFoundException e) {
            throw new EchoBaseException(e.getMessage());
        }
    }

    @Override
    public void closeArea() throws EchoBaseException {
        try {
            header.close();
            text.close();
            index.close();
            lastread.close();
        } catch (IOException e) {
            throw new EchoBaseException(e.getMessage());
        }
    }

    @Override
    public void addMessage(Message msg) throws EchoBaseException {
        int shiftoflast;
        try {
            shiftoflast = index.getLastMessageShift();
        } catch (IOException e) {
            shiftoflast = 0;
        }
        int mesnum;
        MessageHeader msgheader = null;
        try {
            msgheader = header.getMsgHeaderByShift(shiftoflast);
        } catch (IOException e) {
            throw new EchoBaseException(e.getMessage());
        }
        if (msgheader == null) {
            mesnum = 0;
        } else
            mesnum = msgheader.MessageNumber;

        MessageHeader newmsg = new MessageHeader();
        newmsg.MessageNumber = mesnum + 1;
        newmsg.TimesRead = 0;
        try {
            newmsg.Offset = text.getSize();
        } catch (IOException e) {
            throw new EchoBaseException(e.getMessage());
        }
        String MSGID = msg.getMsgId();
        newmsg.MSGIDcrc = MyCRC.CRC(MSGID.getBytes());

        String REPLY = msg.getBody().getKludges().get("REPLY");
        if (REPLY != null) newmsg.REPLYcrc = MyCRC.CRC(REPLY.getBytes());

        newmsg.Attribute |= 0x01000000; // MSG_TYPEECHO

        SafeSimpleDateFormat df = new SafeSimpleDateFormat("dd MMM yy  HH:mm:ss", Locale.US);

        Date date;
        try {
            date = df.parse(msg.getMsgDate());
        } catch (ParseException e) {
            throw new EchoBaseException(e.getMessage(), e);
        }

        newmsg.DateWritten = (int) (date.getTime() / 1000L);
        newmsg.DateReceived = 0; //(int) (System.currentTimeMillis() / 1000L);
        newmsg.DateProcessed = (int) (System.currentTimeMillis() / 1000L);
        newmsg.PasswordCRC = 0xFFFFFFFF;

        SubField FromName = new SubField();
        FromName.loID = 2;
        FromName.buffer = msg.getFrom().getBytes();
        FromName.datalen = FromName.buffer.length;
        newmsg.SubFieldList.add(FromName);

        SubField ToName = new SubField();
        ToName.loID = 3;
        ToName.buffer = msg.getTo().getBytes();
        ToName.datalen = ToName.buffer.length;
        newmsg.SubFieldList.add(ToName);

/*        SubField FromAdd = new SubField();
        FromAdd.LoID = 0;
        FromAdd.Buffer = msg.getFAddr().toString().getBytes();
        FromAdd.datalen = FromAdd.Buffer.length;
        newmsg.SubFieldList.add(FromAdd);*/

        SubField Subject = new SubField();
        Subject.loID = 6;
        Subject.buffer = msg.getSubject().getBytes(Charset.forName(CharsetTools.DEFAULT_ENCODING));
        Subject.datalen = Subject.buffer.length;
        newmsg.SubFieldList.add(Subject);


        if (msg.getTAddr() != null) {
            SubField ToAdd = new SubField();
            ToAdd.loID = 1;
            ToAdd.buffer = msg.getTAddr().toString().getBytes();
            ToAdd.datalen = ToAdd.buffer.length;
            newmsg.SubFieldList.add(ToAdd);
        }

        Pattern collon = Pattern.compile("[:][:\\s]");

        SubField msgid = new SubField();
        msgid.loID = 4;
        msgid.buffer = msg.getMsgId().getBytes();
        msgid.datalen = msgid.buffer.length;
        newmsg.SubFieldList.add(msgid);

        Map<String, String> kludges = msg.getBody().getKludges();
        for (String kludgeName : kludges.keySet()) {
            if (kludgeName.equals("REPLY")) {
                SubField reply = new SubField();
                reply.loID = 5;
                reply.buffer = kludges.get("REPLY").getBytes();
                reply.datalen = reply.buffer.length;
                newmsg.SubFieldList.add(reply);
            } else if (kludgeName.equals("TZUTC")) {
                SubField tz = new SubField();
                tz.loID = 2004;
                tz.buffer = kludges.get("TZUTC").getBytes();
                tz.datalen = tz.buffer.length;
                newmsg.SubFieldList.add(tz);
                newmsg.SubfieldLen = newmsg.getSubLen();
            } else {
                SubField tid = new SubField();
                tid.loID = 2000;
                tid.buffer = (kludgeName + ": " + kludges.get(kludgeName)).getBytes();
                tid.datalen = tid.buffer.length;
                newmsg.SubFieldList.add(tid);
            }

        }


        SubField path = new SubField();
        path.loID = 2002;
        path.buffer = msg.getBody().getPath().toPathString().getBytes();
        path.datalen = path.buffer.length;
        newmsg.SubFieldList.add(path);
        newmsg.SubfieldLen = newmsg.getSubLen();

        SubField seen = new SubField();
        seen.loID = 2001;
        seen.buffer = msg.getBody().getSeenBy().toSeenByString().getBytes();
        
        seen.datalen = seen.buffer.length;
        newmsg.SubFieldList.add(seen);
        newmsg.SubfieldLen = newmsg.getSubLen();

        byte[] msgtext = msg.getBody().toString().getBytes(Charset.forName(CharsetTools.DEFAULT_ENCODING));

        newmsg.TxtLen = msgtext.length;
        int headeroffset = 0;
        try {
            headeroffset = header.writeHeader(newmsg);
            text.writeText(msgtext);
            index.writeIndex(msg.getTo(), headeroffset);

            FixedHeaderInfoStruct fh = header.getFixedHeader();
            fh.setActivemsg(fh.getActivemsg() + 1);
            header.setFixedHeader(fh);
        } catch (IOException e) {
            throw new EchoBaseException(e.getMessage());
        }
    }

    @Override
    public void delMessage(int id) throws EchoBaseException {

    }

    @Override
    public void getMessage(int id) throws EchoBaseException {

    }

    boolean isExists() {
        File headerfile = new File(headerfilename);
        return headerfile.exists();
    }
}
