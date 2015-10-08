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

package org.fidonet.binkp.mina2;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.types.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/20/12
 * Time: 10:30 AM
 */
public class Runner {

    private ExecutorService threadPoolExecutor;
    private Client client;
    private Server server;

    private static final Logger logger = LoggerFactory.getLogger(Runner.class);

    public Runner() {
        threadPoolExecutor = new ThreadPoolExecutor(3, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());
    }

    public void poll(final Link link, final SessionContext context) throws Exception {
        Runnable clientRun = new Runnable() {

            public void waitSessionFinish() throws InterruptedException {
                for (; ; ) {
                    Thread.sleep(500);
                    if (context.isReceivingIsFinish() && context.isSendingIsFinish() ||
                            context.getState().equals(SessionState.STATE_ERR) ||
                            context.getState().equals(SessionState.STATE_END)) {
                        break;
                    }
                }
            }

            @Override
            public void run() {
                try {
                    client = new Client(link);
                    client.run(context);
                    if (client.isConnect()) {
                        waitSessionFinish();
                    }
                } catch (Exception e) {
                    logger.error("Client unexpectedly stops", e);
                } finally {
                    client.stop();
                    client = null;
                }
            }
        };

        threadPoolExecutor.submit(clientRun);
    }

    public void bindServer(final SessionContext context, final int port) {
        Runnable runServer = new Runnable() {
            @Override
            public void run() {
                server = new Server(port);
                try {
                    server.run(context);
                } catch (Exception e) {
                    logger.error("Server unexpectedly stops", e);
                }
            }
        };
        threadPoolExecutor.submit(runServer);
    }

    public void shutdown() {
        if (server != null) {
            server.stop();
        }
        threadPoolExecutor.shutdown();
    }

}
