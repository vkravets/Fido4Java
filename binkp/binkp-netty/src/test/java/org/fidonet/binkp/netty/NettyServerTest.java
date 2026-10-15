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

package org.fidonet.binkp.netty;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.netty.plugin.Client;
import org.fidonet.binkp.netty.plugin.Server;
import org.fidonet.binkp.test.AbstractServerTest;
import org.fidonet.types.Link;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 5/5/18
 * Time: 23:09
 */
public class NettyServerTest extends AbstractServerTest {

    private static final Server server = new Server(0);

    @BeforeAll
    public static void startServer() throws Exception {
        server.run(sessionContext);
    }

    @AfterAll
    public static void stopServer() {
        server.stop();
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void test1() throws Exception {
        final Link uplink = new Link("2:467/10,2:467/10.1,client_password,localhost:"
                                     + server.getLocalPort());
        uplink.setMD(true);
        Client client = new Client(uplink, 10);
        final StationConfig stationConfig = new StationConfig("Test",
                                                              "Test Test",
                                                              "Odessa,UA",
                                                              "BINKP",
                                                              "2:467/10.1");
        stationConfig.setCryptMode(true);
        //        stationConfig.setNRMode(true);
        final List<Link> links = new ArrayList<Link>();
        links.add(uplink);

        final SessionContext sessionContext = new SessionContext(stationConfig,
                                                                 new LinksInfo(links));
        runAndWait(client, sessionContext);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void test2() throws Exception {
        final Link uplink = new Link("2:467/10,2:467/10.999,client_password999,localhost:"
                                     + server.getLocalPort());
        uplink.setMD(true);
        Client client = new Client(uplink, 10);
        final StationConfig stationConfig = new StationConfig("Test",
                                                              "Test Test",
                                                              "Odessa,UA",
                                                              "BINKP",
                                                              "2:467/10.999");
        stationConfig.setCryptMode(true);
        //        stationConfig.setNRMode(true);
        final List<Link> links = new ArrayList<Link>();
        links.add(uplink);

        final SessionContext sessionContext = new SessionContext(stationConfig,
                                                                 new LinksInfo(links));
        runAndWait(client, sessionContext);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void serversUseDifferentPortsWhenStartedTogether() throws Exception {
        Server other = new Server(0);
        try {
            other.run(sessionContext);
            Assertions.assertTrue(server.getLocalPort() > 0);
            Assertions.assertTrue(other.getLocalPort() > 0);
            Assertions.assertNotEquals(server.getLocalPort(), other.getLocalPort());
        } finally {
            other.stop();
        }
    }

    private void runAndWait(Client client, SessionContext sessionContext) throws Exception {
        try {
            client.run(sessionContext);
            if (client.isConnect()) {
                waitSessionFinish(sessionContext);
            }
        } finally {
            client.stop();
        }
    }
}
