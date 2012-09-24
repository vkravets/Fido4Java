package org.fidonet.binkp.commands;

import org.fidonet.binkp.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:09 PM
 */
public class ZYZCommand extends NULCommand{

    @Override
    protected String getArguments(SessionContext sessionContext) {
        return sessionContext.getStationConfig().getSysopName();
    }

    @Override
    protected String getPrefix() {
        return "ZYZ";
    }

}
