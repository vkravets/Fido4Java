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

package org.fidonet.echobase;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;
import org.fidonet.types.FTNAddr;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EchoList {

    private static final ILogger logger = LoggerFactory.getLogger(EchoList.class.getName());

    private HashMap<String, EchoCfg> list;
    private String areaListFile;

    public EchoList(String areaListFile) {
        this.areaListFile = areaListFile;
    }

    public void load(String filename) throws IOException {
        this.areaListFile = filename;
        this.load();
    }

    public void load() throws IOException {
        list = new HashMap<String, EchoCfg>();

        FileReader fr;
        fr = new FileReader(areaListFile);
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

    }

    public boolean isInList(String name) {
        return list.containsKey(name);
    }

    public EchoCfg getEcho(String name) {
        return list.get(name);
    }

    public List<String> getEchoList() {
        return new ArrayList<String>(list.keySet());
    }

    public void addArea(String name, String Path, FTNAddr link, FTNAddr myAddr) throws IOException {
        EchoCfg cfg = new EchoCfg();
        cfg.Name = name;
        cfg.Path = Path + System.getProperty("file.separator") + name;
        cfg.Type = "JAM";
        cfg.AKA = myAddr; //Config.getAddress();
        cfg.Link = link;
        list.put(name, cfg);
        saveToList(cfg);
    }

    private void saveToList(EchoCfg echo) throws IOException {
        FileWriter fw = null;
        try {
            fw = new FileWriter(areaListFile, true);
            PrintWriter out = new PrintWriter(fw);
            out.println(echo.getEchoString());
            out.close();
        } finally {
            if (fw != null) {
                fw.close();
            }
        }


    }
}
