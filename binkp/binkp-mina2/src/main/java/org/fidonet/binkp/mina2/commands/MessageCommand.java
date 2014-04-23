/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.binkp.mina2.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.mina2.SessionContext;
import org.fidonet.binkp.mina2.commands.share.BinkCommand;
import org.fidonet.binkp.mina2.commands.share.Command;
import org.fidonet.binkp.mina2.io.BinkFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:27 PM
 */
public abstract class MessageCommand implements Command {

    protected static final Logger log = LoggerFactory.getLogger(MessageCommand.class);

    protected BinkCommand commandType;

    public MessageCommand(BinkCommand commandType) {
        this.commandType = commandType;
    }

    public BinkCommand getCommandType() {
        return commandType;
    }

    @Override
    public abstract boolean isHandle(SessionContext sessionContext, BinkCommand command, String args);

    @Override
    public abstract void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception;

    @Override
    public void send(IoSession session, SessionContext sessionContext) throws Exception {
        String cmdArgs = getCommandArguments(sessionContext);
        log.debug("Sending message {} [{}]", BinkCommand.findCommand(commandType.getCmd()), cmdArgs);
        session.write(getData(cmdArgs));
    }

    public abstract String getCommandArguments(SessionContext sessionContext);

    public BinkFrame getData(String cmdArgs) {
        ByteBuffer buf;
        byte[] argsData = null;
        if (cmdArgs != null) {
            argsData = cmdArgs.getBytes();
            buf = ByteBuffer.allocate(1 + argsData.length);
        } else {
            buf = ByteBuffer.allocate(1);
        }
        buf.put(commandType.getCmd());
        if (argsData != null) {
            buf.put(argsData);
        }
        int len = buf.capacity() | 0x8000;
        return new BinkFrame((short) len, buf.array());
    }

    @Override
    public BinkFrame getRawData() {
        return null;
    }

    @Override
    public boolean isCommand() {
        return true;
    }

    @Override
    public String toString() {
        return "MessageCommand{" +
                "commandType=" + commandType +
                "isCommand=" + isCommand() +
                '}';
    }
}
