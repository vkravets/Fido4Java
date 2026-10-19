package org.fidonet.config;

import java.util.Locale;

/** Storage requirements and default package composition; optional services are discovered separately. */
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

    public boolean includesUiByDefault() {
        return this == FULL;
    }

    public void validate(JFtnConfig config) {
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
