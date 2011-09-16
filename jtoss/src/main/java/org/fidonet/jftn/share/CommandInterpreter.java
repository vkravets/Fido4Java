package org.fidonet.jftn.share;

import org.fidonet.jftn.engine.script.ScriptManager;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class CommandInterpreter {

    private CommandCollection commands;

    public CommandInterpreter(CommandCollection commands) {
        this.commands = commands;
    }

    public void registerCommand(ScriptManager scriptManager, String name, Object command) throws Exception {
        if ((scriptManager == null)) {
            // TODO logger
            throw new VerifyError("Parameter scriptManager is empty");
        }
        if ((name == null) || (name.trim().length() == 0)) {
            // TODO logger
            throw new VerifyError("Parameter name is empty");
        }
        if (command == null) {
            // TODO logger
            throw new VerifyError("Parameter command is empty");
        }
        Command commandObject = scriptManager.getInterface(command, Command.class);
        commands.addCommand(name, commandObject);
    }
}
