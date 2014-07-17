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
import org.fidonet.fts.FidoPath;
import org.fidonet.fts.SeenBy;
import org.fidonet.jftn.tools.SafeSimpleDateFormat;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.text.ParseException;
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

    public static final String ID_COLUMN = "id";
    public static final String MSGID_COLUMN = "msgId";
    public static final String ID_AREA_COLUMN = "id_echoarea";
    public static final String FROM_NAME_COLUMN = "from_name";
    public static final String TO_NAME_COLUMN = "to_name";
    public static final String FROM_ADDR_COLUMN = "from_addr";
    public static final String TO_ADDR_COLUMN = "to_addr";
    public static final String DATE_COLUMN = "message_date";
    public static final String SUBJECT_COLUMN = "subject";
    public static final String TEXT_COLUMN = "text";
    public static final String SEENBY_COLUMN = "seen_by";
    public static final String PATH_COLUMN = "path";
    public static final String ATTR_COLUMN = "attr";
    public static final String ORIGIN_COLUMN = "origin";


    @DatabaseField(generatedId = true, columnName = ID_COLUMN, canBeNull = false, unique = true, uniqueIndex = true)
    private Long id;

    @DatabaseField(columnName = MSGID_COLUMN, canBeNull = false, index = true)
    private String msgId;

    @DatabaseField(columnName = ID_AREA_COLUMN, canBeNull = false, foreign = true, foreignAutoRefresh = false, foreignAutoCreate = false, index = true)
    private Echoarea area;

    @DatabaseField(columnName = FROM_NAME_COLUMN, canBeNull = false)
    private String fromName;

    @DatabaseField(columnName = TO_NAME_COLUMN, canBeNull = false)
    private String toName;

    @DatabaseField(columnName = FROM_ADDR_COLUMN, canBeNull = false)
    private String fromAddr;

    @DatabaseField(columnName = TO_ADDR_COLUMN, canBeNull = true)
    private String toAddr;

    @DatabaseField(columnName = DATE_COLUMN, canBeNull = false, dataType = DataType.DATE_LONG)
    private Date date;

    @DatabaseField(columnName = SUBJECT_COLUMN, dataType = DataType.LONG_STRING)
    private String subject;

    @DatabaseField(columnName = TEXT_COLUMN, dataType = DataType.LONG_STRING)
    private String text;

    @DatabaseField(columnName = ORIGIN_COLUMN, dataType = DataType.LONG_STRING)
    private String origin;

    @DatabaseField(columnName = SEENBY_COLUMN, dataType = DataType.LONG_STRING)
    private String seenBy;

    @DatabaseField(columnName = PATH_COLUMN, dataType = DataType.LONG_STRING)
    private String path;

    @DatabaseField(columnName = ATTR_COLUMN, dataType = DataType.INTEGER)
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

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
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

    public int getAttr() {
        return attr;
    }

    public void setAttr(int attr) {
        this.attr = attr;
    }

    public Message toMessage() {
        return toMessage(this);
    }

    public static Message toMessage(Echomail echomail) {
        FTNAddr toAddr = null;
        if (echomail.getToAddr() != null) {
            toAddr = new FTNAddr(echomail.getToAddr());
        }
        Message message = new Message(echomail.getFromName(),
                echomail.getToName(),
                new FTNAddr(echomail.getFromAddr()),
                toAddr,
                echomail.getSubject(),
                echomail.getText(),
                echomail.getDate());
        message.getBody().setOrigin(echomail.getOrigin());
        // FIXME: default zone
        message.getBody().setSeenBy(SeenBy.valueOf(echomail.getSeenBy(), 2));
        message.getBody().setPath(FidoPath.valueOf(echomail.getPath(), 2));
        return message;
    }

    public static Echomail fromMessage(Message message, Echoarea area) throws ParseException {
        Echomail msg = new Echomail();
        msg.setArea(area);
        SafeSimpleDateFormat dateFormat = new SafeSimpleDateFormat("dd MMM yy  HH:mm:ss");
        Date msgDate = dateFormat.parse(message.getMsgDate());
        msg.setDate(msgDate);
        msg.setFromAddr(message.getFAddr().as5D());
        if (message.getTAddr() != null)
            msg.setToAddr(message.getTAddr().as5D());
        msg.setMsgId(message.getMsgId());
        msg.setFromName(message.getFrom());
        msg.setToName(message.getTo());
        msg.setSubject(message.getSubject());
        msg.setText(message.getBody().getBody());
        msg.setOrigin(message.getBody().getOrigin());
        msg.setSeenBy(message.getBody().getSeenBy().toString());
        msg.setPath(message.getBody().getPath().toString());
        return msg;
    }

}
