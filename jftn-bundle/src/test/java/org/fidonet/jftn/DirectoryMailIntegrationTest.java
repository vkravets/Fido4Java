package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.db.objects.ConfigurationLink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

class DirectoryMailIntegrationTest {
    @TempDir
    Path temporary;

    private JFtnConfig config(Path source) throws Exception {
        String root = temporary.toString().replace('\\', '/');
        String text = "Inbound=" + root + "/inbound\n" + "Tmp=" + root + "/tmp\n" + "EchoPath="
                + root + "/echo\n" + "Outbound=" + root + "/outbound\n" + "SysOp=Directory Test\n"
                + "Link=1:500/101,1:500/100,secret,localhost\n"
                + "NodeDirectoryEnabled=true\nDirectoryRefreshEnabled=true\nDirectoryLocalSource=nodes\n"
                + "NodeDirectoryPath=" + root + "/catalog\n" + "DirectorySource=nodes|NODELIST|"
                + source.toString().replace('\\', '/') + "|1|500\n";
        JFtnConfig config = new JFtnConfig();
        config.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        return config;
    }

    private Path source(String text) throws Exception {
        Path path = temporary.resolve("nodes.lst");
        Files.writeString(path, text);
        return path;
    }

    private void assertImported() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:"
                                                                 + temporary.resolve("catalog"));
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT system_name FROM directory_entries WHERE source_id='nodes' AND address='1:500/102.0'")) {
            assertTrue(rows.next());
            assertEquals("Directory_Node", rows.getString(1));
        }
    }

    @Test
    void discoversAndRunsDirectoryExtensionWithoutCreatingTrustedLinksForItsEntries()
            throws Exception {
        Path source = source(",102,Directory_Node,City,Test_Sysop,-Unpublished-,300,IBN:node.example\n");
        OrmManager orm = new OrmManager("jdbc:h2:mem:directoryMail" + System.nanoTime());
        MailDatabaseFacade base = new MailDatabaseFacade(orm);
        assertTrue(base.open());
        try (MailRuntime service = new MailRuntime(config(source), base)) {
            assertNotNull(service.getTosser());
            assertImported();
            assertEquals(1, orm.getDao(ConfigurationLink.class).countOf());
        } finally {
            base.close();
        }
    }

    @Test
    void initialImportFailureReleasesTheMailServiceLockAndAllowsRetry() throws Exception {
        Path source = source(",broken\n");
        JFtnConfig config = config(source);
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager("jdbc:h2:mem:directoryRetry"
                                                                        + System.nanoTime()));
        assertTrue(base.open());
        try {
            assertThrows(UncheckedIOException.class, () -> new MailRuntime(config, base));
            Files.writeString(source, ",102,Directory_Node,City,Test_Sysop,-Unpublished-,300\n");
            try (MailRuntime service = new MailRuntime(config, base)) {
                assertNotNull(service.getTosser());
                assertImported();
            }
        } finally {
            base.close();
        }
    }
}
