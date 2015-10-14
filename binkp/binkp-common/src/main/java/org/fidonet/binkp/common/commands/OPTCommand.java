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
import org.fidonet.binkp.common.codec.TrafficCrypter;
import org.fidonet.binkp.common.config.Password;
import org.fidonet.binkp.common.config.ServerRole;
import org.fidonet.binkp.common.crypt.StandardDecrypt;
import org.fidonet.binkp.common.crypt.StandardEncrypt;
import org.fidonet.binkp.common.commands.share.Command;
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
        if (sessionContext.getStationConfig().isNRMode()) args += "NR ";
        if (sessionContext.getStationConfig().isCryptMode()) args += "CRYPT ";
        // TODO Add ND mode
        return args.trim();
    }

    @Override
    protected void handleCommand(Session session, SessionContext sessionContext, String commandArgs) throws Exception {
        String[] tokens = commandArgs.trim().split(" ");
        for (String token : tokens) {
            if (token.startsWith("CRAM")) {
                String[] cramTokens = commandArgs.split("-");
                String cryptType = cramTokens[1];
                MessageDigest md = MessageDigest.getInstance(cryptType);
                Password password = sessionContext.getPassword();
                password.setCrypt(true);
                password.setMd(md);
                password.setKey(cramTokens[2]);
                Command opt_md5 = new CramOPTCommand(MessageDigest.getInstance("MD5"));
                opt_md5.send(session, sessionContext);
            } else if (token.equals("NR")) {
                sessionContext.setNRMode(true);
            } else if (token.equals("CRYPT")) {
                sessionContext.setCryptMode(true);
                Password password = sessionContext.getPassword();
                boolean isMD5 = password.isCrypt() && password.getMessageDigest().getAlgorithm().equals("MD5");
                if (isMD5) {
                    TrafficCrypter trafficCrypter = session.getTrafficCrypter();
                    char[] pass = password.getPassword().toCharArray();
                    boolean isClient = sessionContext.getServerRole().equals(ServerRole.CLIENT);
                    trafficCrypter.setDecrypt(new StandardDecrypt(pass, isClient));
                    trafficCrypter.setEncrypt(new StandardEncrypt(pass, isClient));
                }
                log.info("Remote requests CRYPT mode");
            }
        }
    }
}
