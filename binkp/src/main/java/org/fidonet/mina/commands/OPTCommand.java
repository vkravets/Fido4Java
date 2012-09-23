package org.fidonet.mina.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.mina.SessionContext;
import org.fidonet.mina.io.Password;

import java.security.MessageDigest;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:44 PM
 */
public class OPTCommand extends NULCommand {

    @Override
    protected String getPrefix() {
        return "OPT";
    }

    @Override
    protected String getArguments(SessionContext sessionContext) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        if (commandArgs.startsWith("CRAM")) {
            String[] tokens = commandArgs.split("-");
            String cryptType = tokens[1];
            MessageDigest md = MessageDigest.getInstance(cryptType);
            Password password = sessionContext.getPassword();
            password.setCrypt(true);
            password.setMd(md);
            password.setKey(tokens[2]);
        }
    }
}
