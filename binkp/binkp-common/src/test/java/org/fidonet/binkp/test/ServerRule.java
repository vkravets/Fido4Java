/*
 * Copyright (c) 2012-2018, Vladimir Kravets
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met: Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the Fido4Java nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.fidonet.binkp.test;

import org.fidonet.binkp.common.ServerConnector;
import org.fidonet.binkp.common.SessionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 5/5/18
 * Time: 22:44
 */
public class ServerRule<T extends ServerConnector> implements TestRule {

    private static final Logger log = LoggerFactory.getLogger(ServerRule.class);

    private final ServerConnector serverConnector;
    private final SessionContext sessionContext;
    private final Boolean isAsync;

    public ServerRule(ServerConnector serverConnector, SessionContext context, Boolean isAsync) {
        this.serverConnector = serverConnector;
        this.sessionContext = context;
        this.isAsync = isAsync;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new ServerStatement(base, this.serverConnector);
    }

    private class ServerStatement extends Statement {

        private final ServerConnector serverConnector;
        private final Statement base;
        private final ThreadPoolExecutor threadPoolExecutor;

        public ServerStatement(Statement base,
                               ServerConnector serverConnector) {
            this.base = base;
            this.serverConnector = serverConnector;
            if (!isAsync) {
                this.threadPoolExecutor = new ThreadPoolExecutor(
                        1, 1,
                        60L, TimeUnit.SECONDS,
                        new SynchronousQueue<Runnable>());
            } else {
                this.threadPoolExecutor = null;
            }
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                if (!isAsync) {
                    threadPoolExecutor.submit(new ServerThread());
                    log.info("Ran in Async mode");
                }
                else {
                    this.serverConnector.run(sessionContext);
                    log.info("Ran in Non Async mode");
                }
                base.evaluate();
            }
            finally {
                this.serverConnector.stop();
                if (!isAsync) {
                    threadPoolExecutor.shutdownNow();
                }
            }
        }
    }

    private class ServerThread implements Runnable {
        @Override
        public void run() {
            try {
                serverConnector.run(sessionContext);
            }
            catch (Exception e) {
                throw new RuntimeException("Server error", e);
            }
            System.out.print("Server stopped");
        }
    }
}
