/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.binkp.mina2.handler;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.mina2.SessionContext;
import org.fidonet.binkp.mina2.SessionState;
import org.fidonet.binkp.mina2.codec.DataBulk;
import org.fidonet.binkp.mina2.codec.TrafficCrypter;
import org.fidonet.binkp.mina2.commands.*;
import org.fidonet.binkp.mina2.commands.share.BinkCommand;
import org.fidonet.binkp.mina2.commands.share.Command;
import org.fidonet.binkp.mina2.commands.share.CommandFactory;
import org.fidonet.binkp.mina2.commands.share.CompositeMessage;
import org.fidonet.binkp.mina2.config.ServerRole;
import org.fidonet.binkp.mina2.events.DisconnectedEvent;
import org.fidonet.binkp.mina2.events.FileReceivedEvent;
import org.fidonet.binkp.mina2.io.BinkData;
import org.fidonet.binkp.mina2.io.BinkFrame;
import org.fidonet.binkp.mina2.io.FileData;
import org.fidonet.binkp.mina2.io.FileInfo;
import org.fidonet.events.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class BinkSessionHandler extends IoHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(BinkSessionHandler.class.getName());

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
            return (SessionContext) session.getAttribute(SessionContext.SESSION_CONTEXT_KEY);
        }
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        super.sessionOpened(session);

        SessionContext sessionContext = getSessionContext(session);
        log.debug("Session is opened with {}", sessionContext.getLinksInfo().getCurLink().toString());

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
        Command command = null;
        BinkData binkData = null;
        try {
            binkData = BinkFrame.toBinkData(data);
            command = CommandFactory.createCommand(sessionContext, binkData);
        } catch (Exception ex) {
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
            log.debug("Get command: {}", BinkCommand.findCommand(binkData.getCommand()));
            log.debug("Command data: {}", new String(binkData.getData()));
            command.handle(session, sessionContext, new String(binkData.getData()));
        } else {
            // try to get data bulk
            DataBulk dataFile = new DataBulk(binkData.getData());
            log.debug("Received data block with size {}bytes", dataFile.getRawData().getData().length);
            FileData<OutputStream> fileData = sessionContext.getRecvFiles().peek();
            if (fileData != null) {
                FileInfo info = fileData.getInfo();
                log.debug("Saving data bulk for {} file", info.getName());
                long curSize = info.getCurSize() + dataFile.getRawData().getData().length;
                fileData.getStream().write(dataFile.getRawData().getData());
                info.setCurSize(curSize);
                info.setFinished(curSize == info.getSize());
                if (info.isFinished()) {
                    GOTCommand confirmRecv = new GOTCommand();
                    confirmRecv.send(session, sessionContext);
                    eventBus.publish(new FileReceivedEvent(sessionContext, fileData));
                    log.info("Got {} file with {} bytes file size", info.getName(), info.getSize());
                }
            }
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        super.sessionIdle(session, status);  //To change body of overridden methods use File | Settings | File Templates.
    }
}