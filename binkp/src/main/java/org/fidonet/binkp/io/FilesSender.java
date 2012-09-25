package org.fidonet.binkp.io;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.codec.DataBulk;
import org.fidonet.binkp.commands.EOBCommand;
import org.fidonet.binkp.commands.FILECommand;
import org.fidonet.binkp.commands.share.Command;

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
public class FilesSender implements Runnable {

    public static final String FILESENDER_KEY = FilesSender.class.getName() + ".KEY";

    private Deque<FileData<InputStream>> files;
    private IoSession session;
    private static final int FILE_BLOCK_SIZE = 4096;
    private SessionContext sessionContext;
    private FileData<InputStream> curFile;
    private Exchanger<FileInfo> exchanger;

    public FilesSender(IoSession session, Deque<FileData<InputStream>> files, SessionContext sessionContext) {
        this.files = files;
        this.session = session;
        this.sessionContext = sessionContext;
        exchanger = new Exchanger<FileInfo>();
    }

    public boolean send(FileData<InputStream> data) throws Exception {
        DataInputStream reader = new DataInputStream(data.getStream());
        FileInfo fileInfo = data.getInfo();
        if (fileInfo.getOffset() >= 0 ) {
            if (fileInfo.getOffset() <= fileInfo.getSize()) {
                reader.skip(fileInfo.getOffset());
            } else {
                // TODO logger
            }
        } else {
            sessionContext.setState(SessionState.STATE_WAITGET);
            fileInfo = exchanger.exchange(fileInfo);
            System.out.println(fileInfo);
        }
        byte[] buf = new byte[FILE_BLOCK_SIZE];
        int read = reader.read(buf);
        long fileSize = 0;
        while (read != -1) {
            if (data.getInfo().isShouldSkip()) {
                return false;
            }
            DataBulk dataBulk = new DataBulk(buf, read);
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
                Command file = new FILECommand();
                file.send(session, sessionContext);
                curFile = files.poll();
                boolean isSent = false;
                try {
                    isSent = send(curFile);
                } catch (Exception e) {
                    // todo logger
                }
                if (isSent) {
                    Command eob = new EOBCommand();
                    eob.send(session, sessionContext);
                    sessionContext.setSendingIsFinish(true);
                }
            }

        } catch (Exception e) {
            // todo logger
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
                // TODO logger
            }
        }
    }

    public Exchanger<FileInfo> getExchanger() {
        return exchanger;
    }
}
