import Config.Config;
import binkp.*;
import com.sun.xml.internal.messaging.saaj.packaging.mime.util.OutputUtil;
import misc.Logger;
import types.FTNAddr;

import java.io.*;
import java.security.acl.Owner;

public class jftn {

    static void Help() {
        System.out.println("java ftn usage:");
        System.out.println("jftn <action>:");
        System.out.println("    help - show this help");
        System.out.println("    toss - toss incoming echomail");
    }

    public static void main(String[] args) {

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
                binkp bink = new binkp();
                SessionResult res = bink.Poll(Config.getLink(boss));
                SessFile[] files = res.getFiles();
                for (int i = 0; i < res.getStatus(); i++) {
                    RandomAccessFile of = null;
                    try {
                        of = new RandomAccessFile(Config.getInbound() + "/" + files[i].filename, "rw");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (files[i].body != null) {
                        try {
                            if (of != null) {
                                of.write(files[i].body);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        of.close();
                    } catch (IOException e) {
                        e.printStackTrace();
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
