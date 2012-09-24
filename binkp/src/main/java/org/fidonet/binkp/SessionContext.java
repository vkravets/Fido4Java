package org.fidonet.binkp;

import org.fidonet.binkp.config.ServerRole;
import org.fidonet.binkp.config.StationConfig;
import org.fidonet.binkp.io.FileInfo;
import org.fidonet.binkp.config.Password;
import org.fidonet.types.Link;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:49 PM
 */
public class SessionContext {
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
    private Link link;

    // received files number
    // queue received files
    private Deque<FileInfo> recvFiles;

    // send files number
    // queue send files
    private Deque<FileInfo> sendFiles;
    private SessionState state;
    private ServerRole serverRole;

    private boolean sendingIsFinish = false;
    private boolean receivingIsFinish = false;

    private boolean isSecureSession = false;
    private Password password;

    public SessionContext(StationConfig config, Link link) {
        this.stationConfig = config;
        this.link = link;
        this.password = new Password(link.getPass());
        this.recvFiles = new LinkedBlockingDeque<FileInfo>();
        this.sendFiles = new LinkedBlockingDeque<FileInfo>();
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

    public Link getLink() {
        return link;
    }

    public Deque<FileInfo> getRecvFiles() {
        return recvFiles;
    }

    public Deque<FileInfo> getSendFiles() {
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
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }
}
