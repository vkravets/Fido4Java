package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;
import org.fidonet.binkp.io.BinkFrame;

import java.nio.ByteBuffer;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:27 PM
 */
public abstract class MessageCommand implements Command {

    protected BinkCommand commandType;

    public MessageCommand(BinkCommand commandType) {
        this.commandType = commandType;
    }

    public BinkCommand getCommandType() {
        return commandType;
    }

    @Override
    public abstract boolean isHandle(SessionContext sessionContext, BinkCommand command, String args);

    @Override
    public abstract void handle(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception;

    @Override
    public void send(IoSession session, SessionContext sessionContext) throws Exception {
        String cmdArgs = getCommandArguments(sessionContext);
        session.write(getData(cmdArgs));
    }

    public abstract String getCommandArguments(SessionContext sessionContext);

    public BinkFrame getData(String cmdArgs) {
        ByteBuffer buf;
        byte[] argsData = null;
        if (cmdArgs != null) {
            argsData = cmdArgs.getBytes();
            buf = ByteBuffer.allocate(1 + argsData.length);
        } else {
            buf = ByteBuffer.allocate(1);
        }
        buf.put(commandType.getCmd());
        if (argsData != null) {
            buf.put(argsData);
        }
        int len = buf.capacity() | 0x8000;
        return new BinkFrame((short)len, buf.array());
    }

    @Override
    public BinkFrame getRawData() {
        return null;
    }

    @Override
    public boolean isCommand() {
        return true;
    }

    @Override
    public String toString() {
        return "MessageCommand{" +
                "commandType=" + commandType +
                "isCommand=" + isCommand() +
                '}';
    }
}
