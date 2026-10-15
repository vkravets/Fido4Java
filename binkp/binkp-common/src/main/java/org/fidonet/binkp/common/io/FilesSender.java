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
import org.fidonet.binkp.common.commands.ERRCommand;
import org.fidonet.binkp.common.commands.FILECommand;
import org.fidonet.binkp.common.commands.share.Command;
import org.fidonet.binkp.common.protocol.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
    protected volatile FileData<InputStream> curFile;
    private final BlockingQueue<FileInfo> requests = new LinkedBlockingQueue<>();
    private volatile boolean awaitingOffset;
    private volatile boolean cancelled;

    public FilesSender(T session,
                       Deque<FileData<InputStream>> files,
                       SessionContext sessionContext) {
        this.files = files;
        this.session = session;
        this.sessionContext = sessionContext;
        sessionContext.setActiveSender(this);
    }

    public boolean send(FileData<InputStream> data) throws Exception {
        // The caller owns this stream; GET may reset and reuse it for a resend.
        final InputStream reader = data.getStream();
        FileInfo fileInfo = data.getInfo();
        curFile = data;
        if (data.isRewindBeforeSend()) {
            if (reader instanceof FileInputStream) {
                ((FileInputStream) reader).getChannel().position(0);
            } else if (reader.markSupported()) {
                reader.reset();
            } else {
                throw new IOException("Cannot seek input stream for retransmission");
            }
        } else if (reader.markSupported()) {
            reader.mark(Integer.MAX_VALUE);
        }
        if (fileInfo.getOffset() < 0) {
            awaitingOffset = true;
            sessionContext.setState(SessionState.STATE_WAITGET);
            FileInfo requested = requests.poll(300, TimeUnit.SECONDS);
            awaitingOffset = false;
            if (cancelled || fileInfo.isShouldSkip() || fileInfo.isFinished()) {
                return false;
            }
            if (requested == null) {
                throw new IOException("Timed out waiting for M_GET");
            }
            if (!fileInfo.equals(requested)) {
                throw new IOException("GET does not match the offered file");
            }
            fileInfo.setOffset(requested.getOffset());
            session.write(new FILECommand().getData(fileInfo.arguments()
                                                    + " "
                                                    + fileInfo.getOffset()));
        }
        long offset = fileInfo.getOffset();
        if (offset < 0 || offset > fileInfo.getSize()) {
            throw new IOException("Invalid file offset: " + offset);
        }
        skipFully(reader, offset);
        sessionContext.setState(SessionState.STATE_TRANSFER);
        final byte[] buf = new byte[FILE_BLOCK_SIZE];
        int read = reader.read(buf);
        long fileSize = offset;
        data.getInfo().setCurSize(fileSize);
        while (read != -1) {
            if (cancelled || data.getInfo().isShouldSkip() || data.getInfo().isFinished()) {
                return false;
            }
            final DataBulk dataBulk = new DataBulk(buf, read);
            if (read > fileInfo.getSize() - fileSize) {
                throw new IOException("Input exceeds advertised file size");
            }
            session.write(dataBulk.getRawData());
            fileSize += read;
            data.getInfo().setCurSize(fileSize);
            read = reader.read(buf);
        }
        if (fileSize != fileInfo.getSize()) {
            throw new EOFException("Input ends before advertised file size");
        }
        return true;
    }

    private void skipFully(InputStream reader, long offset) throws IOException {
        long remaining = offset;
        while (remaining > 0) {
            long skipped = reader.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                if (reader.read() == -1) {
                    throw new EOFException("File ends before requested offset");
                }
                remaining--;
            }
        }
    }

    @Override
    public void run() {
        synchronized (sessionContext) {
            if (cancelled || sessionContext.getState() == SessionState.STATE_ERR
                    || sessionContext.getState() == SessionState.STATE_END) {
                return;
            }
            sessionContext.setState(SessionState.STATE_TRANSFER);
        }
        try {
            sessionContext.loadOutbound();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300);
            while (!files.isEmpty() || !sessionContext.getPendingFiles().isEmpty()) {
                if (cancelled || sessionContext.getState() == SessionState.STATE_ERR
                        || sessionContext.getState() == SessionState.STATE_END) {
                    return;
                }
                if (files.isEmpty()) {
                    waitForAcknowledgement(deadline);
                    continue;
                }
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300);
                prepareCurrentFile();
                if (!sendCurrentFileSafely()) {
                    return;
                }
            }
            final Command eob = new EOBCommand();
            synchronized (sessionContext) {
                eob.send(session, sessionContext);
                sessionContext.setSendingIsFinish(true);
                sessionContext.finishSessionIfComplete(session);
            }
        } catch (Exception e) {
            logger.error("Unable to send files", e);
            sessionContext.setState(SessionState.STATE_ERR);
            sessionContext.setLastErrorMessage(e.getMessage());
            sendErrorAndClose();
        }
    }

    private void prepareCurrentFile() throws Exception {
        curFile = files.peek();
        if (curFile.getStream().markSupported() && !curFile.isRewindBeforeSend()) {
            curFile.getStream().mark(Integer.MAX_VALUE);
        }
        if (sessionContext.isNRMode() && curFile.getInfo().getOffset() == 0
                && !curFile.isResumeResponse()) {
            curFile.getInfo().setOffset(-1);
        }
        awaitingOffset = curFile.getInfo().getOffset() < 0;
        sessionContext.getPendingFiles().put(curFile.getInfo(), curFile);
        new FILECommand().send(session, sessionContext);
        files.poll();
    }

    private boolean sendCurrentFileSafely() {
        try {
            send(curFile);
            return true;
        } catch (Exception e) {
            logger.error("Unable to send file {}", curFile.getInfo(), e);
            sessionContext.setLastErrorMessage(e.getMessage());
            sessionContext.setState(SessionState.STATE_ERR);
            sendErrorAndClose();
            return false;
        }
    }

    private void sendErrorAndClose() {
        try {
            new ERRCommand().send(session, sessionContext);
        } catch (Exception ignored) {
        }
        session.close(false);
    }

    private void waitForAcknowledgement(long deadline) throws IOException, InterruptedException {
        if (System.nanoTime() > deadline) {
            throw new IOException("Timed out waiting for file acknowledgement");
        }
        synchronized (sessionContext) {
            sessionContext.wait(100);
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
        FileData<InputStream> data = sessionContext.getPendingFiles().get(fileInfo);
        if (data == null && curFile != null && curFile.getInfo().equals(fileInfo)) {
            data = curFile;
        }
        if (data == null) {
            data = findFile(fileInfo);
        }
        if (data == null) {
            return;
        }
        if (isResend
                && (fileInfo.getOffset() < 0 || fileInfo.getOffset() > data.getInfo().getSize())) {
            throw new IOException("Invalid resume offset");
        }
        data.getInfo().setSkip(true);
        if (awaitingOffset && data == curFile) {
            requests.offer(fileInfo);
        }
        if (isResend) {
            FileData<InputStream> resend = new FileData<>(fileInfo, data.getStream());
            resend.setRewindBeforeSend(true);
            resend.setResumeResponse(true);
            files.addFirst(resend);
        }
        synchronized (sessionContext) {
            sessionContext.notifyAll();
        }
    }

    public void resume(FileInfo info) throws IOException {
        if (awaitingOffset && curFile != null && curFile.getInfo().equals(info)) {
            if (info.getOffset() < 0 || info.getOffset() > curFile.getInfo().getSize()) {
                throw new IOException("Invalid resume offset");
            }
            requests.offer(info);
        } else {
            skip(info, true);
        }
    }

    public void acknowledge(FileInfo info) {
        if (awaitingOffset && curFile != null && curFile.getInfo().equals(info)) {
            requests.offer(info);
        }
        synchronized (sessionContext) {
            sessionContext.notifyAll();
        }
    }

    public void cancel() {
        cancelled = true;
        requests.offer(new FileInfo("", 0, 0));
        synchronized (sessionContext) {
            sessionContext.notifyAll();
        }
    }
}
