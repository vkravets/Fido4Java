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

package org.fidonet.jftn.rpc;

import junit.framework.TestCase;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/24/14
 * Time: 12:49 PM
 */
public class TestServer {

    private Server server;
    private static final int TEST_SERVER_PORT = 30001;
    private final static AtomicInteger pingCurIndex = new AtomicInteger(0);
    private final static AtomicInteger loginCurIndex = new AtomicInteger(0);

    @Before
    public void setUp() {
        server = new Server(TEST_SERVER_PORT, new ServerHandlerTestMock());
        server.run();
        System.out.println("Server Started");
    }

    @After
    public void tearDown() {
        server.stop();
        System.out.println("Server Stopped");
    }

    @Test
    public void testServerAndClient() {
        TTransport transport = null;
        try {
            transport = new TFramedTransport(new TSocket("127.0.0.1", TEST_SERVER_PORT));
            TProtocol protocol = new TBinaryProtocol(transport);
            TMultiplexedProtocol apiProtocol = new TMultiplexedProtocol(protocol, "api");
            TMultiplexedProtocol loginProtocol = new TMultiplexedProtocol(protocol, "login");
            Api.Client apiClient = new Api.Client(apiProtocol);
            LoginService.Client logClient = new LoginService.Client(loginProtocol);
            transport.open();
            System.out.println("[CLIENT] Api.getVersion");
            TestCase.assertEquals(new VersionResponse(Response.OK, "1.0.1"), apiClient.getVersion());
            TestCase.assertEquals(Response.OK, apiClient.ping());
            System.out.println("[CLIENT] LoginService.login()");
            LoginResponse login = logClient.login(new Credential("test", "test"));
            TestCase.assertEquals(new LoginResponse(Response.OK, "session_id_test_1"), login);
            System.out.println("[CLIENT] LoginService.logout()");
            logClient.logout("session_id_test_1");

        } catch (TException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testASyncClient() {
        try {

            for (int k = 0; k < 50; k++) {
                final int i = k;
                TNonblockingSocket transportApi = new TNonblockingSocket("127.0.0.1", TEST_SERVER_PORT);
                Api.AsyncClient apiAsyncClient = new Api.AsyncClient(
                        new TMultiplexedProtocolFactory("api"),
                        new TAsyncClientManager(),
                        transportApi);
                // Make async call of ping RPC API
                apiAsyncClient.ping(new AsyncMethodCallback<Api.AsyncClient.ping_call>() {
                    @Override
                    public void onComplete(Api.AsyncClient.ping_call response) {
                        try {
                            System.out.println("[CLIENT] Async Api.ping() " + i);
                            TestCase.assertEquals(Response.OK, response.getResult());
                        } catch (TException e) {
                            this.onError(e);
                        }
                        synchronized (pingCurIndex) {
                            pingCurIndex.set(pingCurIndex.incrementAndGet());
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        TestCase.fail(e.getMessage());
                    }
                });


                TNonblockingSocket transportLogin = new TNonblockingSocket("127.0.0.1", TEST_SERVER_PORT);
                LoginService.AsyncClient loginAsyncClient = new LoginService.AsyncClient(
                        new TMultiplexedProtocolFactory("login"),
                        new TAsyncClientManager(),
                        transportLogin);

                // Make async call of login RPC API
                loginAsyncClient.login(new Credential("test", "test"), new AsyncMethodCallback<LoginService.AsyncClient.login_call>() {
                    @Override
                    public void onComplete(LoginService.AsyncClient.login_call login_call) {
                        try {
                            System.out.println("[CLIENT] Async LoginService.login() " + i);
                            TestCase.assertEquals(new LoginResponse(Response.OK, "session_id_test_1"), login_call.getResult());
                        } catch (TException e) {
                            this.onError(e);
                        }
                        synchronized (loginCurIndex) {
                            loginCurIndex.set(loginCurIndex.incrementAndGet());
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        e.printStackTrace();
                        TestCase.fail(e.getMessage());
                    }
                });
            }
            Thread.sleep(6000);
            TestCase.assertEquals(loginCurIndex.get(), 50);
            TestCase.assertEquals(pingCurIndex.get(), 50);
        } catch (TException e) {
            e.printStackTrace();
            TestCase.fail(e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
            TestCase.fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            TestCase.fail(e.getMessage());
        }

    }

}
