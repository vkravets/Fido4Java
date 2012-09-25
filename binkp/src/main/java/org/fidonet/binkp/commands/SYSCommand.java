package org.fidonet.binkp.commands;

import org.fidonet.binkp.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:09 PM
 */
public class SYSCommand extends NULCommand {

    @Override
    public String getArguments(SessionContext sessionContext) {
        return sessionContext.getStationConfig().getName();
    }

    @Override
    protected String getPrefix() {
        return "SYS";
    }
}