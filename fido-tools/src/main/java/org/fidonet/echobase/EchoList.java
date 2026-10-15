/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.echobase;

import org.fidonet.types.FTNAddr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EchoList {

    private static final Logger logger = LoggerFactory.getLogger(EchoList.class.getName());

    private HashMap<String, EchoCfg> list = new HashMap<>();
    private String areaListFile;

    public EchoList(String areaListFile) {
        this.areaListFile = areaListFile;
    }

    public void load(String filename) throws IOException {
        HashMap<String, EchoCfg> loaded = readList(filename);
        this.areaListFile = filename;
        this.list = loaded;
    }

    public void load() throws IOException {
        list = readList(areaListFile);
    }

    private HashMap<String, EchoCfg> readList(String filename) throws IOException {
        HashMap<String, EchoCfg> loaded = new HashMap<>();
        try (BufferedReader file = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNumber = 0;
            while ((line = file.readLine()) != null) {
                EchoCfg echo = parseAreaLine(line, filename, ++lineNumber);
                if (echo != null) {
                    loaded.put(echo.Name, echo);
                }
            }
        }
        return loaded;
    }

    private EchoCfg parseAreaLine(String line, String filename, int lineNumber) throws IOException {
        int comment = line.indexOf('#');
        String content = (comment < 0 ? line : line.substring(0, comment)).trim();
        if (content.isEmpty()) {
            return null;
        }
        String[] area = content.split("\\s+");
        if (!area[0].equalsIgnoreCase("echoarea")) {
            return null;
        }
        if (area.length < 3) {
            throw new IOException("Incomplete EchoArea at " + filename + ":" + lineNumber);
        }
        EchoCfg echo = new EchoCfg();
        echo.Name = area[1];
        echo.Path = area[2];
        readAreaOptions(echo, area, filename, lineNumber);
        return echo;
    }

    private void readAreaOptions(EchoCfg echo, String[] area, String filename, int lineNumber)
            throws IOException {
        for (int i = 3; i < area.length; i++) {
            validateOptionValue(area, i, filename, lineNumber);
            switch (area[i]) {
                case "-b" :
                    echo.Type = area[++i];
                    break;
                case "-g" :
                    echo.Group = area[++i];
                    break;
                case "-a" :
                    echo.AKA = new FTNAddr(area[++i]);
                    break;
                default :
                    if (!area[i].startsWith("-")) {
                        echo.Link = new FTNAddr(area[i]);
                    }
            }
        }
    }

    private void validateOptionValue(String[] area, int index, String filename, int lineNumber)
            throws IOException {
        String option = area[index];
        boolean needsValue = option.equals("-b") || option.equals("-g") || option.equals("-a");
        if (needsValue && (index + 1 == area.length || area[index + 1].startsWith("-"))) {
            throw new IOException("Missing value for "
                                  + option
                                  + " at "
                                  + filename
                                  + ":"
                                  + lineNumber);
        }
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
        cfg.AKA = myAddr; // Config.getAddress();
        cfg.Link = link;
        saveToList(cfg);
        list.put(name, cfg);
    }

    private void saveToList(EchoCfg echo) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(areaListFile, true))) {
            out.write(echo.getEchoString());
            out.newLine();
        }
    }
}
