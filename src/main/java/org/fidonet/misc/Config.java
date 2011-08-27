package org.fidonet.misc;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.*;
import java.util.HashMap;

public class Config {

    private static String inbound;
    private static String tmpdir;
    private static String echopath;
    private static boolean valid;
    private static int debuglevel;
    private static HashMap<String, Link> Links;


    public static void ReadConfig(String cfgfile) {
        valid = true;
        Links = new HashMap<String, Link>();

        FileReader fr;
        try {
            fr = new FileReader(cfgfile);
        } catch (FileNotFoundException e) {
            Logger.Log("Config not found! Exiting...");
            e.getMessage();
            return;
        }

        try {
            int strnum = 0;
            String str;
            final BufferedReader file = new BufferedReader(fr);
            while ((str = file.readLine()) != null) {
                strnum++;
                if (str.contains("#")) {
                    str = str.substring(0, str.indexOf('#'));
                }
                if (str.length() == 0) {
                    continue;
                }
                if (!str.contains("=")) {
                    Logger.Log("Error in config string " + strnum);
                    break;
                }
                valid = setParam(str.substring(0, str.indexOf('=')), str.substring(str.indexOf('=') + 1, str.length()));
                if (!valid) {
                    break;
                }
            }

            fr.close();
            file.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean setParam(String name, String value) {
        boolean paramisvalid = true;
        name = name.trim().toLowerCase();
        value = value.trim();
        if ("inbound".equals(name)) {
            inbound = value.trim();
        } else if ("tmp".equals(name)) {
            tmpdir = value.trim();
        } else if ("debuglevel".equals(name)) {
            debuglevel = Integer.valueOf(value);
        } else if ("link".equals(name)) {
            Link link = new Link(value);
            Links.put(link.getAddr().toString(), link);
        } else if ("echopath".equals(name)) {
            echopath = value;
            if (!isDirExists(echopath)) {
                Logger.Log("Bad echo directory:" + echopath);
                paramisvalid = false;
            }
        }
        return paramisvalid;
    }

    private static boolean isDirExists(String path) {
        File testdir = new File(path);
        return testdir.exists();

    }

    public static String getInbound() {
        return inbound;
    }

    public static String getTmpdir() {
        return tmpdir;
    }

    public static boolean isValid() {
        return valid;
    }

    public static int getDebuglevel() {
        return debuglevel;
    }

    public static Link getLink(FTNAddr addr) {
        return Links.get(addr.toString());
    }

    public static String getEchopath() {
        return echopath;
    }
}
