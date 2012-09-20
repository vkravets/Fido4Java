package org.fidonet.jftn.engine.script;

import java.io.File;
import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/5/12
 * Time: 8:12 PM
 */
public interface ScriptEngine {
    public <T> T getInterface(Object object, Class<T> type);
    public void runScript(File script) throws Exception;
    public void runScript(InputStream script) throws Exception;
    public void reloadScripts();
    public void addScriptVar(String name, Object object);
    public void removeScriptVar(String name, Object value);
}
