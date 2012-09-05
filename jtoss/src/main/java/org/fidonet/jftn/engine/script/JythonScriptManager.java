package org.fidonet.jftn.engine.script;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:40 AM
 * To change this template use File | Settings | File Templates.
 */
public class JythonScriptManager implements org.fidonet.jftn.engine.script.ScriptEngine {

    private static ILogger logger = LoggerFactory.getLogger(JythonScriptManager.class.getName());

    private ScriptEngine jythonEngine;
    private Map<String, Object> scriptVariables;
    private String scriptFolder;

    public JythonScriptManager() throws ScriptException, IOException {
        this("./scripts/");
    }

    public JythonScriptManager(String scriptFolder) throws IOException, ScriptException {
        scriptVariables = new HashMap<String, Object>();
        this.scriptFolder = scriptFolder;

        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        jythonEngine = scriptEngineManager.getEngineByExtension("py");
        if (jythonEngine == null) {
            throw new VerifyError("Jython script engine is not found");
        }
        File scriptsFolder = new File(scriptFolder);
        String fullScriptPath = scriptsFolder.getCanonicalPath();
        logger.debug("Adding to PYTHON_PATH \""+fullScriptPath+"\" folder");
        jythonEngine.eval(String.format("import sys; sys.path.append(\"%s\")", fullScriptPath));
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
            logger.warn(String.format("Variable will not be added. Name: \"%s\" Value:\"%s\"", name, value));
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
                        logger.error(String.format("Error during loading %s script. Details: %s", file.getName(), e.getMessage()), e);
                    }
                }
            }
        }
    }
}
