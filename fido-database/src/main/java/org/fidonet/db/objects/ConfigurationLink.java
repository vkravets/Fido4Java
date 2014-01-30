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
import org.fidonet.types.Link;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 9:54 AM
 */

@DatabaseTable(tableName = "links")
public class ConfigurationLink {

    public static final String ID_COLUMN = "id";
    public static final String ADDRESS_COLUMN = "address";
    public static final String PASSWORD_COLUMN = "password";
    public static final String PACKET_PASSWORD_COLUMN = "packet_password";
    public static final String HOST_COLUMN = "host";
    public static final String PORT_COLUMN = "port";
    public static final String FLAGS_COLUMN = "flags";

    @DatabaseField(generatedId = true, columnName = ID_COLUMN, canBeNull = false, unique = true, uniqueIndex = true)
    private Long id;

    @DatabaseField(columnName = ADDRESS_COLUMN, canBeNull = false, unique = true, uniqueIndex = true)
    private String address;

    @DatabaseField(columnName = PASSWORD_COLUMN, canBeNull = false)
    private String password;

    @DatabaseField(columnName = PACKET_PASSWORD_COLUMN, canBeNull = false)
    private String packet_password;

    @DatabaseField(columnName = HOST_COLUMN, defaultValue = "")
    private String host;

    @DatabaseField(columnName = PORT_COLUMN, dataType = DataType.INTEGER, defaultValue = "0")
    private int port;

    @DatabaseField(columnName = FLAGS_COLUMN, defaultValue = "")
    private String flags;

    public ConfigurationLink() {
    }

    public Long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPacket_password() {
        return packet_password;
    }

    public void setPacket_password(String packet_password) {
        this.packet_password = packet_password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getFlags() {
        return flags;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }

    public Link toLink() {
        return new Link(new FTNAddr(getAddress()), null, getPassword(), getHost(), getPort());
    }

}
