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

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 11:55 AM
 */
public class FileData<T> {

    private FileTransferInfo info;
    private T stream;
    private boolean rewindBeforeSend;
    private boolean resumeResponse;
    private SendResource sendResource;
    private volatile long transferStartedNanos;
    private volatile long transferOffset;

    public boolean startTransfer(long offset) {
        if (transferStartedNanos != 0) {
            return false;
        }
        transferOffset = offset;
        transferStartedNanos = System.nanoTime();
        return true;
    }

    public long getTransferMillis() {
        return transferStartedNanos == 0
                ? 0
                : Math.max(1, (System.nanoTime() - transferStartedNanos) / 1_000_000);
    }

    public long getTransferredBytes() {
        return Math.max(0, info.getCurSize() - transferOffset);
    }

    public interface SendResource extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    public SendResource getSendResource() {
        return sendResource;
    }

    public void setSendResource(SendResource resource) {
        sendResource = resource;
    }

    public FileData<T> resend(FileTransferInfo requested) {
        FileData<T> resend = new FileData<>(requested, stream);
        resend.sendResource = sendResource;
        resend.setRewindBeforeSend(true);
        resend.setResumeResponse(true);
        return resend;
    }

    public boolean isResumeResponse() {
        return resumeResponse;
    }

    public void setResumeResponse(boolean value) {
        resumeResponse = value;
    }

    private boolean waitingForHeader;

    public boolean isWaitingForHeader() {
        return waitingForHeader;
    }

    public void setWaitingForHeader(boolean value) {
        waitingForHeader = value;
    }

    public boolean isRewindBeforeSend() {
        return rewindBeforeSend;
    }

    public void setRewindBeforeSend(boolean value) {
        rewindBeforeSend = value;
    }

    public FileData(FileTransferInfo info) {
        this.info = info;
    }

    public FileData(FileTransferInfo info, T stream) {
        this.info = info;
        this.stream = stream;
    }

    public FileTransferInfo getInfo() {
        return info;
    }

    public T getStream() {
        return stream;
    }

    public void setStream(T stream) {
        this.stream = stream;
    }
}
