package org.fidonet.jftn.share;

import org.fidonet.jftn.engine.script.ScriptManager;

import javax.script.Invocable;

/**
 * Created by IntelliJ IDEA.
 * User: sly
 * Date: 8/29/11
 * Time: 11:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class CommandInterpreter {

    public static void registerCommand(String name, Object command) throws Exception {
        Command commandObject = ((Invocable)ScriptManager.getInstance().getJythonScriptEngine()).getInterface(command, Command.class);
        CommandCollection.getInstance().addCommand(name, commandObject);
    }
}
