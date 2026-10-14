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

package org.fidonet.binkp.common.commands.share;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.commands.ADRCommand;
import org.fidonet.binkp.common.commands.BSYCommand;
import org.fidonet.binkp.common.commands.EOBCommand;
import org.fidonet.binkp.common.commands.ERRCommand;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.GETCommand;
import org.fidonet.binkp.common.commands.GOTCommand;
import org.fidonet.binkp.common.commands.LOCCommand;
import org.fidonet.binkp.common.commands.LogCommand;
import org.fidonet.binkp.common.commands.NDLCommand;
import org.fidonet.binkp.common.commands.OKCommand;
import org.fidonet.binkp.common.commands.OPTCommand;
import org.fidonet.binkp.common.commands.PWDCommand;
import org.fidonet.binkp.common.commands.SKIPCommand;
import org.fidonet.binkp.common.commands.SYSCommand;
import org.fidonet.binkp.common.commands.TIMECommand;
import org.fidonet.binkp.common.commands.TRFCommand;
import org.fidonet.binkp.common.commands.VERCommand;
import org.fidonet.binkp.common.commands.ZYZCommand;
import org.fidonet.binkp.common.io.BinkData;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:53 PM
 */
public class CommandFactory {

    private List<Command> commands;

    public Command createCommand(SessionContext sessionContext, BinkData data)
            throws IOException, UnknownCommandException {
        if (data.isCommand()) {
            String argsStr = new String(data.getData());
            BinkCommand command = BinkCommand.findCommand(data.getCommand());
            if (command != null) {
                return createCommand(sessionContext, command, argsStr);
            }
        }
        return null;
    }

    public Command createCommand(SessionContext sessionContext, BinkCommand cmd, String args)
            throws UnknownCommandException {
        for (Command command : getCommandsList()) {
            if (command.isHandle(sessionContext, cmd, args)) {
                return command;
            }
        }
        String msg = String.format("Command[%s %s] is not found! ", cmd, args);
        throw new UnknownCommandException(msg);
    }

    private List<Command> getCommandsList() {
        if (commands == null) {
            commands = new LinkedList<Command>();
            commands.add(new ADRCommand());
            commands.add(new BSYCommand());
            commands.add(new EOBCommand());
            commands.add(new ERRCommand());
            commands.add(new FILECommand());
            commands.add(new GETCommand());
            commands.add(new GOTCommand());
            commands.add(new LOCCommand());
            commands.add(new NDLCommand());
            commands.add(new OKCommand());
            commands.add(new PWDCommand());
            commands.add(new SYSCommand());
            commands.add(new TIMECommand());
            commands.add(new VERCommand());
            commands.add(new ZYZCommand());
            commands.add(new OPTCommand());
            commands.add(new TRFCommand());
            commands.add(new LogCommand());
            commands.add(new SKIPCommand());
        }
        return commands;
    }
}
