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

package org.fidonet.echobase.jam;

import org.fidonet.echobase.EchoBase;
import org.fidonet.echobase.EchoCfg;
import org.fidonet.echobase.EchoList;
import org.fidonet.echobase.exceptions.EchoBaseException;
import org.fidonet.types.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

public class JAMEchoBase implements EchoBase {

    private static final Logger logger = LoggerFactory.getLogger(JAMEchoBase.class.getName());

    private String headerfilename;
    private String textfilename;
    private String indexfilename;
    private String lastrfilename;

    private File headerfile;
    private File textfile;
    private File indexfile;
    private File lastrfile;

    private JHRFile header;
    private JDTFile text;
    private JDXFile index;
    private JLRFile lastread;

    private EchoList echoList;

    public JAMEchoBase(EchoList echoList) {
        this.echoList = echoList;
    }

    void setFileNames(String name) throws EchoBaseException {
        EchoCfg echo = echoList.getEcho(name);
        if (echo == null) {
            throw new EchoBaseException("Unknown area: " + name);
        }
        headerfilename = echo.Path + ".jhr";
        textfilename = echo.Path + ".jdt";
        indexfilename = echo.Path + ".jdx";
        lastrfilename = echo.Path + ".jlr";
    }

    void initFiles() {
        headerfile = new File(headerfilename);
        textfile = new File(textfilename);
        indexfile = new File(indexfilename);
        lastrfile = new File(lastrfilename);
    }

    public void createArea(String name) throws EchoBaseException {
        setFileNames(name);
        initFiles();
        try {
            new JamArea(Path.of(echoList.getEcho(name).Path), name, null).create();
        } catch (IOException e) {
            throw new EchoBaseException("Unable to create " + name + " area", e);
        }
    }
    @Override
    public void openArea(String name) throws EchoBaseException {
        closeArea();
        setFileNames(name);
        initFiles();
        if (!isExists()) {
            createArea(name);
        }
        try {
            header = new JHRFile(headerfile);
            text = new JDTFile(textfile);
            index = new JDXFile(indexfile);
            lastread = new JLRFile(lastrfile);
        } catch (FileNotFoundException e) {
            try {
                closeArea();
            } catch (EchoBaseException closeError) {
                e.addSuppressed(closeError);
            }
            throw new EchoBaseException(e.getMessage(), e);
        }
    }

    @Override
    public void closeArea() throws EchoBaseException {
        Closeable[] files = {header, text, index, lastread};
        header = null;
        text = null;
        index = null;
        lastread = null;
        IOException failure = null;
        for (Closeable file : files) {
            if (file == null) {
                continue;
            }
            try {
                file.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw new EchoBaseException(failure.getMessage(), failure);
        }
    }

    @Override
    public void addMessage(Message msg) throws EchoBaseException {
        if (!msg.isEchomail()) {
            throw new EchoBaseException("JAMEchoBase accepts echomail only");
        }
        try {
            new JamArea(Path.of(headerfilename.substring(0, headerfilename.length() - 4)),
                        msg.getArea(),
                        null).append(msg);
        } catch (IOException e) {
            throw new EchoBaseException("Unable to append JAM message",
                                        e.getCause() == null ? e : e.getCause());
        }
    }
    @Override
    public void delMessage(int id) throws EchoBaseException {
    }

    @Override
    public void getMessage(int id) throws EchoBaseException {
    }

    boolean isExists() {
        File headerfile = new File(headerfilename);
        return headerfile.exists();
    }
}
