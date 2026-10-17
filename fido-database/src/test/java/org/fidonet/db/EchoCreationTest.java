package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.db.objects.Subscription;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

class EchoCreationTest {
    @Test
    void createdAreaAndGrantTogetherAndRolledBackIfGrantFailed() throws Exception {
        OrmManager orm = new OrmManager("jdbc:h2:mem:echoCreation" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            Link first = new Link("1:500/101,1:500/100,secret,localhost");
            Link second = new Link("1:500/102,1:500/100,secret,localhost");
            base.configureLink(first, "secret");
            base.configureLink(second, "secret");
            assertTrue(base.createEchoFromLink(first, "NEW.ECHO", AccessLevel.WRITE));
            assertFalse(base.createEchoFromLink(second, "new.echo", AccessLevel.BOTH));
            assertTrue(base.canWriteEcho(first, "new.echo"));
            assertFalse(base.canReadEcho(first, "new.echo"));
            assertFalse(base.canWriteEcho(second, "new.echo"));
            assertThrows(IllegalArgumentException.class,
                         () -> base.createEchoFromLink(first, "bad.echo", AccessLevel.READ));
            orm.getDao(Subscription.class).executeRaw("DROP TABLE subscription");
            assertThrows(IllegalStateException.class,
                         () -> base.createEchoFromLink(first, "rollback.echo", AccessLevel.BOTH));
            assertFalse(base.isAreaExists("rollback.echo"));
        } finally {
            base.close();
        }
    }
}
