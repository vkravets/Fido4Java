package org.fidonet.binkp.common.protocol;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.commands.ADRCommand;
import org.fidonet.binkp.common.commands.CramOPTCommand;
import org.fidonet.binkp.common.commands.LOCCommand;
import org.fidonet.binkp.common.commands.NDLCommand;
import org.fidonet.binkp.common.commands.OPTCommand;
import org.fidonet.binkp.common.commands.SYSCommand;
import org.fidonet.binkp.common.commands.TIMECommand;
import org.fidonet.binkp.common.commands.VERCommand;
import org.fidonet.binkp.common.commands.ZYZCommand;
import org.fidonet.binkp.common.commands.share.Command;
import org.fidonet.binkp.common.commands.share.CommandFactory;
import org.fidonet.binkp.common.commands.share.CompositeMessage;
import org.fidonet.binkp.common.commands.share.MessageCommand;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.io.BinkData;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.ReceiveTransfer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** The same handshake and incoming frame dispatcher for TCP and process streams. */
public final class BinkpSessionEngine {
    private BinkpSessionEngine() {
    }

    public static void greet(Session session, SessionContext context)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        context.setMessageDigest(digest);
        List<MessageCommand> commands = new ArrayList<>();
        if (context.getServerRole() == ServerRole.SERVER) {
            context.setState(SessionState.STATE_WAITPWD);
            commands.add(new CramOPTCommand(digest));
        }
        commands.add(new OPTCommand());
        commands.add(new SYSCommand());
        commands.add(new ZYZCommand());
        commands.add(new LOCCommand());
        commands.add(new NDLCommand());
        commands.add(new VERCommand());
        commands.add(new TIMECommand());
        commands.add(new ADRCommand());
        new CompositeMessage(commands).send(session, context);
    }

    public static void receive(Session session, SessionContext context, BinkFrame frame)
            throws Exception {
        if (context.getServerRole() == ServerRole.SERVER
                && context.getState() == SessionState.STATE_BSY) {
            return;
        }
        BinkData data = BinkFrame.toBinkData(frame);
        Command command = new CommandFactory().createCommand(context, data);
        if (command != null) {
            command.handle(session, context, new String(data.getData(), StandardCharsets.UTF_8));
        } else if (!data.isCommand()) {
            ReceiveTransfer.accept(session, context, data.getData());
        }
    }
}
