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
                    String[] Area = str.split(" ");
                    EchoCfg echo = new EchoCfg();
                    echo.Name = Area[1];
                    echo.Path = Area[2];
                    for (int i = 3; i < Area.length - 1; i++) {
                        if (Area[i].equals("-b")) {
                            echo.Type = Area[i + 1];
                            i++;
                        } else if (Area[i].equals("-g")) {
                            echo.Group = Area[i + 1];
                            i++;
                        } else if (Area[i].equals("-a")) {
                            echo.AKA = new FTNAddr(Area[i + 1]);
                            i++;
                        } else if (!Area[i].startsWith("-")) {
                            echo.Link = new FTNAddr(Area[i]);
                        }
                    }
                    list.put(echo.Name, echo);
                }
            }

            fr.close();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
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
            out.println(echo.ListString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }


    }
}
