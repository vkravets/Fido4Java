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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/24/14
 * Time: 6:31 PM
 */
public class ServerHandlerTestMock implements ServerHandler {
    @Override
    public Api.Iface getApiHandler() {
        return new Api.Iface() {

            @Override
            public Response ping() throws TException {
                System.out.println("[SERVER] Api.ping()");
                return Response.OK;
            }

            @Override
            public VersionResponse getVersion() throws TException {
                System.out.println("[SERVER] Api.getVersion()");
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
                System.out.println("[SERVER] LoginService.login()");
                return new LoginResponse(Response.OK, "session_id_test_1");
            }

            @Override
            public void logout(String session_id) throws TException {
                TestCase.assertEquals("session_id_test_1", session_id);
                System.out.println("[SERVER] LoginService.logout()");
            }
        };
    }

    @Override
    public MessageService.Iface getMessageHandler() {
        return new MessageService.Iface() {
            @Override
            public List<Message> getMessages(String area) throws TException {
                TestCase.assertEquals("test_area", area);
                System.out.println("[SERVER] MessageService.getMessages()");
                return new ArrayList<Message>();
            }
        };
    }

    @Override
    public StatisticService.Iface getStatisticsHandler() {
        return new StatisticService.Iface() {
            @Override
            public JavaStatistics getJavaStatistics() throws TException {
                System.out.println("[SERVER] StatisticService.getJavaStatistics()");
                return new JavaStatistics("1.7", 1024L, 512L, (short) 10, (short) 2);
            }

            @Override
            public NodeStatistics getNodeStatistics() throws TException {
                System.out.println("[SERVER] StatisticService.getNodeStatistics()");
                return new NodeStatistics("1.0", (short) 13, 100, 23098, 101);
            }
        };
    }
}
