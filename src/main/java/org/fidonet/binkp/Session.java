package org.fidonet.binkp;

import org.fidonet.Config.Config;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 16.02.11
 * Time: 15:36
 */
class Session implements Runnable {

    private Socket sock = null;
    private InputStream instream = null;
    private OutputStream outstream = null;

    private Link curlink = null;

    private boolean active = false;

    private int state = 0;

    private SessFile currentfile = null;

    private final Vector<SessFile> resfiles = new Vector<SessFile>();

    private final SessionResult result = new SessionResult();

    Session(Socket cleintsocket, Link link) {
        sock = cleintsocket;
        curlink = link;
    }

    byte[] DoCommand(byte ctype, String str) {
        ByteBuffer bb = ByteBuffer.allocate(2 + str.length());
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(ctype);
        bb.put(str.getBytes());
        return bb.array();
    }


    private void SendIdent() throws IOException {
        Frame f = new Frame();
        String BBSName = "SYS jftn bbs";
        byte[] bbsname = DoCommand((byte) 0, BBSName);
        f.setType(Frame.TYPE_COMMAND);
        f.setData(bbsname);
        outstream.write(f.toByteArray());
        byte[] sysopname = DoCommand((byte) 0, "ZYZ " + Config.getSysOp());
        f.setData(sysopname);
        outstream.write(f.toByteArray());
        String location = "LOC internet";
        byte[] locat = DoCommand((byte) 0, location);
        f.setData(locat);
        outstream.write(f.toByteArray());
        String VER = "VER jftn/0.0.0/Linux binkp/0.8";
        byte[] ver = DoCommand((byte) 0, VER);
        f.setData(ver);
        outstream.write(f.toByteArray());
        byte[] addr = DoCommand((byte) 1, curlink.getMyaddr().toString());
        f.setData(addr);
        outstream.write(f.toByteArray());
    }

    public void run() {
        try {
            instream = sock.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            outstream = sock.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            SendIdent();
        } catch (IOException e) {
            e.printStackTrace();
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
                    e.printStackTrace();
                }
                if (readed != avail) {
                    System.out.println("Something bad happened!");
                }
                parsebuf(inbuf);
            }
        }
    }

    void end() {
        active = false;
    }

    void parsebuf(byte[] buff) {
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
            StateMachine(f.parse());
        }
        SendEOB();
    }

    void SendPassword() {
        Frame f = new Frame();
        f.setType(Frame.TYPE_COMMAND);
        byte[] pa = DoCommand(Frame.M_PWD, curlink.getPass());
        f.setData(pa);
        try {
            outstream.write(f.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void StateMachine(Block block) {
        int FILE_RECV = 1;
        if (block.type == 0) {
            System.out.println("> " + new String(block.value));
        } else if (block.type == Frame.M_ADR) {
            String addr = new String(block.value);
            FTNAddr remote = new FTNAddr(addr);
            System.out.println("Remote addr: " + remote.toString());
            if (curlink.getAddr().isEquals(remote))
                SendPassword();
        } else if (block.type == Frame.M_PWD) {
            String pass = new String(block.value);
            System.out.println("Remote password: " + pass);
        } else if (block.type == Frame.M_EOB) {
            end();
            result.setStatus(resfiles.size());
        } else if (block.type == Frame.M_FILE) {
            System.out.println("recv file!");
            String s = new String(block.value);
            String[] z = s.split(" ");
            currentfile = new SessFile(z[0], Integer.valueOf(z[1]), Integer.valueOf(z[2]));
            state = FILE_RECV;
        } else if (block.type == 666) {
            if (state == FILE_RECV) {
                System.out.println("data block");
                currentfile.append(block.value);
                if (currentfile.length == currentfile.pos) {
                    state = 0;
                    SendAck(currentfile.filename + " " + currentfile.pos + " " + currentfile.time);
                    resfiles.add(currentfile);
                    currentfile = null;
                }
            } else System.out.println("Skip block. Wrong state!");
        }

    }

    void SendAck(String t) {
        Frame f = new Frame();
        f.setType(Frame.TYPE_COMMAND);
        byte[] pa = DoCommand(Frame.M_GOT, t);
        f.setData(pa);
        try {
            outstream.write(f.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void SendOk() {
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

    void SendEOB() {
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


    public SessionResult getResult() {
        if (resfiles != null) {
            result.files = new SessFile[resfiles.size()];
            result.files = resfiles.toArray(result.files);
        }
        return result;
    }
}
