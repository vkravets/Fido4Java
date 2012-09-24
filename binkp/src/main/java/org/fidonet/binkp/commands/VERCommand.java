package org.fidonet.binkp.commands;

import org.fidonet.binkp.SessionContext;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:10 PM
 */
public class VERCommand extends NULCommand {

    public static final String version = "jftn/1.0";

    @Override
    protected String getArguments(SessionContext sessionContext) {
        return String.format("%s %s", version, "binkp/1.1");
    }

    @Override
    protected String getPrefix() {
        return "VER";
    }
}
