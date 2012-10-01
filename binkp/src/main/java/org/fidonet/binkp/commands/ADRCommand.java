package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.LinksInfo;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;
import org.fidonet.binkp.config.ServerRole;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:44 PM
 */
public class ADRCommand extends MessageCommand {

    public ADRCommand() {
        super(BinkCommand.M_ADR);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_ADR) && args != null && args.length() > 0;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        if (sessionContext.getServerRole().equals(ServerRole.CLIENT)) {
            Command pwd = new PWDCommand();
            pwd.send(session, sessionContext);
        } else {
            // in case if we are server set to session context curent link
            LinksInfo linksInfo = sessionContext.getLinksInfo();
            if (linksInfo.getCurLink() != null) {
                sessionContext.setLastErrorMessage(linksInfo.getCurLink().getAddr().as4D());
                Command busy = new BSYCommand();
                busy.send(session, sessionContext);
            } else {
                commandArgs = commandArgs.trim();
                String[] tokens = commandArgs.split(" ");
                Link curLink = null;
                for (String token : tokens) {
                    FTNAddr linkAddr = new FTNAddr(token);
                    curLink = findLink(linkAddr, sessionContext.getLinksInfo().getLinks());
                    if (curLink != null) break;
                }
                if (curLink != null) {
                    linksInfo.setCurLink(curLink);
                    sessionContext.setState(SessionState.STATE_WAITPWD);
                } else {
                    Command error = new ERRCommand();
                    String msg = "Link with address [%s] is not register on the node";
                    sessionContext.setLastErrorMessage(String.format(msg, commandArgs));
                    error.send(session, sessionContext);
                    sessionContext.setState(SessionState.STATE_ERR);
                    session.close(false);
                }
            }
        }

    }

    private Link findLink(FTNAddr linkAddr, List<Link> links) {
        Link res = null;
        for (Link link : links) {
            if (link.getAddr().equals(linkAddr)) {
                res = link;
                break;
            }
        }
        return res;
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getLinksInfo().getCurLink().getMyaddr().as5D();
    }
}
