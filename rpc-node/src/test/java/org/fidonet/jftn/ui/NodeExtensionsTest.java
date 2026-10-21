package org.fidonet.jftn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.jftn.plugins.ui.SettingsExtension;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class NodeExtensionsTest {
    @Test
    void routesOnlyToAvailableProvidersAndBoundsRequestsBeforeDispatch() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        SettingsExtension provider = new SettingsExtension() {
            @Override
            public String id() {
                return "example";
            }

            @Override
            public View describe() {
                return new View("example", "Example", "database", List.of());
            }

            @Override
            public void apply(String section, Map<String, String> values) {
                writes.incrementAndGet();
            }
        };
        assertEquals("example", NodeExtensions.describe(List.of(provider)).get(0).getId());
        assertThrows(IllegalArgumentException.class,
                     () -> NodeExtensions.save(List.of(provider), "absent", "settings", Map.of()));
        assertThrows(IllegalArgumentException.class,
                     () -> NodeExtensions.save(List.of(provider),
                                               "example",
                                               "settings",
                                               Map.of("field", "x".repeat(4097))));
        assertEquals(0, writes.get());
        NodeExtensions.save(List.of(provider), "example", "settings", Map.of("field", "value"));
        assertEquals(1, writes.get());
    }
}
