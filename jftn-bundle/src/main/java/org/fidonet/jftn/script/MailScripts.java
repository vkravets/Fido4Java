package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.fidonet.jftn.engine.script.JFtnScriptService;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.NetmailHandler;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A node-owned script runtime, created and closed under the mail processing lock. */
public final class MailScripts extends JFtnScriptService implements MailProcessingExtension {
    @FunctionalInterface
    public interface ScanHandler {
        void run() throws IOException;
    }

    @FunctionalInterface
    public interface FileHandler {
        void accept(String area, Path file, String name, FTNAddr peer, String receipt)
                throws IOException;
    }

    private final Tosser tosser;
    private final DatabaseManager database;
    private final ScriptState state;
    private final List<NetmailHandler> handlers = new ArrayList<>();
    private final List<ScanHandler> scans = new ArrayList<>();
    private final List<FileHandler> fileHandlers = new ArrayList<>();
    private final List<String> loaded = new ArrayList<>();
    private final GroovyScriptManager manager;

    public MailScripts(JFtnConfig config, DatabaseManager database, Tosser tosser)
            throws IOException {
        super(null, new HookInterpreter(), new CommandInterpreter(new CommandCollection()));
        this.tosser = tosser;
        this.database = database;
        setConfig(config);
        state = new ScriptState(Path.of(config.getTmpDir(), "scripts"));
        try {
            manager = new GroovyScriptManager();
        } catch (Exception e) {
            throw new IOException("Cannot initialize Groovy", e);
        }
        List<String> scripts = config.getValuesAsList("MailScript");
        try {
            if (scripts != null) {
                for (String script : scripts) {
                    load(script);
                }
            }
        } catch (Exception failure) {
            try {
                close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new IOException("Cannot load mail scripts", failure);
        }
    }

    private void load(String name) throws Exception {
        if (loaded.contains(name)) {
            throw new IllegalArgumentException("Duplicate MailScript " + name);
        }
        String content;
        if (name.startsWith("classpath:")) {
            try (InputStream input = MailScripts.class.getResourceAsStream("/"
                                                                           + name.substring(10))) {
                if (input == null) {
                    throw new IOException("Missing bundled script " + name);
                }
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            content = Files.readString(Path.of(name));
        }
        manager.registerScript(name, content, this);
        loaded.add(name);
    }

    public Tosser getTosser() {
        return tosser;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public ScriptState getState() {
        return state;
    }

    public void onNetmail(NetmailHandler handler) {
        handlers.add(handler);
        tosser.addNetmailHandler(handler);
    }

    public void onScan(ScanHandler handler) {
        scans.add(handler);
    }

    public List<ReceivedFile> getReceivedFiles() throws IOException {
        return ReceivedFile.list((JFtnConfig) getConfig());
    }

    public FileDeliveryQueue getFileQueue() {
        return new FileDeliveryQueue((JFtnConfig) getConfig());
    }

    public void onFile(FileHandler handler) {
        fileHandlers.add(handler);
    }

    /** Invoked by the TIC script only after authentication, CRC checks and durable storage. */
    public void publishFile(String area, Path file, String name, FTNAddr peer, String receipt)
            throws IOException {
        for (FileHandler handler : fileHandlers) {
            handler.accept(area, file, name, peer, receipt);
        }
    }

    @Override
    public void beforeExport(Tosser ignored) throws IOException {
        for (ScanHandler scan : scans) {
            try {
                scan.run();
            } catch (RuntimeException e) {
                throw new IOException("Mail script scan failed", e);
            }
        }
    }

    @Override
    public void afterExport() {
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (int i = loaded.size() - 1; i >= 0; i--) {
            try {
                manager.unregisterScript(loaded.get(i), this);
            } catch (Exception e) {
                if (failure == null) {
                    failure = new IOException("Cannot unload mail script", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        for (NetmailHandler handler : handlers) {
            tosser.removeNetmailHandler(handler);
        }
        loaded.clear();
        handlers.clear();
        scans.clear();
        fileHandlers.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
