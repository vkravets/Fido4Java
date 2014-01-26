/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.jftn.tosser;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.echobase.IBase;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.events.HasEventBus;
import org.fidonet.fts.FtsPackMsg;
import org.fidonet.fts.FtsPkt;
import org.fidonet.jftn.tosser.exception.TosserException;
import org.fidonet.misc.PktTemp;
import org.fidonet.misc.Zipper;
import org.fidonet.types.Link;
import org.fidonet.types.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class Tosser extends HasEventBus {

    private static final Logger logger = LoggerFactory.getLogger(Tosser.class.getName());

    private EchoMgr areamgr;
    private Pattern bunlderegex;
    private JFtnConfig config;

    public Tosser(JFtnConfig config, IBase base) throws IOException {
        this.config = config;
        this.areamgr = new EchoMgr(base);
        this.bunlderegex = Pattern.compile(".*\\.[STFWMstfwm][ouaherOUAHER][0-9A-Za-z]");
    }

    private boolean isBunldeName(String str) {
        return bunlderegex.matcher(str).find();
    }

    public void runFast(String dirname) throws TosserException, IOException, EchoBaseException {
        if (dirname == null) {
            throw new TosserException("Error opening directory as inbound. Dirname is null.");
        }
        final File dir = new File(dirname);
        final File[] files = dir.listFiles();
        if (files == null) {
            throw new TosserException("Directory " + dirname + " not found!");
        }
        //TODO: We should make some checks!
        if (files.length != 0) {
            for (File file : files) {
                if (file.isFile() && isBunldeName(file.getName())) {
                    try {
                        LinkedList<PktTemp> pktlist = Zipper.unpackboundlfast(file.getAbsolutePath());
                        while (!pktlist.isEmpty()) {
                            PktTemp aPktlist = pktlist.pop();
                            if (!tosspkt(aPktlist.getPkt())) {
                                logger.info("Save to Tmp");
                                saveBad(aPktlist);
                            }
                        }
                        if (config.isDeleteTossedFiles() != 0) {
                            if (file.delete()) {
                                logger.error("Error while deleting tossed boundle!");
                            }
                        }
                    } catch (IOException e) {
                        logger.error(String.format("Failed to unpack %s. Details: %s", file.getAbsolutePath(), e.getMessage()), e);
                    } catch (TosserException e) {
                        logger.error(String.format("Failed to toss %s. Details: %s", file.getAbsolutePath(), e.getMessage()), e);
                    }
                }
            }
        }
    }

    private void saveBad(PktTemp pkt) throws IOException, TosserException {
        File bad = new File(config.getTmpDir() + pkt.getName().replace(".pkt", ".bad"));

        if (bad.exists()) {
            logger.warn("Bad " + pkt.getName() + " already exists. ");
            // TODO: need to throw exception?
            return;
        }
        if (!bad.createNewFile()) {
            throw new TosserException("Creating bad packet was failed.");
        }

        RandomAccessFile out = new RandomAccessFile(bad, "rw");

        pkt.getPkt().position(0);
        out.seek(0);
        out.write(pkt.getPkt().array());
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

    private boolean tosspkt(ByteBuffer buf) throws IOException, EchoBaseException {
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
                getEventBus().publish(new TossEchoMailEvent(msg));
            } else {
                // Call onTossNetMail hook
                getEventBus().publish(new TossNetmailEvent(msg));
                logger.debug("Netmail was found");
            }
        }
        return true;
    }

    private void processEchoMail(Message msg) throws IOException, EchoBaseException {
        areamgr.addMessage(msg);
//        msg.dumpHead();
        //return;
    }

    public EchoMgr getAreamgr() {
        return areamgr;
    }
}
