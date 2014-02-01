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

package org.fidonet.db.upgrade.version0;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.fidonet.echobase.AccessLevel;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 10:25 AM
 */
@DatabaseTable(tableName = "subscription")
public class Subscription {

    public static final String ID_LINK_COLUMN = "id_link";
    public static final String ID_AREA_COLUMN = "id_area";
    public static final String LASTMESSAGE_ID_COLUMN = "lastmessage_id";
    public static final String ACCESS_LEVEL_COLUMN = "access_level";

    @DatabaseField(columnName = ID_LINK_COLUMN, foreign = true, foreignAutoRefresh = true)
    private ConfigurationLink configurationLink;

    @DatabaseField(columnName = ID_AREA_COLUMN, foreign = true, foreignAutoRefresh = true)
    private Echoarea echoarea;

    @DatabaseField(columnName = LASTMESSAGE_ID_COLUMN)
    private Long lastMessage;

    @DatabaseField(columnName = ACCESS_LEVEL_COLUMN, dataType = DataType.ENUM_STRING, defaultValue = "BOTH")
    private AccessLevel accessLevel;

    public ConfigurationLink getConfigurationLink() {
        return configurationLink;
    }

    public void setConfigurationLink(ConfigurationLink configurationLink) {
        this.configurationLink = configurationLink;
    }

    public Echoarea getEchoarea() {
        return echoarea;
    }

    public void setEchoarea(Echoarea echoarea) {
        this.echoarea = echoarea;
    }

    public Long getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Long lastMessage) {
        this.lastMessage = lastMessage;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
}
