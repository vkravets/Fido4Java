package org.fidonet.jftn.engine.script;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 8/31/11
 * Time: 10:29 AM
 * To change this template use File | Settings | File Templates.
 */
public class JFtnShare {

    private ScriptManager scriptManager;

    public JFtnShare(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }
}
