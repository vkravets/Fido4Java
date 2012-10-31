package org.fidonet.binkp.handler;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.SessionState;
import org.fidonet.binkp.codec.DataBulk;
import org.fidonet.binkp.codec.TrafficCrypter;
import org.fidonet.binkp.commands.*;
import org.fidonet.binkp.commands.share.BinkCommand;
import org.fidonet.binkp.commands.share.Command;
import org.fidonet.binkp.commands.share.CommandFactory;
import org.fidonet.binkp.commands.share.CompositeMessage;
import org.fidonet.binkp.config.ServerRole;
import org.fidonet.binkp.events.DisconnectedEvent;
import org.fidonet.binkp.events.FileReceivedEvent;
import org.fidonet.binkp.io.BinkData;
import org.fidonet.binkp.io.BinkFrame;
import org.fidonet.binkp.io.FileData;
import org.fidonet.binkp.io.FileInfo;
import org.fidonet.events.EventBus;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 1:18 PM
 */
public class BinkSessionHandler extends IoHandlerAdapter{

    private static ILogger log = LoggerFactory.getLogger(BinkSessionHandler.class);

    private SessionContext sessionContext;
    private EventBus eventBus;

    public BinkSessionHandler(EventBus eventBus) {
        this.sessionContext = null;
        if (eventBus == null) {
            throw new UnsupportedOperationException();
        }
        this.eventBus = eventBus;
    }

    public BinkSessionHandler(SessionContext context, EventBus eventBus) {
        this(eventBus);
        this.sessionContext = context;
    }

    private SessionContext getSessionContext(IoSession session) {
        if (sessionContext != null) {
            return sessionContext;
        } else {
            return (SessionContext)session.getAttribute(SessionContext.SESSION_CONTEXT_KEY);
        }
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        super.sessionOpened(session);

        SessionContext sessionContext = getSessionContext(session);
        log.debug(String.format("Session is opened with %s", sessionContext.getLinksInfo().getCurLink().toString()));

        session.setAttribute(SessionContext.SESSION_CONTEXT_KEY, sessionContext);
        session.setAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY, new TrafficCrypter());

        boolean isClient = sessionContext.getServerRole().equals(ServerRole.CLIENT);

        if (!isClient && sessionContext.isBusy()) {
            log.info("Server is busy. Sending BSY command...");
            Command bsy = new BSYCommand();
            sessionContext.setLastErrorMessage("To many connections");
            bsy.send(session, sessionContext);
            sessionContext.setState(SessionState.STATE_BSY);
            session.close(false);
        }

        List<MessageCommand> commands = new ArrayList<MessageCommand>();
        commands.add(new SYSCommand());
        commands.add(new ZYZCommand());
        commands.add(new LOCCommand());
        commands.add(new NDLCommand());
        commands.add(new VERCommand());
        commands.add(new TIMECommand());
        commands.add(new ADRCommand());

        CompositeMessage greeting = new CompositeMessage(commands);
        greeting.send(session, sessionContext);
        if (!isClient) {
            log.debug("Greeting was sent. Waiting password...");
            sessionContext.setState(SessionState.STATE_WAITPWD);
        }

    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        log.error(cause.getMessage(), cause);
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        SessionContext sessionContext = getSessionContext(session);
        BinkFrame data = (BinkFrame) message;
        BinkData binkData = BinkFrame.toBinkData(data);
        Command command;
        try {
            command = CommandFactory.createCommand(sessionContext, binkData);
        } catch (UnknownCommandException ex) {
            log.error(ex.getMessage(), ex);
            sessionContext.setState(SessionState.STATE_ERR);
            sessionContext.setLastErrorMessage(ex.getMessage());
            Command error = new ERRCommand();
            error.send(session, sessionContext);
            sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
            session.close(false);
            throw ex;
        }
        if (command != null) {
            log.debug("Get command: " + BinkCommand.findCommand(binkData.getCommand()));
            log.debug("Command data: " + new String(binkData.getData()));
            command.handle(session, sessionContext, new String(binkData.getData()));
        } else {
            // try to get data bulk
            DataBulk dataFile = new DataBulk(binkData.getData());
            log.debug("Received data block with size " + dataFile.getRawData().getData().length + " bytes");
            FileData<OutputStream> fileData = sessionContext.getRecvFiles().peek();
            if (fileData != null) {
                FileInfo info = fileData.getInfo();
                log.debug("Saving data bulk for " + info.getName() + " file");
                long curSize = info.getCurSize() + dataFile.getRawData().getData().length;
                fileData.getStream().write(dataFile.getRawData().getData());
                info.setCurSize(curSize);
                info.setFinished(curSize == info.getSize());
                if (info.isFinished()) {
                    GOTCommand confirmRecv = new GOTCommand();
                    confirmRecv.send(session, sessionContext);
                    eventBus.notify(new FileReceivedEvent(sessionContext, fileData));
                    log.info("Got " + info.getName() + " file with " + info.getSize() + " bytes file size");
                }
            }
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        super.sessionIdle(session, status);  //To change body of overridden methods use File | Settings | File Templates.
    }
}
