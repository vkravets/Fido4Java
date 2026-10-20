package org.fidonet.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

class StorageConfigurationTest {
    @Test
    void rejectsDuplicateAndMalformedEndpointsBeforeOpeningStorage() throws Exception {
        IConfig duplicate = config("StorageEndpoint=mail|h2|one\nStorageEndpoint=mail|h2|two\n");
        assertThrows(IllegalArgumentException.class,
                     () -> StorageConfiguration.open(duplicate, "mail", Path.of("unused")));
        IConfig malformed = config("StorageEndpoint=mail|h2\n");
        assertThrows(IllegalArgumentException.class,
                     () -> StorageConfiguration.open(malformed, "mail", Path.of("unused")));
    }

    @Test
    void unknownProviderNeverFallsBackToTheDefaultDatabase() throws Exception {
        IConfig config = config("StorageEndpoint=directory|unavailable|opaque-location\n");
        assertTrue(StorageConfiguration.configured(config, "directory"));
        assertFalse(StorageConfiguration.configured(config, "accounts"));
        assertThrows(IOException.class,
                     () -> StorageConfiguration.open(config, "directory", Path.of("unused")));
    }

    private static IConfig config(String text) throws Exception {
        BaseConfig config = new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }
}
