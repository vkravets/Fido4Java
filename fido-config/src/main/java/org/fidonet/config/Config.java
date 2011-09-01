package org.fidonet.config;


import org.apache.log4j.Logger;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.*;
import java.util.HashMap;

public class Config {

    private static Logger logger = Logger.getLogger(Config.class);

    private static String inbound = "";
    private static String tmpdir = "";
    private static String echopath = "";
    private static boolean valid = false;
    private static int debuglevel = 0;
    private static int deletetossed = 0;
    private static HashMap<String, Link> Links;
    private static FTNAddr Address;
    private static String arealistfile;
    private static String SysOp;


    public static void ReadConfig(String cfgfile) {
        valid = true;
        Links = new HashMap<String, Link>();

        FileReader fr;
        try {
            logger.debug("Reading " + cfgfile);
            fr = new FileReader(cfgfile);
        } catch (FileNotFoundException e) {
            logger.debug("Config not found! Exiting...");
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
                    logger.debug("Error in config string " + strnum);
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
        if (inbound.equals("")) {
            logger.error("Inbound is not set.");
            valid = false;
        }
        if (tmpdir.equals("")) {
            logger.error("Temp is not set.");
            valid = false;
        }
        if (echopath.equals("")) {
            logger.error("Echopath is not set!");
            valid = false;
        }
        if (Links.isEmpty()) {
            logger.error("Cant find any links!");
            valid = false;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("inbound: " + inbound);
            logger.debug("tmp: " + tmpdir);
            logger.debug("debuglevel: " + debuglevel);
            logger.debug("links: ");
            for (Link link : Links.values()) {
                logger.debug("    " + link);
            }
            logger.debug("echopath: " + echopath);
            logger.debug("deletetossed: " + deletetossed);

            logger.debug("address: " + Address);
            logger.debug("arealist: " + arealistfile);
            logger.debug("sysop: " + SysOp);
            logger.debug("Reading config file is finish.");
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
            if (!isDirExists(tmpdir)) {
                logger.warn("Temp directory is not found will be creating during work. Temp=" + tmpdir);
            }

        } else if ("debuglevel".equals(name)) {
            debuglevel = Integer.valueOf(value);
        } else if ("link".equals(name)) {
            Link link = new Link(value);
            Links.put(link.getAddr().toString(), link);
        } else if ("echopath".equals(name)) {
            echopath = value;
            if (!isDirExists(echopath)) {
                logger.warn("Echopath directory is not found will be creating during work. Echopath=" + echopath);
            }
        } else if ("deletetossed".equals(name)) {
            deletetossed = Integer.valueOf(value);
        } else if ("address".equals(name)) {
            Address = new FTNAddr(value);
        } else if ("arealist".equals(name)) {
            arealistfile = value;
        } else if ("sysop".endsWith(name)) {
            SysOp = value;
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

    public static int getDeletetossed() {
        return deletetossed;
    }

    public static FTNAddr getAddress() {
        return Address;
    }

    public static String getArealistfile() {
        if (arealistfile == null) arealistfile = "arealist";
        return arealistfile;
    }

    public static String getSysOp() {
        return SysOp;
    }
}
