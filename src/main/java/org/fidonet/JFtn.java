package org.fidonet;

import org.fidonet.config.Config;
import org.fidonet.protocol.binkp.*;
import org.fidonet.misc.Logger;
import org.fidonet.types.FTNAddr;

import java.io.*;

public class JFtn {

    static void Help() {
        System.out.println("java ftn usage:");
        System.out.println("jftn <action>:");
        System.out.println("    help - show this help");
        System.out.println("    toss - toss incoming echomail");
    }

    public static void main(String[] args) throws IOException {

        final long starttime = System.currentTimeMillis();
        Config.ReadConfig("jftn.conf");
        if (!Config.isValid()) {
            return;
        }

        if (args.length == 0) {
            System.out.println("Error: No action in commandline.");
            Help();
        } else {
            if (args[0].equalsIgnoreCase("toss")) {
                Tosser.RunFast(Config.getInbound());
            } else if (args[0].equalsIgnoreCase("help")) {
                Help();
            } else if (args[0].equalsIgnoreCase("poll")) {
                FTNAddr boss = new FTNAddr("2:5030/1111.0");
                BinkP bink = new BinkP();
                SessionResult res = bink.Poll(Config.getLink(boss));
                SessFile[] files = res.getFiles();
                for (int i = 0; i < res.getStatus(); i++) {
                    RandomAccessFile of = null;
                    try {
                        of = new RandomAccessFile(Config.getInbound() + "/" + files[i].filename, "rw");
                        if (files[i].body != null) {
                                    of.write(files[i].body);
                        }
                        of.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (of != null) {
                            of.close();
                        }
                    }
                }

                System.out.println("Session " + res.getStatus());
            } else {
                System.out.println("Error: Unknown commandline argument.");
                Help();
            }
        }

        Logger.Log("Work time: " + (System.currentTimeMillis() - starttime) / 1000.0 + " sec");
    }
}
