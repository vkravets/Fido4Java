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
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.commands.share.Command;
import org.fidonet.binkp.common.commands.share.MessageCommand;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.ReceiveTransfer;
import org.fidonet.binkp.common.protocol.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Deque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:48 PM
 */
public class FILECommand extends MessageCommand {

    public FILECommand() {
        super(BinkCommand.M_FILE);
    }

    @Override
    public boolean isHandle(SessionContext sessionContext, BinkCommand command, String args) {
        return command.equals(BinkCommand.M_FILE) && args != null && args.length() > 0;
    }

    @Override
    public void handle(Session session, SessionContext sessionContext, String commandArgs)
            throws Exception {
        sessionContext.validateIncomingCommand(BinkCommand.M_FILE);
        Deque<FileData<OutputStream>> receivedFiles = sessionContext.getRecvFiles();
        FileData curFile = receivedFiles.peek();
        FileInfo fileInfo = FileInfo.parseFileInfo(commandArgs);
        if (fileInfo.getOffset() < -1 || fileInfo.getOffset() > fileInfo.getSize()) {
            throw new IOException("Invalid file offset");
        }
        if (curFile != null && !curFile.getInfo().isFinished()
                && !curFile.getInfo().equals(fileInfo)) {
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }
        FileData<OutputStream> fileData = sessionContext.getNotFinishedFileData(fileInfo);
        if (fileData == null) {
            fileData = ReceiveTransfer.open(sessionContext, fileInfo);
            receivedFiles.addFirst(fileData);
        } else {
            receivedFiles.remove(fileData);
            receivedFiles.addFirst(fileData);
        }

        sessionContext.startFileTransfer("receiving", fileData, fileData.getInfo().getCurSize());
        if (fileData.getInfo().getCurSize() == fileInfo.getSize()) {
            ReceiveTransfer.complete(session, sessionContext);
            return;
        }

        // process NR mode
        boolean requestOffset = fileInfo.getOffset() < 0
                || fileInfo.getOffset() != fileData.getInfo().getCurSize();
        fileData.setWaitingForHeader(requestOffset);
        if (requestOffset) {
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        FileData<InputStream> fileData = sessionContext.getSendFiles().peek();
        FileInfo fileInfo = fileData == null ? null : fileData.getInfo();
        if (fileInfo != null) {
            long offset = fileInfo.getOffset();
            if (sessionContext.isNRMode() && offset == 0 && !fileData.isResumeResponse()) {
                fileInfo.setOffset(-1);
                offset = -1;
            }
            if (offset < 0) {
                sessionContext.setState(SessionState.STATE_WAITGET);
            }
            return fileInfo.arguments() + " " + offset;
        }
        return null;
    }
}
