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

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.db.DatabaseType;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/25/12
 * Time: 1:49 PM
 */
public class ScriptGenerator {

    private static List<String> getDropTableStatement(DatabaseType dbType, BaseDaoImpl<?, ?> dao) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        List<String> statements = new ArrayList<String>();
        Method dropIndex = TableUtils.class.getDeclaredMethod("addDropIndexStatements",
                DatabaseType.class,
                dao.getTableInfo().getClass(),
                List.class
        );
        Method dropTables = TableUtils.class.getDeclaredMethod("addDropTableStatements",
                DatabaseType.class,
                dao.getTableInfo().getClass(),
                List.class
        );
        dropIndex.setAccessible(true);
        dropTables.setAccessible(true);
        dropIndex.invoke(null, dbType, dao.getTableInfo(), statements);
        dropTables.invoke(null, dbType, dao.getTableInfo(), statements);
        return statements;
    }

    private static String listToString(List<?> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            sb.append(item).append(sep);
        }
        return sb.toString();
    }

    public static String generateCreateScript(ConnectionSource connectionSource, BaseDaoImpl<?, ?> dao) throws SQLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();
        String tableStatement = listToString(TableUtils.getCreateTableStatements(connectionSource, dao.getDataClass()), "\r\n");
        sb.append(tableStatement);
        return sb.toString();
    }


    public static String generateCreateScript(ConnectionSource connectionSource, List<BaseDaoImpl<?, ?>> daos) throws SQLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();
        for (BaseDaoImpl<?, ?> dao : daos) {
            String tableStatement = generateCreateScript(connectionSource, dao);
            sb.append(tableStatement);
        }
        return sb.toString();
    }

    public static String generateDropScript(ConnectionSource connectionSource, BaseDaoImpl<?, ?> dao) throws SQLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();
        String tableStatement = listToString(getDropTableStatement(connectionSource.getDatabaseType(), dao), "\r\n");
        sb.append(tableStatement);
        return sb.toString();
    }

    public static String generateDropScript(ConnectionSource connectionSource, List<BaseDaoImpl<?, ?>> daos) throws SQLException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();

        for (BaseDaoImpl<?, ?> dao : daos) {
            String tableStatement = generateDropScript(connectionSource, dao);
            sb.append(tableStatement);
        }

        return sb.toString();
    }

    private static class OrmManagerEx extends OrmManager {

        public OrmManagerEx(String jdbcUrl) {
            super(jdbcUrl);
        }

        public OrmManagerEx(String jdbcUrl, String user, String password) {
            super(jdbcUrl, user, password);
        }

        public ConnectionSource getConnectionSource() {
            return connectionSource;
        }
    }


    public static void main(String[] argv) throws IOException, SQLException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        OrmManagerEx ormManager = new OrmManagerEx(argv[0]);
        ormManager.connect();

        List<BaseDaoImpl<?, ?>> daos = new ArrayList<BaseDaoImpl<?, ?>>();
        daos.addAll(ormManager.<BaseDaoImpl<?, ?>>getAllDaos());

        OutputStream createTablesStream = new FileOutputStream("createTables.sql");
        String createTablesScriptContent = generateCreateScript(ormManager.getConnectionSource(), daos);
        createTablesStream.write(createTablesScriptContent.getBytes());
        createTablesStream.flush();
        createTablesStream.close();

        OutputStream dropTablesStream = new FileOutputStream("dropTables.sql");
        String dropTablesScriptContent = generateDropScript(ormManager.getConnectionSource(), daos);
        dropTablesStream.write(dropTablesScriptContent.getBytes());
        dropTablesStream.flush();
        dropTablesStream.close();
    }
}
