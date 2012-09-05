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

    private ScriptEngine scriptEngine;
    private CommandInterpreter commands;
    private HookInterpreter hooks;
    private IConfig config;

    public JFtnShare(ScriptEngine scriptEngine, HookInterpreter hooks, CommandInterpreter commands) {
        this.scriptEngine = scriptEngine;
        this.hooks = hooks;
        this.commands = commands;
    }


    public ScriptEngine getScriptEngine() {
        return scriptEngine;
    }

    public void registerCommand(String name, Object command) throws Exception {
        commands.registerCommand(scriptEngine, name, command);
    }

    public void registerHook(Class<? extends Event> hookClass, Object hook) throws Exception {
        hooks.registerHook(scriptEngine, hookClass, hook);
    }

    public IConfig getConfig() {
        return config;
    }

    public void setConfig(IConfig config) {
        this.config = config;
    }

}
