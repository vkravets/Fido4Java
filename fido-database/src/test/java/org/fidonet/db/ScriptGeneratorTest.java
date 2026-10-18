package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;

import com.j256.ormlite.dao.BaseDaoImpl;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScriptGeneratorTest {
    @Test
    public void generatedSchemaCanBeDroppedAndRecreatedInH2() throws Exception {
        String url = "jdbc:h2:mem:generatedSchema" + System.nanoTime();
        OrmManager manager = new OrmManager(url);
        manager.connect();
        try (Connection connection = DriverManager.getConnection(url)) {
            List<BaseDaoImpl<?, ?>> daos = Arrays.asList((BaseDaoImpl<?, ?>) manager.getDao(Echoarea.class),
                                                         (BaseDaoImpl<?, ?>) manager.getDao(ConfigurationLink.class));
            String create = DatabaseSchemaScriptGenerator.generateCreateScript(manager.connectionSource,
                                                                               daos);
            String drop = DatabaseSchemaScriptGenerator.generateDropScript(manager.connectionSource,
                                                                           daos);
            assertTrue(manager.getDao(Echoarea.class).isTableExists());
            RunScript.execute(connection, new StringReader(drop));
            assertFalse(manager.getDao(Echoarea.class).isTableExists());
            assertFalse(manager.getDao(ConfigurationLink.class).isTableExists());
            RunScript.execute(connection, new StringReader(create));
            assertTrue(manager.getDao(Echoarea.class).isTableExists());
            assertTrue(manager.getDao(ConfigurationLink.class).isTableExists());
            Echoarea area = new Echoarea();
            area.setName("RESTORED");
            manager.getDao(Echoarea.class).create(area);
            assertEquals(1, manager.getDao(Echoarea.class).countOf());
        } finally {
            manager.disconnect();
        }
    }

    @Test
    public void emptySchemaProducesEmptyScripts() throws Exception {
        OrmManager manager = new OrmManager("jdbc:h2:mem:emptySchema" + System.nanoTime());
        manager.connect();
        try {
            assertEquals("",
                         DatabaseSchemaScriptGenerator.generateCreateScript(manager.connectionSource,
                                                                            Collections.emptyList()));
            assertEquals("",
                         DatabaseSchemaScriptGenerator.generateDropScript(manager.connectionSource,
                                                                          Collections.emptyList()));
        } finally {
            manager.disconnect();
        }
    }
}
