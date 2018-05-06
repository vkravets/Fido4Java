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

package org.fidonet.binkp.common.commands;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionHelper;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.commands.share.MessageCommand;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.events.ConnectedEvent;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FilesSender;
import org.fidonet.binkp.common.protocol.Session;

import java.io.InputStream;
import java.util.Deque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:14 PM
 */
public class PWDCommand extends MessageCommand {

    public PWDCommand() {
        super(BinkCommand.M_PWD);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_PWD);
    }

    @Override
    public void handle(Session session, SessionContext sessionContext, String commandArgs) throws Exception {
        if (sessionContext.getState() == SessionState.STATE_WAITPWD) {
            Password password = sessionContext.getPassword();
            final String passwordString = commandArgs.trim();
            if (passwordString.startsWith("CRAM") && sessionContext.isCryptMode()) {
                password.setCrypt(true);
                password.setMd(sessionContext.getMessageDigest());
                password.setKey(sessionContext.getPasswordKey());
            }
            if (password.getText().equals(passwordString)) {
                OKCommand ok = new OKCommand();
                ok.send(session, sessionContext);

                boolean isMD5 = password.isCrypt() && password.getMessageDigest().getAlgorithm().equals("MD5");
                log.debug("MD5: {}", isMD5);
                if (sessionContext.isCryptMode()) {
                    log.info("Setting incoming/oncoming message crypt");
                    SessionHelper.setSessionSecurity(session, sessionContext);
                }

                sessionContext.sendEvent(new ConnectedEvent(sessionContext));
                Deque<FileData<InputStream>> files = sessionContext.getSendFiles();
                // Run thread to sending files in client mode
                FilesSender filesSender = new FilesSender(session, files, sessionContext);
                session.setFileSender(filesSender);
                Thread sendFiles = new Thread(filesSender);
                sendFiles.start();
            } else {
                ERRCommand error = new ERRCommand();
                sessionContext.setState(SessionState.STATE_ERR);
                sessionContext.setLastErrorMessage(String.format("Bad password \"%s\"", commandArgs));
                error.send(session, sessionContext);
                sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
                session.close(false);
            }
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        return sessionContext.getPassword().getText();
    }

    @Override
    public void send(Session session, SessionContext sessionContext) throws Exception {
        super.send(session, sessionContext);
        sessionContext.setState(SessionState.STATE_WAITOK);
    }
}
