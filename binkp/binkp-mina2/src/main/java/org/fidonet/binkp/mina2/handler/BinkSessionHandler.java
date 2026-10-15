/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.binkp.mina2.handler;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.commands.ADRCommand;
import org.fidonet.binkp.common.commands.BSYCommand;
import org.fidonet.binkp.common.commands.CramOPTCommand;
import org.fidonet.binkp.common.commands.ERRCommand;
import org.fidonet.binkp.common.commands.LOCCommand;
import org.fidonet.binkp.common.commands.NDLCommand;
import org.fidonet.binkp.common.commands.OPTCommand;
import org.fidonet.binkp.common.commands.SYSCommand;
import org.fidonet.binkp.common.commands.TIMECommand;
import org.fidonet.binkp.common.commands.VERCommand;
import org.fidonet.binkp.common.commands.ZYZCommand;
import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.commands.share.Command;
import org.fidonet.binkp.common.commands.share.CommandFactory;
import org.fidonet.binkp.common.commands.share.CompositeMessage;
import org.fidonet.binkp.common.commands.share.MessageCommand;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.io.BinkData;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.ReceiveTransfer;
import org.fidonet.binkp.mina2.commons.Mina2Session;
import org.fidonet.binkp.mina2.commons.SessionKeys;
import org.fidonet.events.EventBus;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        return (SessionContext) session.getAttribute(SessionKeys.SESSION_CONTEXT_KEY);
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        super.sessionOpened(session);

        SessionContext sessionContext = getSessionContext(session);
        if (sessionContext == null) {
            sessionContext = Objects.requireNonNull(this.sessionContext,
                                                    "Session context is missing");
            session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, sessionContext);
            session.setAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY, new TrafficCrypter());
        }
        log.debug("Session is opened with {}", sessionContext.getLinksInfo().getCurLink());

        sessionContext.prepareTransfer();
        sessionContext.setReceiveEventBus(eventBus);
        boolean isClient = sessionContext.getServerRole().equals(ServerRole.CLIENT);

        Mina2Session mina2Session = new Mina2Session(session);

        if (!isClient && sessionContext.isBusy()) {
            log.info("Server is busy. Sending BSY command...");
            Command bsy = new BSYCommand();
            sessionContext.setLastErrorMessage("To many connections");
            bsy.send(mina2Session, sessionContext);
            sessionContext.setState(SessionState.STATE_BSY);
            session.closeOnFlush();
            return;
        }

        List<MessageCommand> commands = new ArrayList<MessageCommand>();
        if (!isClient) {
            commands.add(new CramOPTCommand());
        }
        commands.add(new OPTCommand());
        commands.add(new SYSCommand());
        commands.add(new ZYZCommand());
        commands.add(new LOCCommand());
        commands.add(new NDLCommand());
        commands.add(new VERCommand());
        commands.add(new TIMECommand());
        commands.add(new ADRCommand());

        CompositeMessage greeting = new CompositeMessage(commands);
        greeting.send(mina2Session, sessionContext);
        if (!isClient) {
            log.debug("Greeting was sent. Waiting password...");
            sessionContext.setState(SessionState.STATE_WAITPWD);
        }
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        SessionContext sessionContext = getSessionContext(session);
        BinkFrame data = (BinkFrame) message;
        if (data.getData().length == 0) {
            return;
        }
        Command command;
        BinkData binkData;
        Mina2Session mina2Session = new Mina2Session(session);
        try {
            binkData = BinkFrame.toBinkData(data);
            command = new CommandFactory().createCommand(sessionContext, binkData);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            sessionContext.setState(SessionState.STATE_ERR);
            sessionContext.setLastErrorMessage(ex.getMessage());
            Command error = new ERRCommand();
            error.send(mina2Session, sessionContext);
            sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
            session.closeOnFlush();
            throw ex;
        }
        if (command != null) {
            log.debug("Get command: {}", BinkCommand.findCommand(binkData.getCommand()));
            log.debug("Command data: {}", new String(binkData.getData(), StandardCharsets.UTF_8));
            command.handle(mina2Session,
                           sessionContext,
                           new String(binkData.getData(), StandardCharsets.UTF_8));
        } else if (!binkData.isCommand()) {
            try {
                ReceiveTransfer.accept(mina2Session, sessionContext, binkData.getData());
            } catch (Exception e) {
                exceptionCaught(session, e);
            }
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        super.sessionIdle(session, status); // To change body of overridden methods use File | Settings | File
        // Templates.
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        SessionContext context = getSessionContext(session);
        if (context != null) {
            if (context.getState() != SessionState.STATE_ERR
                    && context.getState() != SessionState.STATE_BSY) {
                context.setState(SessionState.STATE_END);
            }
            context.closeReceiveStreams();
        }
        super.sessionClosed(session);
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        log.error(cause.getMessage(), cause);
        SessionContext context = getSessionContext(session);
        if (context != null) {
            context.setState(SessionState.STATE_ERR);
            context.setLastErrorMessage(cause.getMessage());
            try {
                new ERRCommand().send(new Mina2Session(session), context);
            } finally {
                context.closeReceiveStreams();
                session.closeOnFlush();
            }
        }
    }
}
