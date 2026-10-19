package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.engine.script.ScriptApi;
import org.fidonet.jftn.plugins.ui.SettingsExtension;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandRegistry;
import org.fidonet.jftn.share.ScriptHookRegistry;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.NetmailHandler;
import org.fidonet.jftn.tosser.RobotDelivery;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** A node-owned script runtime, created and closed under the mail processing lock. */
public final class MailAutomationRuntime extends ScriptApi implements MailProcessingExtension {
    @FunctionalInterface
    public interface FileEchoHandler {
        void accept(FileEchoEvent event) throws IOException;
    }

    private final List<FileEchoHandler> fileEchoHandlers = new ArrayList<>();

    /** Available during registration of an external script; capture it for later callbacks. */
    public Path getScriptDirectory() {
        return scripts.registrationDirectory();
    }

    public void onFileEcho(FileEchoHandler handler) {
        fileEchoHandlers.add(handler);
    }

    public void publishFileEcho(FileEchoEvent event) throws IOException {
        for (FileEchoHandler handler : fileEchoHandlers) {
            handler.accept(event);
        }
    }
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
    private final MailStorage database;
    private final ScriptState state;
    private final List<NetmailHandler> handlers = new ArrayList<>();
    private final List<ScanHandler> scans = new ArrayList<>();
    private final List<FileHandler> fileHandlers = new ArrayList<>();
    private final ExternalMailScripts scripts;
    private final RobotAccess robotAccess;
    private final DirectoryImportService directoryImports;

    public DirectoryImportService getDirectoryImports() {
        return directoryImports;
    }
    private FileEchoService fileEcho;
    private final List<SettingsExtension> settingsExtensions = new ArrayList<>();

    public List<SettingsExtension> getSettingsExtensions() {
        return List.copyOf(settingsExtensions);
    }

    public void registerSettingsExtension(SettingsExtension extension) {
        if (settingsExtensions.stream()
                              .anyMatch(existing -> existing.id().equals(extension.id()))) {
            throw new IllegalArgumentException("Duplicate settings extension");
        }
        settingsExtensions.add(extension);
    }

    public MailAutomationRuntime(JFtnConfig config, MailStorage database, Tosser tosser)
            throws IOException {
        this(config, database, tosser, ServiceLoader.load(MailScriptExtension.class));
    }

    public MailAutomationRuntime(JFtnConfig config,
                                 MailStorage database,
                                 Tosser tosser,
                                 Iterable<MailScriptExtension> extensions)
            throws IOException {
        this(config, database, tosser, extensions, null);
    }

    public MailAutomationRuntime(JFtnConfig config,
                                 MailStorage database,
                                 Tosser tosser,
                                 Iterable<MailScriptExtension> extensions,
                                 DirectoryImportService directoryImports)
            throws IOException {
        super(null, new ScriptHookRegistry(), new CommandRegistry(new CommandCollection()));
        this.directoryImports = directoryImports;
        this.tosser = tosser;
        this.database = database;
        setConfig(config);
        robotAccess = new RobotAccess(config, database);
        state = new ScriptState(Path.of(config.getTmpDir(), "scripts"));
        this.scripts = new ExternalMailScripts();
        List<String> scripts = config.getValuesAsList("MailScript");
        try {
            for (MailScriptExtension extension : extensions) {
                extension.register(this);
            }
            if (scripts != null) {
                for (String script : scripts) {
                    this.scripts.load(script, this);
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

    public Tosser getTosser() {
        return tosser;
    }

    public RobotDelivery getRobotDelivery() {
        return new RobotDelivery((JFtnConfig) getConfig());
    }

    public RemoteRobotRequests getRemoteRobots() {
        return new RemoteRobotRequests((JFtnConfig) getConfig(), tosser, state);
    }

    public RobotAccess getRobotAccess() {
        return robotAccess;
    }

    public FileEchoService getFileEcho() {
        return fileEcho;
    }

    public void registerFileEcho(FileEchoService service) {
        if (fileEcho != null) {
            throw new IllegalStateException("Multiple fileecho providers");
        }
        fileEcho = service;
    }

    public MailStorage getStorage() {
        return database;
    }

    public MailDatabaseFacade getDatabase() {
        return (MailDatabaseFacade) database;
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

    /** Invoked by the fileecho provider after authentication, CRC checks and durable storage. */
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
        try {
            scripts.close(this);
        } catch (IOException cleanup) {
            failure = cleanup;
        }
        for (NetmailHandler handler : handlers) {
            tosser.removeNetmailHandler(handler);
        }
        handlers.clear();
        scans.clear();
        fileHandlers.clear();
        fileEchoHandlers.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
