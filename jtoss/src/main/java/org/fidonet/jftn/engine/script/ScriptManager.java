package org.fidonet.jftn.engine.script;

import org.apache.log4j.Logger;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:40 AM
 * To change this template use File | Settings | File Templates.
 */
public class ScriptManager {

    private static Logger logger = Logger.getLogger(ScriptManager.class);

    private ScriptEngine jythonEngine;
    private Map<String, Object> scriptVariables;
    private String scriptFolder;

    public ScriptManager() {
        this("./scripts/");
    }

    public ScriptManager(String scriptFolder) {
        scriptVariables = new HashMap<String, Object>();
        this.scriptFolder = scriptFolder;

        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        jythonEngine = scriptEngineManager.getEngineByExtension("py");
        if (jythonEngine == null) {
            // TODO logger
            throw new VerifyError("Jython script engine is not found");
        }
        File scriptsFolder = new File(scriptFolder);
        try {
            String fullScriptPath = scriptsFolder.getCanonicalPath();
            logger.debug("Adding to PYTHON_PATH \""+fullScriptPath+"\" folder");
            jythonEngine.eval(String.format("import sys; sys.path.append(\"%s\")", scriptsFolder.getCanonicalPath()));
        } catch (Exception e){
            logger.error(e.getMessage(), e);
            // TODO throw exception
        }
    }

    private ScriptEngine getJythonScriptEngine() throws Exception {
        return jythonEngine;
    }

    public <T> T getInterface(Object object, Class<T> type) {
        return ((Invocable)jythonEngine).getInterface(object, type);
    }

    public void runScript(File script) throws Exception {
        InputStream inputStream = new FileInputStream(script);
        this.runScript(inputStream);
    }

    public void runScript(InputStream stream) throws Exception {
        InputStreamReader reader = new InputStreamReader(stream);
        ScriptEngine jythonEngine = getJythonScriptEngine();
        if (!scriptVariables.isEmpty()) {
            for (String name : scriptVariables.keySet()) {
                jythonEngine.put(name, scriptVariables.get(name));
            }
        }
        jythonEngine.eval(reader);
    }

    public void addScriptVar(String name, Object value) {
        if (name != null && value != null) {
            scriptVariables.put(name, value);
        } else {
            // TODO: Log warn
        }
    }

    public void removeScriptVar(String name, Object value) {
        if (name != null && value != null) {
            if (scriptVariables.get(name) != null) {
                scriptVariables.remove(name);
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
                if (fileName.indexOf(".py") == fileName.length()-3) {
                    try {
                        logger.debug("Loading " + file.getName());
                        runScript(file);
                    } catch (Exception e) {
                        // TODO logger
                        // TODO throw exception
                    }
                }
            }
        }
    }
}
