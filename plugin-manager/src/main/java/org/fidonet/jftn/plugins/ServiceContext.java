package org.fidonet.jftn.plugins;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit node capabilities, supplied by the host rather than looked up through globals. */
public final class ServiceContext {
    private final Path configuration;
    private final Map<Class<?>, Object> services = new LinkedHashMap<>();

    public ServiceContext(Path configuration) {
        this.configuration = configuration.toAbsolutePath().normalize();
    }

    public Path configuration() {
        return configuration;
    }

    public <T> ServiceContext provide(Class<T> type, T value) {
        if (value == null || !type.isInstance(value)) {
            throw new IllegalArgumentException("Invalid service: " + type.getSimpleName());
        }
        if (services.putIfAbsent(type, value) != null) {
            throw new IllegalArgumentException("Duplicate service: " + type.getSimpleName());
        }
        return this;
    }

    public <T> T require(Class<T> type) {
        Object value = services.get(type);
        if (value == null) {
            throw new IllegalStateException("Required node capability is unavailable: "
                                            + type.getSimpleName());
        }
        return type.cast(value);
    }
}
