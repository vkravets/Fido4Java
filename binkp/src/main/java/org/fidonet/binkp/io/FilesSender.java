package org.fidonet.binkp.io;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.codec.DataBulk;
import org.fidonet.binkp.commands.EOBCommand;
import org.fidonet.binkp.commands.FILECommand;
import org.fidonet.binkp.commands.share.Command;

import java.io.DataInputStream;
import java.util.Deque;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 11:57 AM
 */
public class FilesSender implements Runnable {

    private Deque<FileData> files;
    private IoSession session;
    private static final int FILE_BLOCK_SIZE = 4096;
    private SessionContext sessionContext;

    public FilesSender(IoSession session, Deque<FileData> files, SessionContext sessionContext) {
        this.files = files;
        this.session = session;
        this.sessionContext = sessionContext;
    }

    public void send(FileData data) throws Exception {
        DataInputStream reader = new DataInputStream(data.getStream());
        byte[] buf = new byte[FILE_BLOCK_SIZE];
        int read = reader.read(buf);
        long fileSize = 0;
        while (read != -1) {
            DataBulk dataBulk = new DataBulk(buf, read);
            session.write(dataBulk.getRawData());
            fileSize += read;
            data.getInfo().setCurSize(fileSize);
            read = reader.read(buf);
        }
    }

    @Override
    public void run() {

        sessionContext.setState(SessionState.STATE_TRANSFER);
        try {
            while (!files.isEmpty()) {
                Command file = new FILECommand();
                file.send(session, sessionContext);
                FileData filedata = files.poll();
                try {
                    send(filedata);
                } catch (Exception e) {
                    // todo logger
                }
            }

            Command eob = new EOBCommand();
            eob.send(session, sessionContext);
            sessionContext.setSendingIsFinish(true);
        } catch (Exception e) {
            // todo logger
        }


    }
}
