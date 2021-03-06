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

package org.fidonet.jftn.engine.script;

import org.fidonet.jftn.engine.script.exception.EngineNotFoundException;
import org.fidonet.jftn.engine.script.exception.NotSupportedScriptEngine;
import org.fidonet.jftn.tools.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * Date: 11/26/13
 * Time: 1:57 AM
 */
public abstract class AbstractScriptManager implements ScriptEngine {

    private static final Logger logger = LoggerFactory.getLogger(AbstractScriptManager.class.getName());
    private static final String REGISTER_FUNC_NAME = "register";
    private static final String UNREGISTER_FUNC_NAME = "unload";

    private static final HashMap<String, String> loadedScripts = new HashMap<String, String>();

    private javax.script.ScriptEngine engine;

    public AbstractScriptManager() throws EngineNotFoundException {
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        String fileExtension = getFileExtension();
        javax.script.ScriptEngine scriptEngine = scriptEngineManager.getEngineByExtension(fileExtension);
        if (scriptEngine == null)
            throw new EngineNotFoundException(String.format("Engine for \"%s\" file extension was not found", fileExtension));
        setScriptEngine(scriptEngine);
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

    public void registerScript(String name, String script, Object... params) throws Exception {
        updateCache(name, script);
        scriptCallFunctions(name, script, REGISTER_FUNC_NAME, params);
    }

    public void unregisterScript(String name, Object... params) throws Exception {
        String cache = getFromCache(name);
        if (cache == null || cache.isEmpty()) {
            logger.warn("Something wrong. {} script is empty", name);
            return;
        }
        scriptCallFunctions(name, cache, UNREGISTER_FUNC_NAME, params);
    }

    private void scriptCallFunctions(String name, String script, String funcName, Object... params) throws ScriptException, NotSupportedScriptEngine {

        engine.eval(script);

        try {
            Invocable invocableEngine = (Invocable) engine;
            invocableEngine.invokeFunction(funcName, params);
        } catch (ClassCastException ex) {
            throw new NotSupportedScriptEngine("This script engine is not supported", ex);
        } catch (NoSuchMethodException ex) {
            logger.warn("Script doesn't have register function. Skip");
        }
    }

    public void reloadScripts(Map<String, String> scriptsContent, Object... params) {
        reloadScripts(scriptsContent, false, params);
    }

    public void reloadScripts(Map<String, String> scriptsContent, Boolean forceReload, Object... params) {
        if (scriptsContent.size() > 0) {
            for (Map.Entry<String, String> script : scriptsContent.entrySet()) {
                String fileName = script.getKey();
                if (fileName.indexOf("." + getFileExtension()) == fileName.length() - 3) {
                    try {
                        logger.debug("Loading {}", fileName);
                        String cacheContent = getFromCache(fileName);
                        String content = loadScriptContent(new FileInputStream(fileName));
                        boolean isCached = cacheContent != null && cacheContent.equals(content);
                        // if script was read before, need to call unregister on it, to unload old one.
                        if (isCached) {
                            logger.debug("Unloading old one");
                            unregisterScript(fileName, params);
                        }
                        // if force reload set to true or file was not read - try to register it.
                        if (forceReload || !isCached) {
                            logger.debug("Registering {} script", fileName);
                            registerScript(fileName, content);
                        }
                        logger.debug("{} successfully registered", fileName);

                    } catch (Exception e) {
                        logger.error("Error during loading {} script. Details: {}", fileName, e.getMessage(), e);
                    }
                }
            }
        }
    }

    private String getFromCache(String name) {
        return loadedScripts.get(name);
    }

    private void updateCache(String name, String scriptContent) {
        loadedScripts.put(name, scriptContent);
    }


    @Override
    public javax.script.ScriptEngine getEngine() {
        return engine;
    }

    protected abstract String getFileExtension();

    private String loadScriptContent(InputStream stream) throws ScriptException {
        try {
            return FileUtils.getFileContent(stream);
        } catch (IOException exp) {
            throw new ScriptException(exp);
        }
    }

}
