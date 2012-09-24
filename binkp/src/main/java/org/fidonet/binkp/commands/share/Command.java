package org.fidonet.binkp.commands.share;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.codec.Data;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:27 PM
 */
public interface Command extends Data {
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args);
    public void send(IoSession session, SessionContext sessionContext) throws Exception;
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception;
}
