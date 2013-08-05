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
import org.fidonet.binkp.events.FileSendEvent;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 6:11 PM
 */
public class GOTCommand extends MessageCommand {

    public GOTCommand() {
        super(BinkCommand.M_GOT);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_GOT) && args != null && args.length() > 0;
    }

    private FileData<InputStream> findSentFile(SessionContext sessionContext, FileInfo info) {
        for (FileData<InputStream> next : sessionContext.getSendFiles()) {
            FileInfo fileInfo = next.getInfo();
            if (fileInfo.equals(info)) {
                return next;
            }
        }
        return null;
    }

    @Override
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        FileInfo info = FileInfo.parseFileInfo(commandArgs);
        FileData<InputStream> sentFile = findSentFile(sessionContext, info);
        if (sentFile != null) {
            FileInfo fileSentInfo = sentFile.getInfo();
            fileSentInfo.setFinished(true);
            sessionContext.sendEvent(new FileSendEvent(sessionContext, sentFile));
            long totalSize = sessionContext.getRecvFilesSize() + fileSentInfo.getSize();
            sessionContext.setSendFilesSize(totalSize);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        FileData<OutputStream> fileData = sessionContext.getRecvFiles().peek();
        FileInfo fileInfo = fileData.getInfo();
        return String.format("%s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp());
    }
}
