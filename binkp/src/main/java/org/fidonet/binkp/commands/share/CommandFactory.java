package org.fidonet.binkp.commands.share;

import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.*;
import org.fidonet.binkp.io.BinkData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 3:53 PM
 */
public class CommandFactory {

    private static List<Command> commands;

    static {
        commands = new ArrayList<Command>();
        commands.add(new ADRCommand());
        commands.add(new BSYCommand());
        commands.add(new EOBCommand());
        commands.add(new ERRCommand());
        commands.add(new FILECommand());
        commands.add(new GETCommand());
        commands.add(new GOTCommand());
        commands.add(new LOCCommand());
        commands.add(new NDLCommand());
        commands.add(new OKCommand());
        commands.add(new PWDCommand());
        commands.add(new SYSCommand());
        commands.add(new TIMECommand());
        commands.add(new VERCommand());
        commands.add(new ZYZCommand());
        commands.add(new OPTCommand());
        commands.add(new TRFCommand());
        commands.add(new LogCommand());
        commands.add(new SKIPCommand());
    }

    public static Command createCommand(SessionContext sessionContext, BinkData data) throws IOException, UnknownCommandException {
        if (data.isCommand()) {
            String argsStr = new String(data.getData());
            BinkCommand command = BinkCommand.findCommand(data.getCommand());
            if (command != null)
                return createCommand(sessionContext, command, argsStr);
        }
        return null;
    }

    public static Command createCommand(SessionContext sessionContext, BinkCommand cmd, String args) throws UnknownCommandException {
        for (Command command : commands) {
            if (command.isHandle(sessionContext, cmd, args)) {
                return command;
            }
        }
        String msg = String.format("Command[%s %s] is not found! ", cmd, args);
        throw new UnknownCommandException(msg);
    }
}
