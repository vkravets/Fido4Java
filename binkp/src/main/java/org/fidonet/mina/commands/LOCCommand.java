package org.fidonet.mina.commands;

import org.fidonet.mina.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:09 PM
 */
public class LOCCommand extends NULCommand {

    @Override
    public String getArguments(SessionContext sessionContext) {
        return sessionContext.getStationConfig().getLocation();
    }

    @Override
    protected String getPrefix() {
        return "LOC";
    }
}
