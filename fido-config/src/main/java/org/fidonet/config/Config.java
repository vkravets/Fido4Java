package org.fidonet.config;


import org.apache.log4j.Logger;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.*;
import java.util.HashMap;
import java.util.Scanner;

public class Config {

    private Logger logger = Logger.getLogger(Config.class);

    private String inbound = "";
    private String tmpdir = "";
    private String echopath = "";
    private boolean valid = false;
    private int debuglevel = 0;
    private int deletetossed = 0;
    private HashMap<String, Link> Links;
    private FTNAddr Address;
    private String arealistfile;
    private String SysOp;


    public void ReadConfig(String cfgfile) {
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
                Scanner scanner = new Scanner(str);
                scanner.useDelimiter("=");
                if (scanner.hasNext()){
                    String name = scanner.next();
                    String value = scanner.next();
                    valid = setParam(name, value);
                    if (!valid) {
                        break;
                    }
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

    private boolean setParam(String name, String value) {
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

    private boolean isDirExists(String path) {
        File testdir = new File(path);
        return testdir.exists();

    }

    public String getInbound() {
        return inbound;
    }

    public String getTmpdir() {
        return tmpdir;
    }

    public boolean isValid() {
        return valid;
    }

    public int getDebuglevel() {
        return debuglevel;
    }

    public Link getLink(FTNAddr addr) {
        return Links.get(addr.toString());
    }

    public String getEchopath() {
        return echopath;
    }

    public int getDeletetossed() {
        return deletetossed;
    }

    public FTNAddr getAddress() {
        return Address;
    }

    public String getArealistfile() {
        if (arealistfile == null) arealistfile = "arealist";
        return arealistfile;
    }

    public String getSysOp() {
        return SysOp;
    }
}
