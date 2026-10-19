package org.fidonet.jftn.ui;

import org.fidonet.config.JFtnConfig;

import java.util.List;
import java.util.Set;

/** Common configuration parsing; transport-specific options stay in their factories. */
public final class RpcConfiguration {
    private RpcConfiguration() {
    }

    public static boolean enabled(JFtnConfig config, String key) {
        String value = config.getValue(key, "false").trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public static Set<String> origins(JFtnConfig config, String key) {
        List<String> values = config.getValuesAsList(key);
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
