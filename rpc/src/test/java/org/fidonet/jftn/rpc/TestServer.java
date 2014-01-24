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
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    @Before
    public void setUp() {
        server = new Server(TEST_SERVER_PORT, new ServerHandler() {
            @Override
            public Api.Iface getApiHandler() {
                return new Api.Iface() {

                    @Override
                    public Response ping() throws TException {
                        return Response.OK;
                    }

                    @Override
                    public VersionResponse getVersion() throws TException {
                        return new VersionResponse(Response.OK, "1.0.1");
                    }
                };
            }

            @Override
            public LoginService.Iface getLoginHandler() {
                return new LoginService.Iface() {
                    @Override
                    public LoginResponse login(Credential cred) throws TException {
                        TestCase.assertEquals(new Credential("test", "test"), cred);
                        return new LoginResponse(Response.OK, "session_id_test_1");
                    }

                    @Override
                    public void logout(String session_id) throws TException {
                        TestCase.assertEquals("session_id_test_1", session_id);
                    }
                };
            }

            @Override
            public MessageService.Iface getMessageHandler() {
                return new MessageService.Iface() {
                    @Override
                    public List<Message> getMessages(String area) throws TException {
                        TestCase.assertEquals("test_area", area);
                        return new ArrayList<Message>();
                    }
                };
            }

            @Override
            public StatisticService.Iface getStatisticsHandler() {
                return new StatisticService.Iface() {
                    @Override
                    public JavaStatistics getJavaStatistics() throws TException {
                        return new JavaStatistics("1.7", 1024L, 512L, (short) 10, (short) 2);
                    }

                    @Override
                    public NodeStatistics getNodeStatistics() throws TException {
                        return new NodeStatistics("1.0", (short) 13, 100, 23098, 101);
                    }
                };
            }
        });
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
            TestCase.assertEquals(new VersionResponse(Response.OK, "1.0.1"), apiClient.getVersion());
            TestCase.assertEquals(Response.OK, apiClient.ping());
            LoginResponse login = logClient.login(new Credential("test", "test"));
            TestCase.assertEquals(new LoginResponse(Response.OK, "session_id_test_1"), login);

            Api.AsyncClient apiAsyncClient = new Api.AsyncClient(new TProtocolFactory() {
                @Override
                public TProtocol getProtocol(TTransport tTransport) {
                    return new TMultiplexedProtocol(new TBinaryProtocol(tTransport), "api");
                }
            },
                    new TAsyncClientManager(), new TNonblockingSocket("127.0.0.1", 30001));
            apiAsyncClient.ping(new AsyncMethodCallback<Api.AsyncClient.ping_call>() {
                @Override
                public void onComplete(Api.AsyncClient.ping_call response) {
                    try {
                        TestCase.assertEquals(Response.OK, response.getResult());
                    } catch (TException e) {
                        this.onError(e);
                    }
                }

                @Override
                public void onError(Exception e) {
                    TestCase.fail(e.getMessage());
                }
            });

            LoginService.AsyncClient loginAsyncClient = new LoginService.AsyncClient(new TProtocolFactory() {
                @Override
                public TProtocol getProtocol(TTransport tTransport) {
                    return new TMultiplexedProtocol(new TBinaryProtocol(tTransport), "login");
                }
            },
                    new TAsyncClientManager(), new TNonblockingSocket("127.0.0.1", TEST_SERVER_PORT));
            loginAsyncClient.login(new Credential("test", "test"), new AsyncMethodCallback<LoginService.AsyncClient.login_call>() {
                @Override
                public void onComplete(LoginService.AsyncClient.login_call login_call) {
                    try {
                        TestCase.assertEquals(new LoginResponse(Response.OK, "session_id_test_1"), login_call.getResult());
                    } catch (TException e) {
                        this.onError(e);
                    }
                }

                @Override
                public void onError(Exception e) {
                    TestCase.fail(e.getMessage());
                }
            });

            Thread.sleep(1000);
        } catch (TException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (transport != null) {
                transport.close();
            }
        }
    }

}
