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

package org.fidonet.binkp.common.commands;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.protocol.Session;

import java.security.MessageDigest;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:44 PM
 */
public class OPTCommand extends NULCommand {

    @Override
    protected String getPrefix() {
        return "OPT";
    }

    @Override
    protected String getArguments(SessionContext sessionContext) {
        String args = "";
        if (sessionContext.getStationConfig().isNREnabled()
                && sessionContext.getStationConfig().isNRRequested()) {
            args += "NR ";
        }
        if (sessionContext.getStationConfig().isCryptMode()) {
            args += "CRYPT ";
        }
        // TODO Add ND mode
        return args.trim();
    }

    @Override
    protected void handleCommand(Session session, SessionContext sessionContext, String commandArgs)
            throws Exception {
        for (String token : commandArgs.trim().split("\\s+")) {
            handleOption(sessionContext, token);
        }
    }

    private void handleOption(SessionContext context, String token) throws Exception {
        if (token.startsWith("CRAM") && context.getLinksInfo().getCurLink() != null
                && context.getLinksInfo().getCurLink().isMD()) {
            configureCram(context, token);
            return;
        }
        if (token.equals("NR")) {
            if (context.getStationConfig().isNREnabled()) {
                context.setNRMode(true);
            }
            return;
        }
        if (token.equals("CRYPT")) {
            configureCrypt(context);
        }
    }

    private void configureCram(SessionContext context, String token) throws Exception {
        String[] cramTokens = token.split("-", 3);
        if (cramTokens.length != 3 || !cramTokens[2].matches("(?i)(?:[0-9a-f]{2})+")) {
            throw new IllegalArgumentException("Invalid CRAM challenge");
        }
        String cryptType = supportedDigest(cramTokens[1]);
        if (cryptType == null) {
            return;
        }
        MessageDigest md = MessageDigest.getInstance(cryptType);
        Password password = context.getPassword();
        password.setCrypt(true);
        password.setMd(md);
        password.setKey(cramTokens[2]);
        context.setMessageDigest(md);
        context.setPasswordKey(cramTokens[2]);
    }

    private String supportedDigest(String offeredDigests) {
        for (String offered : offeredDigests.split("/")) {
            if (offered.equals("MD5") || offered.equals("SHA1")) {
                return offered;
            }
        }
        return null;
    }

    private void configureCrypt(SessionContext context) {
        if (context.getStationConfig().isCryptMode()) {
            context.setCryptMode(true);
            log.info("Remote requests CRYPT mode");
            return;
        }
        context.setCryptMode(false);
        log.info("Ignore CRYPT mode, doesn't support");
    }
}
