package org.fidonet.binkp.commands;

import org.fidonet.binkp.SessionContext;

import java.security.MessageDigest;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/23/12
 * Time: 5:04 PM
 */
public class CramOPTCommand extends OPTCommand {

    private MessageDigest messageDigest;

    public CramOPTCommand(MessageDigest messageDigest) {
        this.messageDigest = messageDigest;
        messageDigest.reset();
        messageDigest.update(String.format("%d%d", System.currentTimeMillis(),
                System.nanoTime()).getBytes());

    }

    @Override
    protected String getArguments(SessionContext sessionContext) {
        byte[] bufKey = messageDigest.digest();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            builder.append(String.format("%02x", bufKey[i]));
        }
        String key = builder.toString();
        return String.format("CRAM-%s-%s", messageDigest.getAlgorithm(), key);
    }
}
