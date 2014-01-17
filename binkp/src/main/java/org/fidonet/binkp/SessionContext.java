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

package org.fidonet.binkp;

import org.fidonet.binkp.config.Password;
import org.fidonet.binkp.config.ServerRole;
import org.fidonet.binkp.config.StationConfig;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;
import org.fidonet.events.Event;
import org.fidonet.events.HasEventBus;
import org.fidonet.types.Link;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:49 PM
 */
public class SessionContext extends HasEventBus {

    public static final String SESSION_CONTEXT_KEY = SessionContext.class.getName() + ".CONTEXT";

    // send size
    private long sendMailSize;
    private long sendFilesSize;

    // received size
    private long recvMailSize;
    private long recvFilesSize;

    // config client/server
    private StationConfig stationConfig;

    // last error message
    private String lastErrorMessage;

    // current link
    private LinksInfo linksInfo;

    // received files number
    // queue received files
    private Deque<FileData<OutputStream>> recvFiles;
    private List<FileData<OutputStream>> notFinishedFiles;

    // send files number
    // queue send files
    private Deque<FileData<InputStream>> sendFiles;
    private SessionState state;
    private ServerRole serverRole;

    private boolean sendingIsFinish = false;
    private boolean receivingIsFinish = false;

    private boolean isSecureSession = false;
    private Password password;
    private String logMessage;

    private boolean nrMode = false;
    private boolean cryptMode = false;
    private boolean busy = false;

    public SessionContext(SessionContext context) {
        this(context.getStationConfig(), context.getLinksInfo());
    }

    public SessionContext(StationConfig config, LinksInfo linksInfo) {
        this.stationConfig = config;
        this.linksInfo = linksInfo;
        this.recvFiles = new LinkedBlockingDeque<FileData<OutputStream>>();
        this.sendFiles = new LinkedBlockingDeque<FileData<InputStream>>();
        this.notFinishedFiles = new ArrayList<FileData<OutputStream>>();
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public StationConfig getStationConfig() {
        return stationConfig;
    }

    public LinksInfo getLinksInfo() {
        return linksInfo;
    }

    public Deque<FileData<OutputStream>> getRecvFiles() {
        return recvFiles;
    }

    public Deque<FileData<InputStream>> getSendFiles() {
        return sendFiles;
    }

    public long getSendMailSize() {
        return sendMailSize;
    }

    public void setSendMailSize(long sendMailSize) {
        this.sendMailSize = sendMailSize;
    }

    public long getSendFilesSize() {
        return sendFilesSize;
    }

    public void setSendFilesSize(long sendFilesSize) {
        this.sendFilesSize = sendFilesSize;
    }

    public long getRecvMailSize() {
        return recvMailSize;
    }

    public void setRecvMailSize(long recvMailSize) {
        this.recvMailSize = recvMailSize;
    }

    public long getRecvFilesSize() {
        return recvFilesSize;
    }

    public void setRecvFilesSize(long recvFilesSize) {
        this.recvFilesSize = recvFilesSize;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public SessionState getState() {
        return state;
    }

    public ServerRole getServerRole() {
        return serverRole;
    }

    public void setServerRole(ServerRole serverRole) {
        this.serverRole = serverRole;
    }

    public void setSendingIsFinish(boolean sendingIsFinish) {
        this.sendingIsFinish = sendingIsFinish;
    }

    public boolean isSendingIsFinish() {
        return sendingIsFinish;
    }

    public boolean isReceivingIsFinish() {
        return receivingIsFinish;
    }

    public void setReceivingIsFinish(boolean receivingIsFinish) {
        this.receivingIsFinish = receivingIsFinish;
    }

    public boolean isSecureSession() {
        return isSecureSession;
    }

    public void setSecureSession(boolean secureSession) {
        isSecureSession = secureSession;
    }

    public Password getPassword() {
        if (password == null) {
            Link curLink = linksInfo.getCurLink();
            if (curLink != null) {
                password = new Password(curLink.getPass());
            } else {
                return null;
            }
        }
        return password;
    }

    public Password getPassword(Link link) {
        if (link != null) {
            return new Password(link.getPass());
        }
        return null;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public boolean isNRMode() {
        return nrMode;
    }

    public void setNRMode(boolean nrMode) {
        this.nrMode = nrMode;
    }

    public boolean isCryptMode() {
        return cryptMode;
    }

    public void setCryptMode(boolean cryptMode) {
        this.cryptMode = cryptMode;
    }

    public void sendEvent(Event event) {
        getEventBus().publish(event);
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public void setNotFinishedFiles(List<FileData<OutputStream>> files) {
        notFinishedFiles.addAll(files);
    }

    public FileData<OutputStream> getNotFinishedFileData(FileInfo info) {
        int pos = notFinishedFiles.indexOf(info);
        if (pos != -1) {
            return notFinishedFiles.get(pos);
        }
        return null;
    }
}
