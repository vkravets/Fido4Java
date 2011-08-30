package org.fidonet.jftn.engine.script;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:40 AM
 * To change this template use File | Settings | File Templates.
 */
public class ScriptManager {

    private static ScriptManager instance;
    private ScriptEngineManager scriptEngineManager;

    public ScriptManager() {
        scriptEngineManager = new ScriptEngineManager();
    }

    public static ScriptManager getInstance() {
        if (instance == null) {
            instance = new ScriptManager();
        }
        return instance;
    }

    public ScriptEngine getJythonScriptEngine() throws Exception {
        ScriptEngine scriptEngine = scriptEngineManager.getEngineByExtension("py");
        if (scriptEngine == null) {
            throw new Exception("Jython script engine is not foudn");
        }
        return scriptEngine;
    }

    public void runScript(File script) throws Exception {
        InputStream inputStream = new FileInputStream(script);
        this.runScript(inputStream);
    }

    public void runScript(InputStream stream) throws Exception {
        InputStreamReader reader = new InputStreamReader(stream);
        ScriptEngine jythonEngine = getJythonScriptEngine();
        jythonEngine.eval(reader);
    }

}
