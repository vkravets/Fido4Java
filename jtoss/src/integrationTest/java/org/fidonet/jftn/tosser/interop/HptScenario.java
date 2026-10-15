package org.fidonet.jftn.tosser.interop;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.jftn.tosser.jam.JamBridge;
import org.fidonet.types.FTNAddr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs inside the container so Java and Husky share the same filesystem and locks. */
final class HptScenario implements AutoCloseable {
    static final FTNAddr LOCAL = new FTNAddr("2:500/102");
    static final FTNAddr PEER = new FTNAddr("2:500/101");
    final Path root;
    final JFtnConfig config;
    final DatabaseManager base;
    final Tosser tosser;
    final JamBridge bridge;
    final JamArea echo;
    final JamArea netmail;

    HptScenario(Path root) throws Exception {
        this.root = root;
        createDirectories();
        config = configuration();
        base = new DatabaseManager(new OrmManager("jdbc:h2:" + root.resolve("database")));
        if (!base.open()) {
            throw new IOException("Cannot open integration SQL database");
        }
        try {
            configureDatabase();
            tosser = new Tosser(config, base);
            bridge = new JamBridge(config, base, base);
            echo = new JamArea(root.resolve("jam/test"), "test", LOCAL);
            netmail = new JamArea(root.resolve("jam/netmail"), null, LOCAL);
        } catch (Exception failure) {
            base.close();
            throw failure;
        }
    }

    private void createDirectories() throws IOException {
        for (String directory : new String[]{"inbound", "protected-inbound", "outbound", "tmp-in",
                "tmp-out", "logs", "dupes", "jam", "state", "jftn-out"}) {
            Files.createDirectories(root.resolve(directory));
        }
    }

    private JFtnConfig configuration() throws Exception {
        String text = "Inbound="
                      + root.resolve("inbound")
                      + "\nTmp="
                      + root.resolve("state")
                      + "\nOutbound="
                      + root.resolve("jftn-out")
                      + "\nEchoPath="
                      + root.resolve("jam")
                      + "\nSysOp=Test Sysop\nSystem=Interop\n"
                      + "Link=2:500/101,2:500/102,secret,localhost\nDefaultRoute=2:500/101\n"
                      + "JamEcho=TEST,"
                      + root.resolve("jam/test")
                      + "\nJamNetmail="
                      + root.resolve("jam/netmail")
                      + "\n";
        Files.writeString(root.resolve("jftn.conf"), text);
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private void configureDatabase() {
        base.configureLink(config.getLink(PEER), "secret");
        base.configureSubscription(config.getLink(PEER), "test", AccessLevel.BOTH);
    }

    @Override
    public void close() {
        base.close();
    }
}
