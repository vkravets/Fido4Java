package org.fidonet.jftn.share;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.util.Collection;
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

    private static ILogger logger = LoggerFactory.getLogger(CommandCollection.class.getName());

    private Map<String, Command> commands;

    public CommandCollection() {
        commands = new HashMap<String, Command>();
    }

    public void addCommand(String name, Command command) {
        if (command != null && name != null && name.length() > 0) {
            Command cmd = commands.get(name);
            if (cmd == null) {
                commands.put(name, command);
            } else {
                logger.warn("\""+name+"\" command already registered!");
            }
        }
    }

    public Command findCommandByName(String name){
        return commands.get(name);
    }

    public Collection<Command> getCommands() {
        return commands.values();
    }

}
