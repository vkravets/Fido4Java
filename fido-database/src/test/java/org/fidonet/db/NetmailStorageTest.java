package org.fidonet.db;

import org.fidonet.db.objects.Netmail;
import org.fidonet.db.objects.NetmailDelivery;
import org.fidonet.db.objects.NetmailIndex;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NetmailStorageTest {
    @Test
    public void preservesSearchKeysAndRejectsArchiveDuplicatesAcrossRestart() throws Exception {
        String url = "jdbc:h2:mem:netmailIndex" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        DatabaseManager base = new DatabaseManager(new OrmManager(url));
        assertTrue(base.open());
        Message original = message("Message without MSGID\r");
        try {
            base.saveNetmail(original, "stored", null);
        } finally {
            base.close();
        }
        assertIndexedNetmail(url, original);
        assertIndexedNetmail(url, original);
    }

    private void assertIndexedNetmail(String url, Message original) throws Exception {
        OrmManager orm = new OrmManager(url);
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            assertEquals(1, orm.getDao(NetmailIndex.class).countOf());
            assertEquals(false, base.importArchive(original, "archive"));
            assertEquals(1, orm.getDao(Netmail.class).countOf());
            assertEquals(1, orm.getDao(NetmailDelivery.class).countOf());
            int[] selected = {0};
            base.visitEditorNetmail(original.getFAddr(), message -> selected[0]++);
            assertEquals(1, selected[0]);
            base.visitEditorNetmail(new FTNAddr("2:500/101"), message -> selected[0]++);
            assertEquals(1, selected[0]);
        } finally {
            base.close();
        }
    }

    private Message message(String body) {
        Message message = new Message("Author",
                                      "Reader",
                                      new FTNAddr("2:500/101.1"),
                                      new FTNAddr("2:500/102.2"),
                                      "Subject",
                                      body,
                                      new Date(1000000));
        message.setAttributes(1);
        return message;
    }

    @Test
    public void storesIdempotentlyAndPreservesPointsAttributesAndControlLines() throws Exception {
        OrmManager orm = new OrmManager("jdbc:h2:mem:netmail" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            Message message = message("\1MSGID: 2:500/101.1 unique\rHello\r");
            base.saveNetmail(message, "first", null);
            base.saveNetmail(message, "first", null);
            base.saveNetmail(message("Second\r"), "second", null);
            assertEquals(2, orm.getDao(Netmail.class).countOf());
            assertEquals(2, orm.getDao(NetmailDelivery.class).countOf());
            Message restored = base.getLocalNetmail(new FTNAddr("2:500/102.2")).get(0);
            assertEquals(message.getBody().toString(), restored.getBody().toString());
            assertEquals(message.getFAddr(), restored.getFAddr());
            assertEquals(1, restored.getAttributes());
            assertEquals(0, base.getLocalNetmail(new FTNAddr("2:500/102")).size());
        } finally {
            base.close();
        }
    }

    @Test
    public void killSentDeletesOnlyAfterAcknowledgementAndKeepsDuplicateHistory() throws Exception {
        OrmManager orm = new OrmManager("jdbc:h2:mem:killSent" + System.nanoTime());
        DatabaseManager base = new DatabaseManager(orm);
        assertTrue(base.open());
        try {
            Message message = message("\1MSGID: 2:500/101.1 kill-sent\rBody\r");
            message.setAttributes(128);
            base.saveNetmail(message, "kill", "2:500/103");
            assertEquals(1, base.getQueuedNetmail().size());
            assertEquals(1, orm.getDao(Netmail.class).countOf());
            base.markNetmailSent("kill");
            assertEquals(0, orm.getDao(Netmail.class).countOf());
            base.saveNetmail(message, "repeated", "2:500/103");
            assertEquals(0, orm.getDao(Netmail.class).countOf());
        } finally {
            base.close();
        }
    }
}
