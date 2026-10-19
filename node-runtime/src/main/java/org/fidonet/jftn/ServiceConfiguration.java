package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.plugins.ServiceContext;

import java.nio.file.Files;

/** Configuration is optional during pre-storage bootstrap and explicit during normal startup. */
public final class ServiceConfiguration {
    private ServiceConfiguration() {
    }

    public static JFtnConfig read(ServiceContext context) throws Exception {
        JFtnConfig active = context.find(JFtnConfig.class).orElse(null);
        if (active != null) {
            return active;
        }
        if (!Files.isRegularFile(context.configuration())) {
            return null;
        }
        JFtnConfig config = new JFtnConfig();
        config.load(context.configuration().toString());
        return config;
    }

    public static boolean flag(JFtnConfig config, String name, boolean fallback) {
        String value = config.getValue(name, Boolean.toString(fallback)).trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(name + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
