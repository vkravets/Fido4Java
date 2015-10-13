/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.binkp.mina2.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.commands.BinkCommand;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.mina2.commands.share.Command;
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
                boolean wait_pwd = false;
                for (String token : tokens) {
                    FTNAddr linkAddr = new FTNAddr(token);
                    if (linksInfo.setCurLink(linkAddr)) {
                        sessionContext.setState(SessionState.STATE_WAITPWD);
                        wait_pwd = true;
                        break;
                    }
                }
                if (!wait_pwd) {
                    sessionContext.setState(SessionState.STATE_ERR);
                    Command error = new ERRCommand();
                    String msg = "Link with address [%s] is not register on the node";
                    sessionContext.setLastErrorMessage(String.format(msg, commandArgs));
                    error.send(session, sessionContext);
                    sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
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
        StringBuilder sb = new StringBuilder();
        for (FTNAddr addr : sessionContext.getLinksInfo().getCurLink().getMyaddr()) {
            sb.append(addr.as5D()).append(" ");
        }
        return sb.toString().trim();
    }
}
