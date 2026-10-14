package org.fidonet.binkp.common;

import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.FilesSender;
import org.fidonet.binkp.common.protocol.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A session boundary that records protocol writes and never starts a transport. */
public class RecordingSession implements Session {
    public final List<BinkFrame> frames = Collections.synchronizedList(new ArrayList<>());
    public volatile boolean closed;
    private FilesSender<Session> sender;
    private TrafficCrypter crypter;

    @Override
    public void write(Object message) {
        BinkFrame frame = (BinkFrame) message;
        frames.add(new BinkFrame(frame.getDataInfo(), frame.getData().clone()));
    }

    @Override
    public void close(boolean immediate) {
        closed = true;
    }

    @Override
    public FilesSender<Session> getFileSender() {
        return sender;
    }

    @Override
    public void setFileSender(FilesSender sender) {
        this.sender = sender;
    }

    @Override
    public TrafficCrypter getTrafficCrypter() {
        return crypter;
    }

    @Override
    public void setTrafficCrypter(TrafficCrypter crypter) {
        this.crypter = crypter;
    }
}
