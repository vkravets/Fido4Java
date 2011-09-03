package org.fidonet.jftn.tosser;

import org.apache.log4j.Logger;
import org.fidonet.config.IConfig;
import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.echobase.jam.JAMEchoBase;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.event.HasEventBus;
import org.fidonet.misc.PktTemp;
import org.fidonet.misc.Zipper;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Tosser extends HasEventBus {

    private static Logger logger = Logger.getLogger(Tosser.class);

    private EchoMgr areamgr;
    private Pattern bunlderegex;
    private IConfig config;
    private Map<String, Link> links;

    public Tosser(IConfig config) {
        this.config = config;

        EchoList echoList = new EchoList(getArealistFile());

        echoList.load();
        this.links = getLinks(config.getValuesAsList("Link"));
        this.areamgr = new EchoMgr(new JAMEchoBase(echoList), echoList, getEchoPath());
        this.bunlderegex = Pattern.compile(".*\\.[STFWMstfwm][ouaherOUAHER][0-9A-Za-z]");
    }

    private Map<String, Link> getLinks(List<String> links) {
        Map<String, Link> result = new HashMap<String, Link>();
        for (String linkStr : links) {
            Link link = new Link(linkStr);
            result.put(link.getAddr().toString(), link);
        }
        return result;
    }

    private Link getLink(FTNAddr addr) {
        return links.get(addr.toString());
    }

    private String getArealistFile() {
        String areaList = config.getValue("AreaListFile", "areas");
        try {
            File areasFile = new File(areaList);
            if (!areasFile.exists()) {
                areasFile.createNewFile();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return areaList;
    }

    private String getEchoPath() {
        String echoPath = config.getValue("EchoPath");
        File echoPathDir = new File(echoPath);
        if (!echoPathDir.exists()) {
            logger.warn("Echo path is not exists. Will be created...");
            echoPathDir.mkdirs();
        }
        return echoPath;
    }

    private Integer isDeleteTossedFiles() {
        return Integer.valueOf(config.getValue("Deletetossed", "1"));
    }

    private String getTmpDir() {
        String sysTemp = System.getenv("TEMP");
        String tmpDir = config.getValue("Tmp");
        if (tmpDir == null) {
            if (sysTemp != null) {
                tmpDir = sysTemp + System.getProperty("file.separator") + "jtoss";
                File systemTemp = new File(tmpDir);
                if (!systemTemp.exists()) {
                    systemTemp.mkdirs();
                }
            }
        }
        if (tmpDir == null) {
            tmpDir = "temp";
        }
        File tmp = new File(tmpDir);
        if (!tmp.exists()) {
            logger.warn("Temp folder was not exists. Will be created...");
            tmp.mkdirs();
        }
        try {
            return tmp.getCanonicalPath() + System.getProperty("file.separator");
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return tmpDir + System.getProperty("file.separator");
        }
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
                if (isDeleteTossedFiles() != 0) {
                    file.delete();
                }
            }
        }
    }

    private void saveBad(PktTemp pkt) {
        File bad = new File(getTmpDir() + pkt.name.replace(".pkt", ".bad"));

        if (bad.exists()) return;

        try {
            bad.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        Link origlink = getLink(q.getOrigaddr());
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
        areamgr.addMessage(msg, getLink(msg.getUpLink()).getMyaddr());
//        msg.DumpHead();
        //return;
    }

    public EchoMgr getAreamgr() {
        return areamgr;
    }
}
