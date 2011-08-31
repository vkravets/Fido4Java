package org.fidonet.jftn.share;

import org.fidonet.jftn.engine.script.JFtnShare;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class CommandInterpreter {

    public static void registerCommand(JFtnShare jftn, String name, Object command) throws Exception {
        if (jftn == null) {
            throw new VerifyError("Parameter jftn is not correctly specified");
        }
        if ((name == null) || (name.trim().length() == 0)) {
            throw new VerifyError("Parameter name is empty");
        }
        if (command == null) {
            throw new VerifyError("Parameter command is empty");
        }
        Command commandObject = jftn.getScriptManager().getInterface(command, Command.class);
        CommandCollection.getInstance().addCommand(name, commandObject);
    }
}
