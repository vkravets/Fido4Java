package org.fidonet.jftn.share;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/29/11
 * Time: 11:21 AM
 * To change this template use File | Settings | File Templates.
 */
public class CommandCollection {

    private Map<String, Command> commands;

    public CommandCollection() {
        commands = new HashMap<String, Command>();
    }

    public void addCommand(String name, Command command) {
        if (command != null && name != null && name.length() > 0) {
            commands.put(name, command);
        }
        // TODO: throw exception
    }

    public Command findCommandByName(String name){
        return commands.get(name);
    }

}
