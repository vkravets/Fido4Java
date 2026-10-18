package org.fidonet.jftn.script;

import org.fidonet.jftn.engine.script.GroovyScriptManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads external Groovy scripts and owns their engines and registration directories. */
final class ExternalMailScripts {
    private final GroovyScriptManager manager;
    private final List<String> loaded = new ArrayList<>();
    private Path loadingDirectory;

    ExternalMailScripts() throws IOException {
        try {
            manager = new GroovyScriptManager();
        } catch (Exception failure) {
            throw new IOException("Cannot initialize Groovy", failure);
        }
    }

    Path registrationDirectory() {
        if (loadingDirectory == null) {
            throw new IllegalStateException("An external script directory is required");
        }
        return loadingDirectory;
    }

    void load(String name, MailAutomationRuntime api) throws Exception {
        if (loaded.contains(name)) {
            throw new IllegalArgumentException("Duplicate MailScript " + name);
        }
        String content;
        if (name.startsWith("classpath:")) {
            try (InputStream input = MailAutomationRuntime.class.getResourceAsStream("/"
                    + name.substring(10))) {
                if (input == null) {
                    throw new IOException("Missing bundled script " + name);
                }
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            content = Files.readString(Path.of(name));
        }
        loadingDirectory = name.startsWith("classpath:")
                ? null
                : Path.of(name).toAbsolutePath().normalize().getParent();
        try {
            manager.registerScript(name, content, api);
        } finally {
            loadingDirectory = null;
        }
        loaded.add(name);
    }

    void close(MailAutomationRuntime api) throws IOException {
        IOException failure = null;
        for (int i = loaded.size() - 1; i >= 0; i--) {
            try {
                manager.unregisterScript(loaded.get(i), api);
            } catch (Exception e) {
                if (failure == null) {
                    failure = new IOException("Cannot unload mail script", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        loaded.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
