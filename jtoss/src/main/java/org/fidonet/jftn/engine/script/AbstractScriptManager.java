/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.jftn.engine.script;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * Date: 11/26/13
 * Time: 1:57 AM
 */
public abstract class AbstractScriptManager implements ScriptEngine {

    private static final ILogger logger = LoggerFactory.getLogger(AbstractScriptManager.class.getName());

    private String scriptFolder = "";
    private javax.script.ScriptEngine engine;

    public AbstractScriptManager() throws EngineNotFoundException {
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        String fileExtension = getFileExtension();
        javax.script.ScriptEngine scriptEngine = scriptEngineManager.getEngineByExtension(fileExtension);
        if (scriptEngine == null) throw new EngineNotFoundException(String.format("Engine for \"%s\" file extension was not found", fileExtension));
        setScriptEngine(scriptEngine);
    }

    public AbstractScriptManager(String scriptFolder) throws EngineNotFoundException {
        this();
        setScriptFolder(scriptFolder);
    }


    protected void setScriptFolder(String scriptFolder) {
        this.scriptFolder = scriptFolder;
    }

    protected javax.script.ScriptEngine getScripEngine() {
        return engine;
    }

    protected void setScriptEngine(javax.script.ScriptEngine engine) throws VerifyError {
        if (engine == null) {
            throw new VerifyError("engine cannot be null");
        }
        this.engine = engine;
    }

    public <T> T getInterface(Object object, Class<T> type) {
        return ((Invocable) engine).getInterface(object, type);
    }

    public void runScript(File script) throws Exception {
        InputStream inputStream = new FileInputStream(script);
        this.runScript(inputStream);
    }

    public void runScript(InputStream stream) throws Exception {
        InputStreamReader reader = new InputStreamReader(stream);
        engine.eval(reader);
    }

    public void putVariable(String name, Object value) {
        if (name != null && value != null) {
            engine.put(name, value);
        } else {
            logger.warn(String.format("Variable will not be added. Name: \"%s\" Value:\"%s\"", name, value));
        }
    }

    public void removeVariable(String name, Object value) {
        if (name != null && value != null) {
            if (engine.get(name) != null) {
                engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(name);
            } else {
                logger.warn("Variable was scoped already");
            }
        } else {
            logger.warn("Variable cannot be scoped. Name or Values is specified.");
        }
    }

    public void reloadScripts() {
        File scriptFolderFile = new File(scriptFolder);
        File[] fileList = scriptFolderFile.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                String fileName = file.getName();
                if (fileName.indexOf("." + getFileExtension()) == fileName.length() - 3) {
                    try {
                        logger.debug("Loading " + file.getName());
                        runScript(file);
                    } catch (Exception e) {
                        logger.error(String.format("Error during loading %s script. Details: %s", file.getName(), e.getMessage()), e);
                    }
                }
            }
        }
    }

    @Override
    public javax.script.ScriptEngine getEngine() {
        return engine;
    }

    protected abstract String getFileExtension();

}
