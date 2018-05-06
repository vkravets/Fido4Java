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

package org.fidonet.binkp.common.io;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.DataBulk;
import org.fidonet.binkp.common.commands.EOBCommand;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.share.Command;
import org.fidonet.binkp.common.protocol.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;
import java.util.concurrent.Exchanger;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 11:57 AM
 */
public class FilesSender<T extends Session> implements Runnable {

    public static final Logger logger = LoggerFactory.getLogger(FilesSender.class);

    protected Deque<FileData<InputStream>> files;
    protected T session;
    protected static final int FILE_BLOCK_SIZE = 4096;
    protected SessionContext sessionContext;
    protected FileData<InputStream> curFile;
    protected Exchanger<FileInfo> exchanger;

    public FilesSender(T session, Deque<FileData<InputStream>> files, SessionContext sessionContext) {
        this.files = files;
        this.session = session;
        this.sessionContext = sessionContext;
        exchanger = new Exchanger<FileInfo>();
    }

    public boolean send(FileData<InputStream> data) throws Exception {
        final DataInputStream reader = new DataInputStream(data.getStream());
        FileInfo fileInfo = data.getInfo();
        if (fileInfo.getOffset() >= 0) {
            if (fileInfo.getOffset() <= fileInfo.getSize()) {
                long skippedBytes = reader.skip(fileInfo.getOffset());
                assert skippedBytes == fileInfo.getOffset();
            } else {
                logger.warn("{} file have current offset greater than file size", fileInfo.getName(), new Throwable());
                logger.debug(fileInfo.toString());
            }
        } else {
            sessionContext.setState(SessionState.STATE_WAITGET);
            fileInfo = exchanger.exchange(fileInfo);
            System.out.println(fileInfo);
        }
        final byte[] buf = new byte[FILE_BLOCK_SIZE];
        int read = reader.read(buf);
        long fileSize = 0;
        while (read != -1) {
            if (data.getInfo().isShouldSkip()) {
                return false;
            }
            final DataBulk dataBulk = new DataBulk(buf, read);
            session.write(dataBulk.getRawData());
            fileSize += read;
            data.getInfo().setCurSize(fileSize);
            read = reader.read(buf);
        }
        return true;
    }

    @Override
    public void run() {

        sessionContext.setState(SessionState.STATE_TRANSFER);
        try {
            while (!files.isEmpty()) {
                final Command file = new FILECommand();
                file.send(session, sessionContext);
                curFile = files.poll();
                try {
                    send(curFile);
                } catch (Exception e) {
                    logger.error("Unable to sent file {}", curFile.getInfo().toString());
                }
            }
            final Command eob = new EOBCommand();
            eob.send(session, sessionContext);
            sessionContext.setSendingIsFinish(true);
        } catch (Exception e) {
            logger.error("Unable to send files", e);
        }

    }

    private FileData<InputStream> findFile(FileInfo info) {
        for (FileData<InputStream> fileData : files) {
            if (fileData.getInfo().equals(info)) {
                return fileData;
            }
        }
        return null;
    }

    public synchronized void skip(FileInfo fileInfo, boolean isResend) throws IOException {
        if (curFile.getInfo().equals(fileInfo)) {
            curFile.getInfo().setSkip(true);
            if (isResend) {
                curFile.getStream().reset();
                files.addFirst(new FileData<InputStream>(fileInfo, curFile.getStream()));
            }
        } else {
            FileData<InputStream> fileData = findFile(fileInfo);
            if (fileData != null) {
                fileData.getInfo().setSkip(true);
                if (isResend) {
                    fileData.getStream().reset();
                    files.addLast(new FileData<InputStream>(fileInfo, fileData.getStream()));
                }
            } else {
                logger.warn("File is not found the the queue {}", fileInfo.toString());
            }
        }
    }

    public Exchanger<FileInfo> getExchanger() {
        return exchanger;
    }

}
