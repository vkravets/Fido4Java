package org.fidonet.echobase;

import org.fidonet.config.Config;
import org.fidonet.misc.Logger;
import org.fidonet.types.FTNAddr;

import java.io.*;
import java.util.HashMap;

public class EchoList {

    private static HashMap<String, EchoCfg> list;

    public static void Load(String filename) {

        list = new HashMap<String, EchoCfg>();

        FileReader fr;
        try {
            fr = new FileReader(filename);
        } catch (FileNotFoundException e) {
            Logger.Log("Echolist not found!");
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

    public static boolean isInList(String name) {
        return list.containsKey(name);
    }

    public static EchoCfg getEcho(String name) {
        return list.get(name);
    }

    public static void addArea(String name, String Path, FTNAddr Uplink) {
        EchoCfg cfg = new EchoCfg();
        cfg.Name = name;
        cfg.Path = Path + name;
        cfg.Type = "JAM";
        cfg.AKA = Config.getLink(Uplink).getMyaddr(); //Config.getAddress();
        cfg.Link = Uplink;
        list.put(name, cfg);
        saveToList(cfg);
    }

    private static void saveToList(EchoCfg echo) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(Config.getArealistfile(), true);
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
