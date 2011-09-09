package org.fidonet.protocol.binkp;

import org.apache.log4j.Logger;
import org.fidonet.config.IConfig;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 16.02.11
 * Time: 15:36
 */
class Session /*implements Runnable*/ {

    private Logger logger = Logger.getLogger(Session.class);

    private Socket sock = null;
    private InputStream instream = null;
    private OutputStream outstream = null;

    private Link curlink = null;

    private boolean active = false;

    private int state = 0;

    private SessFile currentfile = null;

    private int LastError = -1;

    private IConfig config;

    public Session(Socket cleintsocket, Link link, IConfig config) {
        this.sock = cleintsocket;
        this.curlink = link;
        this.config = config;
    }

    public byte[] doCommand(byte ctype, String str) {
        ByteBuffer bb = ByteBuffer.allocate(2 + str.length());
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(ctype);
        bb.put(str.getBytes());
        return bb.array();
    }


    private void sendIdentification() throws IOException {
        Frame f = new Frame();
        String BBSName = "SYS "+config.getValue("bbsname");
        byte[] bbsname = doCommand((byte) 0, BBSName);
        f.setType(Frame.TYPE_COMMAND);
        f.setData(bbsname);
        outstream.write(f.toByteArray());
        byte[] sysopname = doCommand((byte) 0, "ZYZ " + config.getValue("sysop"));
        f.setData(sysopname);
        outstream.write(f.toByteArray());
        String location = "LOC "+config.getValue("bbsloc");
        byte[] locat = doCommand((byte) 0, location);
        f.setData(locat);
        outstream.write(f.toByteArray());
        String VER = "VER jftn/0.0.0/Linux jbinkp/0.8";
        byte[] ver = doCommand((byte) 0, VER);
        f.setData(ver);
        outstream.write(f.toByteArray());
        byte[] addr = doCommand((byte) 1, curlink.getMyaddr().toString());
        f.setData(addr);
        outstream.write(f.toByteArray());
    }

    public int run() {
        try {
            instream = sock.getInputStream();
            outstream = sock.getOutputStream();
            sendIdentification();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        active = true;
        while (active) {
            int avail = 0;
            int readed = 0;
            try {
                avail = instream.available();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (avail != 0) {
                byte[] inbuf = new byte[avail];
                try {
                    readed = instream.read(inbuf);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                if (readed != avail) {
                    logger.debug("Something bad happened!");
                }
                parsebuf(inbuf);
            }
        }
        return LastError;
    }

    void end(int x) {
        LastError = x;
        active = false;
    }

    private void parsebuf(byte[] buff) {
        ByteBuffer b = ByteBuffer.allocate(buff.length);
        b.put(buff);
        b.position(0);
        Frame f = new Frame();

        while (b.position() < b.capacity()) {
            short head = b.getShort();
            short len;

            if (head < 0) {
                f.setType(Frame.TYPE_COMMAND);
                len = (short) (head + 32768);
            } else {
                f.setType(Frame.TYPE_DATA);
                len = head;
            }

            byte[] n = new byte[len];
            b.get(n);
            f.setData(n);
            stateMachine(f.parse());
        }
        sendEOB();
    }

    private void sendPassword() {
        Frame f = new Frame();
        f.setType(Frame.TYPE_COMMAND);
        byte[] pa = doCommand(Frame.M_PWD, curlink.getPass());
        f.setData(pa);
        try {
            outstream.write(f.toByteArray());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void stateMachine(Block block) {
        int FILE_RECV = 1;
        if (block.type == 0) {
            logger.debug("> " + new String(block.value));
        } else if (block.type == Frame.M_ADR) {
            String addr = new String(block.value);
            FTNAddr remote = new FTNAddr(addr);
            logger.debug("Remote addr: " + remote.toString());
            if (curlink.getAddr().isEquals(remote))
                sendPassword();
        } else if (block.type == Frame.M_PWD) {
            String pass = new String(block.value);
            logger.debug("Remote password: " + pass);
        } else if (block.type == Frame.M_EOB) {
            end(0);
        } else if (block.type == Frame.M_FILE) {
            logger.debug("recv file!");
            String s = new String(block.value);
            String[] z = s.split(" ");
            currentfile = new SessFile(z[0], Integer.valueOf(z[1]), Integer.valueOf(z[2]));
            state = FILE_RECV;
        } else if (block.type == 666) {
            if (state == FILE_RECV) {
                logger.debug("data block");
                currentfile.append(block.value);
                if (currentfile.length == currentfile.pos) {
                    state = 0;
                    sendAck(currentfile.filename + " " + currentfile.pos + " " + currentfile.time);
                    saveFile(currentfile);
                    currentfile = null;
                }
            } else logger.warn("Skip block. Wrong state!");
        }

    }

    private void sendAck(String t) {
        Frame f = new Frame();
        f.setType(Frame.TYPE_COMMAND);
        byte[] pa = doCommand(Frame.M_GOT, t);
        f.setData(pa);
        try {
            outstream.write(f.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendOk() {
        byte[] okcmd = new byte[3];
        okcmd[0] = (byte) 128;
        okcmd[1] = 1;
        okcmd[2] = Frame.M_OK;
        try {
            outstream.write(okcmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendEOB() {
        byte[] okcmd = new byte[3];
        okcmd[0] = (byte) 128;
        okcmd[1] = 1;
        okcmd[2] = Frame.M_EOB;
        try {
            outstream.write(okcmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


//    public SessionResult getResult() {
//        if (resfiles != null) {
//            result.files = new SessFile[resfiles.size()];
//            result.files = resfiles.toArray(result.files);
//        }
//        return result;
//    }

    private void saveFile(SessFile f)
    {
        String inbound = config.getValue("inbound");
        FileOutputStream save = null;
        try {
            save = new FileOutputStream(inbound+"/"+f.filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if(save != null)
        {
            try {
                save.write(f.body);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}

