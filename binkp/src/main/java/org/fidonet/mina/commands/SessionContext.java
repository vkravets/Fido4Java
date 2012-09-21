package org.fidonet.mina.commands;

import org.fidonet.binkp.StationConfig;
import org.fidonet.mina.io.FileInfo;
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
    // received size


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

    public SessionContext(StationConfig config, Link link) {
        this.stationConfig = config;
        this.link = link;
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
}
