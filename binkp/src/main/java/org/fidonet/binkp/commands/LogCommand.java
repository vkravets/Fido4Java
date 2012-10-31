package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/25/12
 * Time: 1:40 PM
 */
public class LogCommand extends NULCommand {

    ILogger log = LoggerFactory.getLogger(LogCommand.class);

    @Override
    protected String getPrefix() {
        return null;
    }

    @Override
    protected String getArguments(SessionContext sessionContext) {
        return sessionContext.getLogMessage();
    }

    @Override
    protected void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        log.info("REMOTE -> " + commandArgs);
    }
}
