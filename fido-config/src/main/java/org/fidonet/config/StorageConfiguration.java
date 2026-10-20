package org.fidonet.config;

import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** File-only backend selection. Locations are opaque to functional plugins. */
public final class StorageConfiguration {
    private StorageConfiguration() {
    }

    public static StorageSession open(IConfig config, String name, Path fallback)
            throws IOException {
        String[] endpoint = endpoint(config, name);
        if (endpoint == null) {
            return StorageServices.file(fallback);
        }
        return StorageServices.shared(endpoint[1], endpoint[2]);
    }

    public static boolean configured(IConfig config, String name) {
        return endpoint(config, name) != null;
    }

    public static boolean exists(IConfig config, String name, Path fallback) throws IOException {
        String[] endpoint = endpoint(config, name);
        return endpoint == null
                ? StorageServices.exists("h2", fallback.toAbsolutePath().normalize().toString())
                : StorageServices.exists(endpoint[1], endpoint[2]);
    }

    private static String[] endpoint(IConfig config, String name) {
        List<String> values = config.getValuesAsList("StorageEndpoint");
        Map<String, String[]> endpoints = new HashMap<>();
        for (String value : values == null ? List.<String>of() : values) {
            String[] fields = value.split("\\|", 3);
            if (fields.length != 3 || !fields[0].matches("[a-z][a-z0-9-]*")
                    || !fields[1].matches("[a-z][a-z0-9-]*") || fields[2].isBlank()) {
                throw new IllegalArgumentException("StorageEndpoint=name|provider|location");
            }
            if (endpoints.put(fields[0], fields) != null) {
                throw new IllegalArgumentException("Duplicate storage endpoint: " + fields[0]);
            }
        }
        return endpoints.get(name);
    }
}
