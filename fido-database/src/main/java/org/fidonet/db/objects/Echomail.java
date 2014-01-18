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

package org.fidonet.db.objects;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 9:41 AM
 */
@DatabaseTable(tableName = "echomail")
public class Echomail {

    @DatabaseField(generatedId = true, columnName = "id", canBeNull = false, unique = true, uniqueIndex = true)
    private Long id;

    @DatabaseField(columnName = "msgId", canBeNull = false, unique = true, uniqueIndex = true)
    private String msgId;

    @DatabaseField(columnName = "id_echoarea", foreign = true, foreignAutoRefresh = false)
    private Echoarea area;

    @DatabaseField(columnName = "from_name", canBeNull = false)
    private String fromName;

    @DatabaseField(columnName = "to_name", canBeNull = false)
    private String toName;

    @DatabaseField(columnName = "from_addr", canBeNull = false)
    private String fromAddr;

    @DatabaseField(columnName = "to_addr", canBeNull = true)
    private String toAddr;

    @DatabaseField(columnName = "message_date", canBeNull = false, dataType = DataType.DATE_LONG)
    private Date date;

    @DatabaseField(columnName = "subject", dataType = DataType.LONG_STRING)
    private String subject;

    @DatabaseField(columnName = "text", dataType = DataType.LONG_STRING)
    private String text;

    @DatabaseField(columnName = "seen_by", dataType = DataType.LONG_STRING)
    private String seenBy;

    @DatabaseField(columnName = "path", dataType = DataType.LONG_STRING)
    private String path;

    @DatabaseField(columnName = "attr", dataType = DataType.INTEGER)
    private int attr;

    public Echomail() {
    }

    public Long getId() {
        return id;
    }

    public Echoarea getArea() {
        return area;
    }

    public void setArea(Echoarea area) {
        this.area = area;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getToName() {
        return toName;
    }

    public void setToName(String toName) {
        this.toName = toName;
    }

    public String getFromAddr() {
        return fromAddr;
    }

    public void setFromAddr(String fromAddr) {
        this.fromAddr = fromAddr;
    }

    public String getToAddr() {
        return toAddr;
    }

    public void setToAddr(String toAddr) {
        this.toAddr = toAddr;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSeenBy() {
        return seenBy;
    }

    public void setSeenBy(String seenBy) {
        this.seenBy = seenBy;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public Message toMessage() {
        return toMessage(this);
    }

    public static Message toMessage(Echomail echomail) {
        return new Message(echomail.getFromName(),
                echomail.getToName(),
                new FTNAddr(echomail.getFromAddr()),
                new FTNAddr(echomail.getToAddr()),
                echomail.getSubject(),
                echomail.getText(),
                echomail.getDate());
    }

    public static Echomail fromMessage(Message message, Echoarea area) throws ParseException {
        Echomail msg = new Echomail();
        msg.setArea(area);
//        DateFormat format = DateFormat.getDateInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy  HH:mm:ss");
        Date msgDate = dateFormat.parse(message.getMsgDate());
        msg.setDate(msgDate);
        msg.setFromAddr(message.getFAddr().as5D());
        if (message.getTAddr() != null)
            msg.setToAddr(message.getTAddr().as5D());
        msg.setFromName(message.getFrom());
        msg.setToName(message.getTo());
        msg.setSubject(new String(message.getByteSubj()));
        msg.setText(message.getText());
        msg.setPath("");
        msg.setSeenBy("");
        msg.setMsgId(message.getMsgId());
        return msg;
    }
}
