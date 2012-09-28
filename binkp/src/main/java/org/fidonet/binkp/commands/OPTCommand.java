package org.fidonet.binkp.commands;

import org.apache.mina.core.session.IoSession;
import org.fidonet.binkp.SessionContext;
import org.fidonet.binkp.codec.TrafficCrypter;
import org.fidonet.binkp.config.Password;
import org.fidonet.binkp.config.ServerRole;
import org.fidonet.binkp.crypt.StandardDecrypt;
import org.fidonet.binkp.crypt.StandardEncrypt;

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
    protected void handleCommand(IoSession session, SessionContext sessionContext, String commandArgs) throws Exception {
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
            } else if (token.equals("NR")) {
                sessionContext.setNRMode(true);
            } else if (token.equals("CRYPT")) {
                sessionContext.setCryptMode(true);
                Password password = sessionContext.getPassword();
                boolean isMD5 = password.isCrypt() && password.getMessageDigest().getAlgorithm().equals("MD5");
                if (isMD5) {
                    TrafficCrypter trafficCrypter = (TrafficCrypter) session.getAttribute(TrafficCrypter.TRAFFIC_CRYPTER_KEY);
                    char[] pass = password.getPassword().toCharArray();
                    boolean isClient = sessionContext.getServerRole().equals(ServerRole.CLIENT);
                    trafficCrypter.setDecrypt(new StandardDecrypt(pass, isClient));
                    trafficCrypter.setEncrypt(new StandardEncrypt(pass, isClient));
                }
                System.out.println("Remote requests CRYPT mode");
            }
        }
    }
}
