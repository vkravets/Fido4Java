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

package org.fidonet.db;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.echobase.IBase;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:55 AM
 */
public class DatabaseManager implements IBase {

    private final OrmManager dbManager;

    public DatabaseManager(OrmManager manager) {
        this.dbManager = manager;
    }

    @Override
    public boolean open() {
        try {
            if (!dbManager.isConnect()) {
                dbManager.connect();
            }
            dbManager.createTables();
        } catch (SQLException e) {
            // TODO: logger
            return false;
        }
        return true;
    }

    @Override
    public boolean createArea(String areaName) {
        return this.createArea(areaName, null);
    }

    @Override
    public boolean createArea(String areaName, String description) {
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        Echoarea echoarea = new Echoarea();
        echoarea.setName(areaName);
        if (description != null)
            echoarea.setDescription(description);
        try {
            echoareas.create(echoarea);
        } catch (SQLException e) {
            // TODO: logger
            return false;
        }
        return true;
    }

    @Override
    public Iterator<Message> getMessages(String areaname) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        List<Message> result = new ArrayList<Message>();
        try {
            List<Echoarea> echoareaList = echoareaQueryBuilder.selectColumns("id", "name").where().eq("name", areaname).query();
            if (echoareaList.size() == 0) {
                return result.iterator();
            }
            List<Echomail> query = echomailQueryBuilder.where().eq("id_echoarea", echoareaList.get(0).getId()).query();
            for (Echomail echomail : query) {
                result.add(echomail.toMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();  //todo: logger
        }
        return result.iterator();
    }

    @Override
    public Iterator<Message> getMessages(String areaname, int bundleSize) {
        return null;  //todo: implement
    }

    @Override
    public Iterator<Message> getMessages(Link link) {
        return null;  //todo: implement
    }

    @Override
    public Iterator<Message> getMessages(Link link, int bundleSize) {
        return null; //todo: implement
    }

    @Override
    public Iterator<Message> getMessages(Link link, String areaname, int bundleSize) {
        return null; //todo: implement
    }

    @Override
    public Iterator<Message> getMessages(Link link, String areaname, long startMessage, int bundleSize) {
        return null;  //todo: implement
    }

    @Override
    public Message getMessage(String area, int id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        Dao<Echoarea, Object> echoareas = dbManager.getDao(Echoarea.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        QueryBuilder<Echoarea, Object> echoareaQueryBuilder = echoareas.queryBuilder();
        try {
            List<Echoarea> echoareaList = echoareaQueryBuilder.selectColumns("id", "name").where().eq("name", area).query();
            if (echoareaList.size() == 0) {
                return null;
            }
            List<Echomail> query = echomailQueryBuilder.where().eq("id_echoarea", echoareaList.get(0).getId()).eq("id", id).query();
            if (query.size() > 0)
                return query.get(0).toMessage();
        } catch (SQLException e) {
            e.printStackTrace(); // TODO: logger
        }
        return null;
    }

    @Override
    public Message getMessage(int id) {
        Dao<Echomail, Object> echomails = dbManager.getDao(Echomail.class);
        QueryBuilder<Echomail, Object> echomailQueryBuilder = echomails.queryBuilder();
        try {
            List<Echomail> query = echomailQueryBuilder.where().eq("id", id).query();
            if (query.size() > 0)
                return query.get(0).toMessage();
        } catch (SQLException e) {
            e.printStackTrace();  // TODO: logger
        }
        return null;
    }

    @Override
    public long getMessageSize(String areaname) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addMessage(Message message, String areaname) {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        Dao<Echomail, Object> echomailDao = dbManager.getDao(Echomail.class);
        try {
            List<Echoarea> echoareas = echoareasDao.queryBuilder().where().eq("name", areaname).query();
            Echoarea area;
            if (echoareas.size() > 0) {
                area = echoareas.get(0);
            } else {
                // TODO: Check autocreate flag
                area = new Echoarea();
                area.setName(areaname);
                echoareasDao.create(area);
            }
            Echomail echomail = Echomail.fromMessage(message, area);
            echomailDao.create(echomail);
        } catch (SQLException e) {
            e.printStackTrace();  //todo: logger
        } catch (ParseException e) {
            e.printStackTrace();  //todo: logger
        }
    }

    public List<String> getAreas() {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        List<String> result = new ArrayList<String>();
        try {
            List<Echoarea> echoareaList = echoareasDao.queryBuilder().query();
            for (Echoarea echo : echoareaList) {
                result.add(echo.getName());
            }
        } catch (SQLException e) {
            e.printStackTrace();  //todo: logger
        }
        return result;
    }

    public boolean isAreaExists(String name) {
        Dao<Echoarea, Long> echoareasDao = dbManager.getDao(Echoarea.class);
        try {
            List<Echoarea> echoareaList = echoareasDao.queryBuilder().where().eq("name", name).query();
            return echoareaList.size() > 0;
        } catch (SQLException e) {
            e.printStackTrace();  //todo: logger
        }
        return false;
    }


    @Override
    public void close() {
        dbManager.disconnect();
    }
}
