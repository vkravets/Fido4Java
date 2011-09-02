package org.fidonet.jftn.engine.script;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.event.Event;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/31/11
 * Time: 10:29 AM
 * To change this template use File | Settings | File Templates.
 */
public class JFtnShare  {

    private ScriptManager scriptManager;
    private CommandInterpreter commands;
    private HookInterpreter hooks;
    private IConfig config;

    public JFtnShare(ScriptManager scriptManager, HookInterpreter hooks, CommandInterpreter commands) {
        this.scriptManager = scriptManager;
        this.hooks = hooks;
        this.commands = commands;
    }


    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public void registerCommand(String name, Object command) throws Exception {
        commands.registerCommand(scriptManager, name, command);
    }

    public void registerHook(Class<? extends Event> hookClass, Object hook) throws Exception {
        hooks.registerHook(scriptManager, hookClass, hook);
    }

    public IConfig getConfig() {
        return config;
    }

    public void setConfig(IConfig config) {
        this.config = config;
    }

}
