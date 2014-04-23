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

package org.fidonet.binkp.mina3.commands;

import org.apache.mina.api.IoSession;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.commands.BinkCommand;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.mina3.commands.share.Command;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Deque;
import java.util.Iterator;

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
    public void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
        sessionContext.setState(SessionState.STATE_TRANSFER);
        Deque<FileData<OutputStream>> receivedFiles = sessionContext.getRecvFiles();
        FileData curFile = receivedFiles.peek();
        if (curFile != null && !curFile.getInfo().isFinished()) {
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }
        FileInfo fileInfo = null;
        try {
            fileInfo = FileInfo.parseFileInfo(commandArgs);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return;
        }
        FileData<OutputStream> fileData = sessionContext.getNotFinishedFileData(fileInfo);
        if (fileData == null) {
            fileData = new FileData<OutputStream>(fileInfo, new ByteArrayOutputStream());
            receivedFiles.addFirst(fileData);
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }

        // process NR mode
        if (fileInfo.getOffset() < 0) {
            fileInfo.setOffset(0);
            Command get = new GETCommand();
            get.send(session, sessionContext);
        }
    }

    @Override
    public String getCommandArguments(SessionContext sessionContext) {
        Iterator<FileData<InputStream>> iterator = sessionContext.getSendFiles().iterator();
        boolean isSent = true;
        FileInfo fileInfo = null;
        while (isSent && iterator.hasNext()) {
            FileData fileData = iterator.next();
            fileInfo = fileData.getInfo();
            isSent = fileInfo.getCurSize() == fileInfo.getSize();
        }
        if (fileInfo != null) {
            long offset = fileInfo.getOffset();
            if (sessionContext.getStationConfig().isNRMode() && offset == 0) {
                fileInfo.setOffset(-1);
            }
            return String.format("%s %s %s %s", fileInfo.getName(), fileInfo.getSize(), fileInfo.getTimestamp(), offset);

        }
        return null;
    }
}
