package org.fidonet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Test;

import java.util.Date;

public class MessageTailTest {
    private Message message(String text) {
        return new Message("Author",
                           "Reader",
                           new FTNAddr("2:500/1"),
                           new FTNAddr("2:500/2"),
                           "Subject",
                           text,
                           new Date(0));
    }

    @Test
    public void preservesQuotedRoutingTextAndUnknownControlPositions() {
        String text = "AREA:TEST\r\1MSGID: 2:500/1 unique\rIntro\r"
                      + "SEEN-BY: this is an example\r * Origin: quoted example\rMore text\r"
                      + "\1X-UNKNOWN: first\rBetween\r\1X-UNKNOWN: second\rEnd\r"
                      + " * Origin: Real (2:500/1)\rSEEN-BY: 500/1\r\1PATH: 500/1\r";
        Message message = message(text);
        assertTrue(message.getBody().getBody().contains("SEEN-BY: this is an example\r"));
        assertTrue(message.getBody().getBody().contains(" * Origin: quoted example\r"));
        assertEquals(text, message.getBody().toString());
    }

    @Test
    public void netmailKeepsEchoLikeTextAndAllViaLinesAtTheEnd() {
        String text = "\1INTL 2:500/2 2:500/1\rHello\rSEEN-BY: 500/9\r"
                      + " * Origin: quoted text\r\1Via 2:500/3 @20261015.010203.UTC Other 1.0\r"
                      + "\1Via 2:500/4 @20261015.020304.UTC Other 1.0\r";
        Message message = message(text);
        assertTrue(message.getBody().getBody().contains(" * Origin: quoted text\r"));
        assertEquals(2, message.getBody().getViaLines().size());
        assertEquals(text, message.getBody().toString());
        assertEquals(text, message.copy().getBody().toString());
    }
}
