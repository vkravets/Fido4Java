package org.fidonet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import org.junit.Test;

import java.util.Date;

public class MessageBoundaryTest {
    @Test
    public void nullAddressesAndMalformedFallbackMsgidAreSafeAndCharsetAliasesAreRecognized() {
        Message netmail = new Message("from",
                                      "to",
                                      null,
                                      null,
                                      "subject",
                                      "\1FMPT: 3\rbody\r",
                                      new Date());
        assertNull(netmail.getFAddr());
        assertNull(netmail.getTAddr());
        assertEquals("body\r", netmail.getBodyInMessageCharset());
        assertEquals("CP866", netmail.getMessageCharset());
        for (String address : new String[]{"unknown", "bad:address/abc"}) {
            Message echo = new Message("from",
                                       "to",
                                       new FTNAddr("invalid"),
                                       null,
                                       "subject",
                                       "AREA:TEST\r\1MSGID: "
                                       + address
                                       + " id\r\1CHARSET: UTF-8 4\rbody\r",
                                       new Date());
            assertFalse(echo.getFAddr().isValid());
            assertEquals("UTF-8", echo.getMessageCharset());
        }
        assertEquals("", netmail.getStripedBody(new String[0]).getBody());
    }

    private Message message(String text) {
        return new Message("from",
                           "to",
                           new FTNAddr("1:500/100"),
                           new FTNAddr("1:500/101"),
                           "subject",
                           text,
                           new Date());
    }

    @Test
    public void malformedNetmailControlLinesDoNotReplacePacketAddresses() {
        for (String intl : new String[]{"bad", "bad bad", "1:500/102 bad"}) {
            Message message = message("\1INTL: " + intl + "\rBody\r");
            assertEquals(new FTNAddr("1:500/100"), message.getFAddr());
            assertEquals(new FTNAddr("1:500/101"), message.getTAddr());
        }
        for (String point : new String[]{"0", "-1", "65536", "not-a-number"}) {
            Message message = message("\1FMPT: " + point + "\r\1TOPT: " + point + "\rBody\r");
            assertEquals(Integer.valueOf(0), message.getFAddr().getPnt());
            assertEquals(Integer.valueOf(0), message.getTAddr().getPnt());
        }
    }

    @Test
    public void echomailKeepsMalformedControlLinesAsTextAndFallsBackToMsgid() {
        Message message = new Message("from",
                                      "to",
                                      null,
                                      null,
                                      "subject",
                                      "AREA:TEST\r\1MSGID: 1:500/102 abc\r\1malformed\r * Origin: broken (bad)\r",
                                      new Date());
        assertEquals(new FTNAddr("1:500/102"), message.getFAddr());
        assertTrue(message.getBody().getBody().contains("\1malformed"));
        for (String origin : new String[]{" * Origin: missing closing (", " * Origin: no address",
                " * Origin: )wrong("}) {
            Message other = message("AREA:TEST\r" + origin + "\r");
            assertEquals(new FTNAddr("1:500/100"), other.getFAddr());
        }
    }
}
