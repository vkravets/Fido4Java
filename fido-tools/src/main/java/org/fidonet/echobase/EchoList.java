package org.fidonet.echobase;

import org.apache.log4j.Logger;
import org.fidonet.types.FTNAddr;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EchoList {

    private static Logger logger = Logger.getLogger(EchoList.class);

    private HashMap<String, EchoCfg> list;
    private String areaListFile;

    public EchoList(String areaListFile) {
        this.areaListFile = areaListFile;
    }

    public void load(String filename) {
        this.areaListFile = filename;
        this.load();
    }

    public void load() {
        list = new HashMap<String, EchoCfg>();

        FileReader fr;
        try {
            fr = new FileReader(areaListFile);
        } catch (FileNotFoundException e) {
            // TODO throw exception
            logger.error("Echolist not found!");
            return;
        }

        try {
            String str;
            final BufferedReader file = new BufferedReader(fr);
            while ((str = file.readLine()) != null) {
                if (str.contains("#")) {
                    str = str.substring(0, str.indexOf('#'));
                }
                if (str.length() == 0) {
                    continue;
                }
                if (str.toLowerCase().startsWith("echoarea")) {
                    String[] area = str.split(" ");
                    EchoCfg echo = new EchoCfg();
                    echo.Name = area[1];
                    echo.Path = area[2];
                    for (int i = 3; i < area.length - 1; i++) {
                        if (area[i].equals("-b")) {
                            echo.Type = area[i + 1];
                            i++;
                        } else if (area[i].equals("-g")) {
                            echo.Group = area[i + 1];
                            i++;
                        } else if (area[i].equals("-a")) {
                            echo.AKA = new FTNAddr(area[i + 1]);
                            i++;
                        } else if (!area[i].startsWith("-")) {
                            echo.Link = new FTNAddr(area[i]);
                        }
                    }
                    list.put(echo.Name, echo);
                }
            }

            fr.close();
            file.close();
        } catch (IOException e) {
            // TODO logger
            // TODO throw exception
        }

    }

    public boolean isInList(String name) {
        return list.containsKey(name);
    }

    public EchoCfg getEcho(String name) {
        return list.get(name);
    }

    public List<String> getEchoList () {
        return new ArrayList<String>(list.keySet());
    }

    public void addArea(String name, String Path, FTNAddr link, FTNAddr myAddr) {
        EchoCfg cfg = new EchoCfg();
        cfg.Name = name;
        cfg.Path = Path + System.getProperty("file.separator") + name;
        cfg.Type = "JAM";
        cfg.AKA = myAddr; //Config.getAddress();
        cfg.Link = link;
        list.put(name, cfg);
        saveToList(cfg);
    }

    private void saveToList(EchoCfg echo) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(areaListFile, true);
            PrintWriter out = new PrintWriter(fw);
            out.println(echo.getEchoString());
            out.close();
        } catch (IOException e) {
            // TODO logger
            // TODO throw exception
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    // TODO throw exception
                }
            }
        }


    }
}
