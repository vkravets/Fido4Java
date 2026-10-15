package org.fidonet.db;

import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Test;

import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MessageRoundTripTest {
    @Test
    public void conversionUsesProtocolDateLocaleAndSupportsMissingRecipient() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            Message original = new Message("from",
                                           "to",
                                           new FTNAddr("2:500/1"),
                                           null,
                                           "subject",
                                           "body\r",
                                           new Date(1700000000000L));
            Echomail current = Echomail.fromMessage(original, null);
            assertNull(current.toMessage().getTAddr());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void restoresLegacyNullTextAndSeparatelyStoredAreaAndMsgid() {
        Echomail stored = new Echomail();
        stored.setFromAddr("2:500/1");
        stored.setDate(new Date());
        assertEquals("", stored.toMessage().getBody().getBody());
        Echoarea area = new Echoarea();
        area.setName("TEST");
        stored.setArea(area);
        stored.setMsgId("2:500/1 id");
        stored.setText("body\r");
        assertEquals("TEST", stored.toMessage().getArea());
        assertEquals(stored.getMsgId(), stored.toMessage().getMsgId());
    }

    @Test
    public void preservesAreaMsgidReplyCharsetAndUnknownControlData() throws Exception {
        Message original = new Message("from",
                                       "to",
                                       new FTNAddr(1, 500, 101, 0),
                                       new FTNAddr(1, 501, 102, 0),
                                       "Привет",
                                       "AREA:TEST\r"
                                       + "\1MSGID: 1:500/101 12345678\r"
                                       + "\1REPLY: 1:500/102 87654321\r"
                                       + "\1CHRS: UTF-8 4\r"
                                       + "\1UNKNOWN: keep\r"
                                       + "Привет\r"
                                       + " * Origin: Test (1:500/101)\r",
                                       new Date());
        Echoarea area = new Echoarea();
        area.setName("TEST");
        OrmManager database = new OrmManager("jdbc:h2:mem:messageRoundTrip" + System.nanoTime());
        try {
            database.connect();
            database.getDao(Echoarea.class).create(area);
            Echomail stored = Echomail.fromMessage(original, area);
            database.getDao(Echomail.class).create(stored);
            Message restored = database.getDao(Echomail.class)
                                       .queryForId(stored.getId())
                                       .toMessage();
            assertEquals(original.getMsgId(), restored.getMsgId());
            assertEquals(original.getBody().getKludges(), restored.getBody().getKludges());
            assertEquals(original.getBody().getBody(), restored.getBody().getBody());
            assertEquals("TEST", restored.getArea());
            assertEquals(original.getFAddr(), restored.getFAddr());
        } finally {
            database.disconnect();
        }
    }

    @Test
    public void restoresLegacySeparatelyStoredMsgid() {
        Echomail stored = new Echomail();
        stored.setFromAddr("1:500/101");
        stored.setDate(new Date());
        stored.setText("body\r");
        stored.setMsgId("1:500/101 12345678");
        assertEquals(stored.getMsgId(), stored.toMessage().getMsgId());
    }
}
