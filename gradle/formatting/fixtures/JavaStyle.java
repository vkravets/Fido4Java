package org.fidonet.formatting;

import java.security.MessageDigest;

/**
 * Created by IntelliJ IDEA.
 * Author: Formatting fixture
 * Date: 10/15/26
 */
abstract class JavaStyle {
    boolean cram(String passwordString, Context sessionContext) {
        return passwordString.startsWith("CRAM-"
                                         + sessionContext.getMessageDigest()
                                                         .getAlgorithm()
                                                         .replace("-", "")
                                         + "-");
    }

    void addresses(Context sessionContext) {
        if (sessionContext != null) {
            for (String address : sessionContext.getStationConfig()
                                                .getAddress()
                                                .trim()
                                                .split("\\s+")) {
                consume(address);
            }
        }
    }

    abstract void longMethod(String firstParameterWithALongName,
                             String secondParameterWithALongName,
                             String thirdParameterWithALongName);

    @Deprecated
    abstract void consume(String address);

    interface Context {
        MessageDigest getMessageDigest();

        Station getStationConfig();
    }

    interface Station {
        String getAddress();
    }
}
