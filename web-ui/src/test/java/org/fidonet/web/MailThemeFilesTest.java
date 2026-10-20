package org.fidonet.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MailThemeFilesTest {
    @TempDir
    Path root;

    @Test
    void exposesOnlyValidatedThemePropertiesAndNeverWritesOperatorFiles() throws Exception {
        String colors = "\"background\":\"#112233\",\"text\":\"#ffffff\",\"quote1\":\"#ffffff\",\"quote2\":\"#ffffff\",\"quote3\":\"#ffffff\",\"ending\":\"#ffffff\",\"link\":\"#ffffff\",\"selection\":\"#ffffff\",\"caret\":\"#ffffff\",\"search\":\"#ffffff\"";
        String theme = "{\"id\":\"custom\",\"name\":\"My theme\",\"private\":\"not-public\",\"colors\":{"
                + colors + "}}";
        Path file = root.resolve("custom.json");
        Files.writeString(file, theme);
        Files.writeString(root.resolve("broken.json"), "{");
        Files.writeString(root.resolve("unsafe.json"), theme.replace("#112233", "url(bad)"));
        Files.writeString(root.resolve("secrets.conf"), "private");
        Files.createDirectory(root.resolve("nested"));
        Files.writeString(root.resolve("nested/hidden.json"), theme);
        var values = new MailThemeFiles(root).read();
        assertEquals(1, values.size());
        assertEquals(3, values.get(0).size());
        assertEquals("My theme", values.get(0).get("name"));
        assertEquals(theme, Files.readString(file));
        assertEquals(0, new MailThemeFiles(root.resolve("missing")).read().size());
    }
}
