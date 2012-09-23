package org.fidonet.mina.commands.share;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.BinkpCommand;
import org.fidonet.mina.SessionContext;
import org.fidonet.mina.codec.Data;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:27 PM
 */
public interface Command extends Data {
    public boolean isHandle(SessionContext sessionContext, BinkpCommand command, String args);
    public void send(IoSession session, SessionContext sessionContext) throws Exception;
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception;
}
