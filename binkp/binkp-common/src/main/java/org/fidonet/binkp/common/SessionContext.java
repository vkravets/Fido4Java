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

package org.fidonet.binkp.common;

import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.common.events.ConnectedEvent;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.events.FileEvent;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.events.FileSendEvent;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.common.io.FilesSender;
import org.fidonet.binkp.common.protocol.Session;
import org.fidonet.events.Event;
import org.fidonet.events.EventBus;
import org.fidonet.events.HasEventBus;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:49 PM
 */
public class SessionContext extends HasEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionContext.class);

    // send size
    private long sendMailSize;
    private long sendFilesSize;

    // received size
    private long recvMailSize;
    private long recvFilesSize;

    // config client/server
    private StationConfig stationConfig;
    private volatile Configuration configurationTemplate;

    private static final class Configuration {
        private final StationConfig station;
        private final LinksInfo links;

        private Configuration(StationConfig station, LinksInfo links) {
            this.station = station;
            this.links = links;
        }
    }

    private synchronized Configuration configurationSnapshot() {
        return configurationTemplate == null
                ? new Configuration(stationConfig, linksInfo)
                : configurationTemplate;
    }

    /** Updates the template for future sessions without modifying existing connections. */
    public synchronized void reconfigure(SessionContext configuration) {
        configurationTemplate = configuration.configurationSnapshot();
    }

    // last error message
    private volatile String lastErrorMessage;
    private volatile Throwable lastFailure;

    // current link
    private LinksInfo linksInfo;

    // received files number
    // queue received files
    private Deque<FileData<OutputStream>> recvFiles;
    private List<FileData<OutputStream>> notFinishedFiles;

    // send files number
    // queue send files
    private Deque<FileData<InputStream>> sendFiles;
    private final ConcurrentMap<FileInfo, FileData<InputStream>> pendingFiles = new ConcurrentHashMap<>();
    private final Deque<FileData<InputStream>> deferredFiles = new LinkedBlockingDeque<>();

    public Deque<FileData<InputStream>> getDeferredFiles() {
        return deferredFiles;
    }

    private volatile SessionState state;
    private ServerRole serverRole;

    private volatile boolean sendingIsFinish = false;
    private volatile boolean receivingIsFinish = false;

    private boolean isSecureSession = false;
    private Password password;
    private MessageDigest messageDigest;
    private String passwordKey;
    private String logMessage;

    private volatile boolean nrMode = false;
    private boolean cryptMode = false;
    private boolean busy = false;
    private EventBus receiveEventBus;
    private FilesSender<?> activeSender;
    private int remoteMajor = 1;
    private int remoteMinor;
    private boolean transferStarted;
    private boolean batchActivity;
    private int batchNumber;
    private Consumer<SessionContext> outboundLoader;
    private ReceivedFileHandler receivedFileHandler;
    private SentFileHandler sentFileHandler;
    private Consumer<SessionContext> sessionClosedHandler;
    private volatile SessionActivityListener activityListener;

    public void setActivityListener(SessionActivityListener listener) {
        activityListener = listener;
    }

    private void activity(SessionActivityListener.Kind kind, String name, long bytes) {
        SessionActivityListener listener = activityListener;
        if (listener == null) {
            return;
        }
        try {
            listener.record(kind, name, bytes);
        } catch (RuntimeException ignored) {
            // Diagnostics must not change protocol or file acknowledgement behavior.
        }
    }

    @FunctionalInterface
    public interface ReceivedFileHandler {
        void accept(SessionContext context, FileData<OutputStream> file) throws IOException;
    }

    @FunctionalInterface
    public interface SentFileHandler {
        void accept(SessionContext context, FileData<InputStream> file) throws IOException;
    }

    public void setReceivedFileHandler(ReceivedFileHandler handler) {
        receivedFileHandler = handler;
    }

    public void setSentFileHandler(SentFileHandler handler) {
        sentFileHandler = handler;
    }

    public void setSessionClosedHandler(Consumer<SessionContext> handler) {
        sessionClosedHandler = handler;
    }

    public void commitReceivedFile(FileData<OutputStream> file) throws IOException {
        if (receivedFileHandler != null) {
            receivedFileHandler.accept(this, file);
        }
        activity(SessionActivityListener.Kind.RECEIVED,
                 file.getInfo().getName(),
                 file.getInfo().getSize());
    }

    public void commitSentFile(FileData<InputStream> file) throws IOException {
        if (sentFileHandler != null) {
            sentFileHandler.accept(this, file);
        }
        activity(SessionActivityListener.Kind.SENT,
                 file.getInfo().getName(),
                 file.getInfo().getSize());
    }

    public synchronized void setRemoteProtocol(int major, int minor) {
        if (!transferStarted && major >= 1 && minor >= 0) {
            remoteMajor = major;
            remoteMinor = minor;
        }
    }

    public synchronized boolean isBinkp11() {
        return stationConfig.isBinkp11Enabled()
                && (remoteMajor > 1 || remoteMajor == 1 && remoteMinor >= 1);
    }

    public synchronized void beginTransfer() {
        transferStarted = true;
    }

    public synchronized void recordCommand(BinkCommand command) {
        if (transferStarted && command != BinkCommand.M_EOB && command != BinkCommand.M_OK
                && command != BinkCommand.M_PWD && command != BinkCommand.M_ADR) {
            batchActivity = true;
        }
    }

    public synchronized int getBatchNumber() {
        return batchNumber;
    }

    /** Runs on the sending worker before each batch; enqueue only new outgoing files. */
    public void setOutboundLoader(Consumer<SessionContext> loader) {
        outboundLoader = loader;
    }

    public void loadOutbound() {
        if (outboundLoader != null) {
            outboundLoader.accept(this);
        }
    }

    private void startNextBatch(Session session) {
        batchNumber++;
        batchActivity = false;
        sendingIsFinish = false;
        receivingIsFinish = false;
        FilesSender<Session> sender = new FilesSender<>(session, sendFiles, this);
        session.setFileSender(sender);
        new Thread(sender, "binkp-batch-" + batchNumber).start();
    }

    public void setActiveSender(FilesSender<?> sender) {
        activeSender = sender;
    }

    public void setReceiveEventBus(EventBus bus) {
        receiveEventBus = bus;
    }

    public void receiveEvent(Event event) {
        logEvent(event);
        (receiveEventBus == null ? getEventBus() : receiveEventBus).publish(event);
    }

    public void closeReceiveStreams() {
        if (sessionClosedHandler != null) {
            sessionClosedHandler.accept(this);
        }
        if (activeSender != null) {
            activeSender.cancel();
        }
        for (FileData<OutputStream> file : recvFiles) {
            try {
                file.getStream().close();
            } catch (IOException ignored) {
            }
        }
        for (FileData<OutputStream> file : notFinishedFiles) {
            try {
                file.getStream().close();
            } catch (IOException ignored) {
            }
        }
        recvFiles.clear();
        notFinishedFiles.clear();
        synchronized (this) {
            notifyAll();
        }
    }

    public void prepareTransfer() {
        for (FileData<InputStream> deferred : deferredFiles) {
            pendingFiles.putIfAbsent(deferred.getInfo(), deferred);
        }
        deferredFiles.clear();
        for (FileData<InputStream> pending : pendingFiles.values()) {
            pending.getInfo().setFinished(false);
            pending.getInfo().setSkip(false);
            pending.getInfo().setOffset(0);
            pending.getInfo().setCurSize(0);
            pending.setRewindBeforeSend(true);
            pending.setResumeResponse(false);
            if (!sendFiles.contains(pending)) {
                sendFiles.addFirst(pending);
            }
        }
        pendingFiles.clear();
        sendingIsFinish = false;
        receivingIsFinish = false;
        nrMode = false;
        cryptMode = false;
        password = null;
        passwordKey = null;
        messageDigest = null;
        remoteMajor = 1;
        remoteMinor = 0;
        transferStarted = false;
        batchActivity = false;
        batchNumber = 0;
    }

    public SessionContext(SessionContext context) {
        this(context, context.configurationSnapshot());
    }

    private SessionContext(SessionContext context, Configuration configuration) {
        this(configuration.station, new LinksInfo(new ArrayList<>(configuration.links.getLinks())));
        outboundLoader = context.outboundLoader;
        receivedFileHandler = context.receivedFileHandler;
        sentFileHandler = context.sentFileHandler;
        sessionClosedHandler = context.sessionClosedHandler;
        if (configuration.links.getCurLink() != null) {
            linksInfo.setCurLink(configuration.links.getCurLink().getAddr());
        }
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

    public Throwable getLastFailure() {
        return lastFailure;
    }

    /** Publishes failure details before the terminal state becomes visible to other threads. */
    public void fail(Throwable cause) {
        lastFailure = cause;
        setLastErrorMessage(cause == null ? null : cause.getMessage());
        setState(SessionState.STATE_ERR);
    }

    public StationConfig getStationConfig() {
        Configuration current = configurationTemplate;
        if (current != null)
            return current.station;
        return stationConfig;
    }

    public LinksInfo getLinksInfo() {
        Configuration current = configurationTemplate;
        if (current != null)
            return current.links;
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
        activity(SessionActivityListener.Kind.STATE, state.name(), 0);
    }

    public SessionState getState() {
        return state;
    }

    public ServerRole getServerRole() {
        return serverRole;
    }

    public void setServerRole(ServerRole serverRole) {
        this.serverRole = Objects.requireNonNull(serverRole,
                                                 "Session role must be CLIENT or SERVER");
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

    public ConcurrentMap<FileInfo, FileData<InputStream>> getPendingFiles() {
        return pendingFiles;
    }

    public boolean isTransferComplete() {
        return sendingIsFinish && receivingIsFinish && pendingFiles.isEmpty()
                && sendFiles.isEmpty();
    }

    public synchronized void finishSessionIfComplete(Session session) {
        boolean batchComplete = sendingIsFinish && receivingIsFinish && pendingFiles.isEmpty();
        if (batchComplete && state != SessionState.STATE_END && state != SessionState.STATE_ERR) {
            if (isBinkp11() && (batchActivity || !sendFiles.isEmpty())) {
                startNextBatch(session);
                return;
            }
            if (!sendFiles.isEmpty()) {
                return;
            }
            ServerRole role = Objects.requireNonNull(serverRole,
                                                     "Session role must be set before completing a transfer");
            setState(SessionState.STATE_END);
            sendEvent(new DisconnectedEvent(this));
            if (role == ServerRole.SERVER) {
                session.close(false);
            }
        }
    }

    public void setReceivingIsFinish(boolean receivingIsFinish) {
        this.receivingIsFinish = receivingIsFinish;
    }

    /** Retain partial files for later offers while ignoring data beyond the batch boundary. */
    public void endIncomingBatch() {
        FileData<OutputStream> file;
        while ((file = recvFiles.poll()) != null) {
            if (!file.getInfo().isFinished() && !notFinishedFiles.contains(file)) {
                notFinishedFiles.add(file);
            }
        }
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

    public MessageDigest getMessageDigest() {
        return messageDigest;
    }

    public void setMessageDigest(MessageDigest messageDigest) {
        this.messageDigest = messageDigest;
    }

    public String getPasswordKey() {
        return passwordKey;
    }

    public void setPasswordKey(String passwordKey) {
        this.passwordKey = passwordKey;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public boolean isNRMode() {
        return nrMode && stationConfig.isNREnabled();
    }

    public void setNRMode(boolean nrMode) {
        this.nrMode = nrMode && stationConfig.isNREnabled();
    }

    public boolean isCryptMode() {
        return cryptMode;
    }

    public void setCryptMode(boolean cryptMode) {
        this.cryptMode = cryptMode;
    }

    public void sendEvent(Event event) {
        logEvent(event);
        getEventBus().publish(event);
    }

    private void logEvent(Event event) {
        if (event instanceof ConnectedEvent) {
            LOGGER.info("binkp session ready: peer={}, role={}, NR={}",
                        logPeer(),
                        serverRole,
                        isNRMode());
        } else if (event instanceof DisconnectedEvent) {
            LOGGER.info("binkp transfer ended: peer={}, role={}, state={}",
                        logPeer(),
                        serverRole,
                        state);
        } else if (event instanceof FileReceivedEvent) {
            logFile("received", (FileEvent<?>) event);
        } else if (event instanceof FileSendEvent) {
            logFile("sent and acknowledged", (FileEvent<?>) event);
        }
    }

    private Object logPeer() {
        Link link = linksInfo == null ? null : linksInfo.getCurLink();
        return link == null ? "unidentified" : link.getAddr();
    }

    private void logFile(String action, FileEvent<?> event) {
        FileInfo info = event.getFile().getInfo();
        LOGGER.info("binkp file {}: peer={}, name={}, bytes={}",
                    action,
                    logPeer(),
                    info.getName(),
                    info.getSize());
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
        for (FileData<OutputStream> file : notFinishedFiles) {
            if (!file.getInfo().isFinished() && file.getInfo().equals(info)) {
                return file;
            }
        }
        for (FileData<OutputStream> file : recvFiles) {
            if (!file.getInfo().isFinished() && file.getInfo().equals(info)) {
                return file;
            }
        }
        return null;
    }
}
