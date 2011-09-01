package org.fidonet.echobase;

import org.apache.log4j.Logger;
import org.fidonet.types.FTNAddr;

import java.io.*;
import java.util.HashMap;

public class EchoList {

    private static Logger logger = Logger.getLogger(EchoList.class);

    private HashMap<String, EchoCfg> list;
    private String areaListFile;

    public void Load(String filename) {
        list = new HashMap<String, EchoCfg>();

        FileReader fr;
        try {
            fr = new FileReader(filename);
        } catch (FileNotFoundException e) {
            logger.error("Echolist not found!");
            return;
        }
        areaListFile = filename;

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

    public void addArea(String name, String Path, FTNAddr link, FTNAddr myAddr) {
        EchoCfg cfg = new EchoCfg();
        cfg.Name = name;
        cfg.Path = Path + name;
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
        } catch (IOException e) {
            e.printStackTrace();
        }

        PrintWriter out = new PrintWriter(fw);
        out.println(echo.ListString());
        out.close();

        try {
            if (fw != null) fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
