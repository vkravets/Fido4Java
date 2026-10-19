package org.fidonet.config;

import java.util.Locale;

/** Storage and service capabilities; delivery transport is selected independently. */
public enum RuntimeProfile {
    FULL, HEADLESS, LEGACY, TRANSIT;

    public static RuntimeProfile read(JFtnConfig config) {
        try {
            return valueOf(config.getValue("RuntimeProfile",
                                           System.getProperty("jftn.profile", "full"))
                                 .trim()
                                 .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("RuntimeProfile must be full, headless, legacy or transit",
                                               failure);
        }
    }

    public boolean usesSql() {
        return this == FULL || this == HEADLESS;
    }

    public boolean hasUi() {
        return this == FULL;
    }

    public void validate(JFtnConfig config) {
        if (this != FULL && Boolean.parseBoolean(config.getValue("RpcEnabled", "false"))) {
            throw new IllegalArgumentException("RPC/UI require RuntimeProfile=full");
        }
        if (usesSql()) {
            return;
        }
        require(config, "EchoSubscriptions", "config");
        require(config, "NetmailRoutes", "config");
        require(config, "RobotAccessSource", "files");
        require(config, "FileEchoSource", "files");
        require(config, "NodeDirectoryEnabled", "false");
        require(config, "JamBridgeEnabled", "false");
        require(config, "AllowVirtualOutbound", "false");
    }

    private static void require(JFtnConfig config, String key, String expected) {
        if (!config.getValue(key, expected).equalsIgnoreCase(expected)) {
            throw new IllegalArgumentException(key
                                               + " must be "
                                               + expected
                                               + " in SQL-free profiles");
        }
    }
}
