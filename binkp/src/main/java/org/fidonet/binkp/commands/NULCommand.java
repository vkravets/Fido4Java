/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;

import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:15 PM
 */
public abstract class NULCommand extends MessageCommand {

    public NULCommand() {
        super(BinkCommand.M_NUL);
    }

    protected abstract String getPrefix();

    protected abstract String getArguments(SessionContext sessionContext);

    protected void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        String args = getArguments(sessionContext);
        args = args == null ? "" : args;
        if (getPrefix() != null) {
            return String.format("%s %s", getPrefix(), args);
        }
        return args;
    }

    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_NUL) && args != null && args.startsWith(getPrefix());
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        String args = "";
        if (getPrefix() != null) {
            String safePrefix = Pattern.quote(getPrefix());
            args = commandArgs.replaceFirst(safePrefix, "").trim();
        }
        handleCommand(session, sessionContext, args);
    }
}
