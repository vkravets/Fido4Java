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

package org.fidonet.binkp.mina3.handler;

import org.apache.mina.api.AbstractIoHandler;
import org.apache.mina.api.IoSession;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.codec.DataBulk;
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.commands.*;
import org.fidonet.binkp.common.commands.share.*;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.events.DisconnectedEvent;
import org.fidonet.binkp.common.events.FileReceivedEvent;
import org.fidonet.binkp.common.io.BinkData;
import org.fidonet.binkp.common.io.BinkFrame;
import org.fidonet.binkp.common.io.FileData;
import org.fidonet.binkp.common.io.FileInfo;
import org.fidonet.binkp.mina3.commons.Mina3Session;
import org.fidonet.binkp.mina3.commons.SessionKeys;
import org.fidonet.events.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
public class BinkSessionHandler extends AbstractIoHandler {

    protected static final Logger log = LoggerFactory.getLogger(BinkSessionHandler.class.getName());

    protected SessionContext sessionContext;
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
        SessionContext context = session.getAttribute(SessionKeys.SESSION_CONTEXT_KEY);
        return context == null ? sessionContext : context;
    }

    @Override
    public void sessionOpened(IoSession session) {
        SessionContext sessionContext = getSessionContext(session);
        log.debug("Session is opened with {}", sessionContext.getLinksInfo().getCurLink().toString());

        session.setAttribute(SessionKeys.SESSION_CONTEXT_KEY, sessionContext);
        session.setAttribute(SessionKeys.TRAFFIC_CRYPTER_KEY, new TrafficCrypter());

        sessionContext.prepareTransfer();
        sessionContext.setReceiveEventBus(eventBus);
        boolean isClient = sessionContext.getServerRole().equals(ServerRole.CLIENT);

        Mina3Session mina3Session = new Mina3Session(session);

        if (!isClient && sessionContext.isBusy()) {
            log.info("Server is busy. Sending BSY command...");
            Command bsy = new BSYCommand();
            sessionContext.setLastErrorMessage("To many connections");
            try {
                bsy.send(mina3Session, sessionContext);
            } catch (Exception e) {
                log.warn("Node is busy");
            }
            sessionContext.setState(SessionState.STATE_BSY);
//            session.close(false);
        }

        List<MessageCommand> commands = new ArrayList<MessageCommand>();
        if (!isClient) commands.add(new org.fidonet.binkp.common.commands.CramOPTCommand());
        commands.add(new org.fidonet.binkp.common.commands.OPTCommand());
        commands.add(new SYSCommand());
        commands.add(new ZYZCommand());
        commands.add(new LOCCommand());
        commands.add(new NDLCommand());
        commands.add(new VERCommand());
        commands.add(new TIMECommand());
        commands.add(new ADRCommand());

        CompositeMessage greeting = new CompositeMessage(commands);
        greeting.send(mina3Session, sessionContext);
        if (!isClient) {
            log.debug("Greeting was sent. Waiting password...");
            sessionContext.setState(SessionState.STATE_WAITPWD);
        }

    }

    @Override
    public void sessionClosed(IoSession session) {
        SessionContext context = getSessionContext(session);
        if (context != null) { context.setState(SessionState.STATE_END); context.closeReceiveStreams(); }
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        SessionContext sessionContext = getSessionContext(session);
        BinkFrame data = (BinkFrame) message;
        if (data.getData().length == 0) return;
        Command command = null;
        BinkData binkData = null;
        Mina3Session mina3Session = new Mina3Session(session);
        try {
            binkData = BinkFrame.toBinkData(data);
            command = new CommandFactory().createCommand(sessionContext, binkData);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            sessionContext.setState(SessionState.STATE_ERR);
            sessionContext.setLastErrorMessage(ex.getMessage());
            Command error = new ERRCommand();
            try {
                error.send(mina3Session, sessionContext);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                exceptionCaught(session, e);
            }
            sessionContext.sendEvent(new DisconnectedEvent(sessionContext));
            session.close(false);
            exceptionCaught(session, ex);
        }
        if (command != null) {
            log.debug("Get command: {}", BinkCommand.findCommand(binkData.getCommand()));
            log.debug("Command data: {}", new String(binkData.getData(), java.nio.charset.StandardCharsets.UTF_8));
            try {
                command.handle(mina3Session, sessionContext, new String(binkData.getData(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                exceptionCaught(session, e);
            }
        } else {
            try { org.fidonet.binkp.common.io.ReceiveTransfer.accept(mina3Session, sessionContext, binkData.getData()); }
            catch (Exception e) { exceptionCaught(session, e); }
        }
    }


    @Override
    public void exceptionCaught(IoSession session, Exception cause) {
        SessionContext sessionContext = getSessionContext(session);
        log.error(cause.getMessage(), cause);
        sessionContext.setState(SessionState.STATE_ERR);
        session.close(false);
    }
}
