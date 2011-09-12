package org.fidonet.jftn.tosser;

import org.apache.log4j.Logger;
import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.echobase.jam.JAMEchoBase;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.event.HasEventBus;
import org.fidonet.misc.PktTemp;
import org.fidonet.misc.Zipper;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class Tosser extends HasEventBus {

    private static Logger logger = Logger.getLogger(Tosser.class);

    private EchoMgr areamgr;
    private Pattern bunlderegex;
    private JFtnConfig config;

    public Tosser(JFtnConfig config) {
        this.config = config;

        EchoList echoList = new EchoList(config.getArealistFile());

        echoList.load();
        this.areamgr = new EchoMgr(new JAMEchoBase(echoList), echoList, config.getEchoPath());
        this.bunlderegex = Pattern.compile(".*\\.[STFWMstfwm][ouaherOUAHER][0-9A-Za-z]");
    }

    private boolean isBunldeName(String str) {
        return bunlderegex.matcher(str).find();
    }

    public void runFast(String dirname) {
        if (dirname == null) {
            logger.error("Tosser.Run: Error opening directory as inbound. Dirname is null.");
            return;
        }
        final File dir = new File(dirname);
        final File[] files = dir.listFiles();
        if (files == null) {
            logger.error("Directory " + dirname + " not found!");
            return;
        }
        //TODO: We should make some checks!
        if (files.length != 0) {
            LinkedList<PktTemp> pktlist = null;
            for (File file : files) {
                if (file.isFile() && isBunldeName(file.getName())) {
                    try {
                        pktlist = Zipper.unpackboundlfast(file.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    continue;
                }
                if (pktlist != null) {
                    for (int i1 = 0; i1 < pktlist.size(); i1++) {
                        PktTemp aPktlist = pktlist.pop();
                        if (!tosspkt(aPktlist.pkt)) {
                            logger.info("Save to Tmp");
                            saveBad(aPktlist);
                        }
                    }
                }
                if (config.isDeleteTossedFiles() != 0) {
                    if (file.delete()) {
                        logger.error("Error while deleting tossed boundle!");
                    }
                }
            }
        }
    }

    private boolean saveBad(PktTemp pkt) {
        File bad = new File(config.getTmpDir() + pkt.name.replace(".pkt", ".bad"));
        boolean result = true;

        if (bad.exists()) return result;

        try {
            result = bad.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!result) return result;

        RandomAccessFile out = null;

        try {
            out = new RandomAccessFile(bad, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        pkt.pkt.position(0);
        try {
            if (out != null) {
                out.seek(0);
                out.write(pkt.pkt.array());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }


/*    private static void tosspkt(String filename) {
        Logger.Log("Tossing file " + filename);
        final FileInputStream ins;
        final File inf = new File(filename);

        try {
            ins = new FileInputStream(inf);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        final byte[] arr = new byte[(int) inf.length()];
        try {
            final int readed = ins.read(arr);
            if (readed < inf.length()) {
                System.out.println("Some problem while readin " + inf.getName());
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        final ftsPkt p = new ftsPkt(arr);
        final ftsPackMsg[] msgs = p.getMsgs();
        for (int i = 0; i < msgs.length; i++) {
            ftsPackMsg msg1 = msgs[i];
            final Message msg = new Message(msg1);
            if (msg.isEchomail()) {
                ProcessEchoMail(msg);
            } else {
                Logger.Log("Netmail???");
            }
        }
        try {
            ins.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        inf.delete();
    }*/

    private boolean tosspkt(ByteBuffer buf) {
        final FtsPkt q = new FtsPkt(buf);
        Link origlink = config.getLink(q.getOrigaddr());
        if (origlink == null) {
            logger.error("Unknown Link! Drop it.");
            return false;
        }
        if (!q.getPass().equals("") && !q.getPass().equals(origlink.getPass())) {
            logger.error("Bad PASSWORD! Drop it.");
            return false;
        }

        final FtsPackMsg[] msgs = q.getMsgs();
        for (FtsPackMsg msg1 : msgs) {
            final Message msg = new Message(msg1);
            if (msg.isEchomail()) {
                processEchoMail(msg);
                // Call onTossEchoMsg hook
                getEventBus().notify(new TossEchoMailEvent(msg));
            } else {
                // Call onTossNetMail hook
                getEventBus().notify(new TossNetmailEvent(msg));
                logger.debug("Netmail was found");
            }
        }
        return true;
    }

    private void processEchoMail(Message msg) {
        areamgr.addMessage(msg, config.getLink(msg.getUpLink()).getMyaddr());
//        msg.DumpHead();
        //return;
    }

    public EchoMgr getAreamgr() {
        return areamgr;
    }
}
