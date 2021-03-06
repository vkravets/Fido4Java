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

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.types.Link;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 5/5/18
 * Time: 23:29
 */
public class AbstractServerTest {
    protected static final StationConfig config;
    static {
        config = new StationConfig(
            "Test Station",
            "Vasya Pupkin",
            "Odessa, Ukraine",
            "BINKP",
            "2:467/10");
        config.setCryptMode(true);
//        config.setNRMode(true);
    }

    protected static final LinksInfo links = new TestLinksInfo().getLinksInfo();
    protected static final SessionContext sessionContext = new SessionContext(config, links);

    public void waitSessionFinish(SessionContext context) throws InterruptedException {
        for (; ; ) {
            Thread.sleep(500);
            if (context.isReceivingIsFinish() && context.isSendingIsFinish() ||
                    context.getState().equals(SessionState.STATE_ERR) ||
                    context.getState().equals(SessionState.STATE_END)) {
                break;
            }
        }
    }

    private static class TestLinksInfo {

        private final LinksInfo linksInfo;

        public TestLinksInfo() {
            final List<Link> links = new ArrayList<Link>();
            links.add(new Link("2:467/10,2:467/10,password,localhost").withMD(true));
            links.add(new Link("2:467/10.1,2:467/10,client_password,localhost").withMD(true));
            links.add(new Link("2:467/10.99,2:467/10,client_password99,localhost").withMD(true));
            links.add(new Link("2:467/10.999,2:467/10,client_password999,localhost").withMD(false));
            this.linksInfo = new LinksInfo(links);
        }

        public LinksInfo getLinksInfo() {
            return linksInfo;
        }
    }
}
